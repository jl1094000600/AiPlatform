package com.aipal.service;

import com.aipal.entity.AiModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Single gateway for OpenAI-compatible chat completions.
 * Centralizes endpoint resolution, auth, usage parsing and token estimation.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ModelGateway {

    private static final int MINIMAX_OUTPUT_MAX_TOKENS = 8192;
    private static final int DEFAULT_MAX_TOKENS = 4096;
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 120_000;
    /** Placeholder used by UI/masked credentials; must not be sent as a real key. */
    private static final String MASKED_API_KEY = "******";

    private final ObjectMapper objectMapper;

    public ModelCallResult chat(AiModel model, String systemPrompt, String userPrompt) {
        return chat(model, systemPrompt, userPrompt, null, null);
    }

    public ModelCallResult chat(AiModel model, String systemPrompt, String userPrompt,
                                BigDecimal temperature, Integer maxTokens) {
        return chat(model, systemPrompt, userPrompt, temperature, maxTokens, null, null, true);
    }

    /**
     * @param requireApiKey when false, Authorization header is omitted if the key is blank or masked
     * @param connectTimeout read/write timeout overrides; null uses defaults
     */
    public ModelCallResult chat(AiModel model, String systemPrompt, String userPrompt,
                                BigDecimal temperature, Integer maxTokens,
                                Duration connectTimeout, Duration readTimeout,
                                boolean requireApiKey) {
        requireChatReady(model, requireApiKey);
        BigDecimal effectiveTemperature = temperature != null
                ? temperature
                : (model.getDefaultTemperature() == null ? BigDecimal.valueOf(0.2) : model.getDefaultTemperature());
        int effectiveMaxTokens = maxTokens != null ? maxTokens : resolveMaxTokens(model);

        Map<String, Object> body = Map.of(
                "model", model.getModelCode(),
                "temperature", effectiveTemperature,
                "max_tokens", effectiveMaxTokens,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt == null ? "" : systemPrompt),
                        Map.of("role", "user", "content", userPrompt == null ? "" : userPrompt)
                )
        );
        String response = executeJson(model, "/chat/completions", body, requireApiKey, connectTimeout, readTimeout);
        try {
            return extractResult(response, systemPrompt, userPrompt);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse model response: " + e.getMessage(), e);
        }
    }

    public static int resolveMaxTokens(AiModel model) {
        int configured = model.getMaxTokens() == null ? DEFAULT_MAX_TOKENS : model.getMaxTokens();
        int providerLimit = "MiniMax".equalsIgnoreCase(model.getProvider())
                ? MINIMAX_OUTPUT_MAX_TOKENS
                : 200_000;
        return Math.max(1, Math.min(configured, providerLimit));
    }

    public static int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }

    public static String resolveChatEndpoint(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Model endpoint is required");
        }
        return baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";
    }

    private void requireChatReady(AiModel model, boolean requireApiKey) {
        if (model == null) {
            throw new IllegalArgumentException("Model is required");
        }
        if (model.getEndpoint() == null || model.getEndpoint().isBlank()) {
            throw new IllegalArgumentException("Model endpoint is not configured");
        }
        if (requireApiKey && !hasUsableApiKey(model)) {
            throw new IllegalArgumentException("Model API key is not configured");
        }
    }

    private boolean hasUsableApiKey(AiModel model) {
        String key = model.getApiKey();
        return key != null && !key.isBlank() && !MASKED_API_KEY.equals(key);
    }

    private String executeJson(AiModel model, String path, Object body, boolean requireApiKey,
                               Duration connectTimeout, Duration readTimeout) {
        RestClient client = buildClient(connectTimeout, readTimeout);
        RestClient.RequestBodySpec request = client
                .post()
                .uri(resolveEndpoint(model.getEndpoint(), path))
                .contentType(MediaType.APPLICATION_JSON);
        if (hasUsableApiKey(model) || requireApiKey) {
            request.headers(headers -> headers.setBearerAuth(model.getApiKey() == null ? "" : model.getApiKey()));
        }
        return request.body(body).retrieve().body(String.class);
    }

    private RestClient buildClient(Duration connectTimeout, Duration readTimeout) {
        int connectMs = connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT_MS : (int) connectTimeout.toMillis();
        int readMs = readTimeout == null ? DEFAULT_READ_TIMEOUT_MS : (int) readTimeout.toMillis();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectMs);
        requestFactory.setReadTimeout(readMs);
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    private String resolveEndpoint(String baseUrl, String path) {
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return trimmed + path;
    }

    private ModelCallResult extractResult(String response, String systemPrompt, String userPrompt) throws IOException {
        if (response == null || response.isBlank()) {
            return ModelCallResult.empty();
        }
        JsonNode root = objectMapper.readTree(response);
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        String text = content.isMissingNode() ? "" : content.asText();
        JsonNode usage = root.path("usage");
        int inputTokens = firstInt(usage, "prompt_tokens", "input_tokens");
        int outputTokens = firstInt(usage, "completion_tokens", "output_tokens");
        int totalTokens = firstInt(usage, "total_tokens");
        if (totalTokens <= 0) {
            inputTokens = estimateTokens(systemPrompt) + estimateTokens(userPrompt);
            outputTokens = estimateTokens(text);
            totalTokens = inputTokens + outputTokens;
        } else if (inputTokens + outputTokens <= 0) {
            inputTokens = estimateTokens(systemPrompt) + estimateTokens(userPrompt);
            outputTokens = Math.max(0, totalTokens - inputTokens);
        } else if (totalTokens <= 0) {
            totalTokens = inputTokens + outputTokens;
        }
        return new ModelCallResult(text, inputTokens, outputTokens, totalTokens);
    }

    private int firstInt(JsonNode node, String... names) {
        if (node == null || node.isMissingNode()) {
            return 0;
        }
        for (String name : names) {
            JsonNode value = node.path(name);
            if (value.isNumber()) {
                return Math.max(0, value.asInt());
            }
        }
        return 0;
    }
}
