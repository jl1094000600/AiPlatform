package com.aipal.service;

import com.aipal.entity.AiModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MultimodalModelClient {
    private static final int MAX_ERROR_BODY_LENGTH = 500;

    private final ModelService modelService;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

    public String analyzeImage(byte[] content, String mimeType, String fileName) {
        requireContent(content, "image");
        String effectiveMimeType = requireMimeType(mimeType, "image");
        AiModel model = requireDefaultModel(ModelService.CAPABILITY_VISION);
        validateModelConfiguration(model, ModelService.CAPABILITY_VISION);

        String dataUrl = "data:" + effectiveMimeType + ";base64," + Base64.getEncoder().encodeToString(content);
        String prompt = fileName == null || fileName.isBlank()
                ? "Analyze this image and describe the important details."
                : "Analyze the image named " + fileName + " and describe the important details.";
        Map<String, Object> body = Map.of(
                "model", model.getModelCode(),
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "text", "text", prompt),
                                Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))
                        )
                ))
        );

        String response = executeJson(model, "/chat/completions", body, ModelService.CAPABILITY_VISION);
        return parseVisionText(response);
    }

    public String transcribeAudio(byte[] content, String mimeType, String fileName) {
        requireContent(content, "audio");
        String effectiveMimeType = requireMimeType(mimeType, "audio");
        AiModel model = requireDefaultModel(ModelService.CAPABILITY_ASR);
        validateModelConfiguration(model, ModelService.CAPABILITY_ASR);

        String effectiveFileName = fileName == null || fileName.isBlank() ? "audio" : fileName;
        MultipartBodyBuilder multipart = new MultipartBodyBuilder();
        multipart.part("file", new NamedByteArrayResource(content, effectiveFileName))
                .contentType(MediaType.parseMediaType(effectiveMimeType));
        multipart.part("model", model.getModelCode());
        multipart.part("response_format", "json");

        String response;
        try {
            response = restClientBuilder.clone().build()
                    .post()
                    .uri(resolveEndpoint(model.getEndpoint(), "/audio/transcriptions"))
                    .headers(headers -> headers.setBearerAuth(model.getApiKey()))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipart.build())
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw httpFailure(ModelService.CAPABILITY_ASR, e);
        } catch (RestClientException e) {
            throw new IllegalStateException("ASR model request failed: " + e.getMessage(), e);
        }
        return parseAsrText(response);
    }

    private String executeJson(AiModel model, String path, Object body, String capability) {
        try {
            return restClientBuilder.clone().build()
                    .post()
                    .uri(resolveEndpoint(model.getEndpoint(), path))
                    .headers(headers -> headers.setBearerAuth(model.getApiKey()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw httpFailure(capability, e);
        } catch (RestClientException e) {
            throw new IllegalStateException(capability + " model request failed: " + e.getMessage(), e);
        }
    }

    private AiModel requireDefaultModel(String capability) {
        AiModel model = modelService.getDefaultEnabledModel(capability);
        if (model == null) {
            throw new IllegalStateException(
                    "No enabled default " + capability + " model is configured for the current tenant");
        }
        return model;
    }

    private void validateModelConfiguration(AiModel model, String capability) {
        if (model.getEndpoint() == null || model.getEndpoint().isBlank()) {
            throw new IllegalStateException(capability + " default model endpoint is not configured");
        }
        if (model.getApiKey() == null || model.getApiKey().isBlank() || "******".equals(model.getApiKey())) {
            throw new IllegalStateException(capability + " default model API key is not configured");
        }
        if (model.getModelCode() == null || model.getModelCode().isBlank()) {
            throw new IllegalStateException(capability + " default model code is not configured");
        }
    }

    private String parseVisionText(String response) {
        JsonNode root = parseResponse(response, ModelService.CAPABILITY_VISION);
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        String text = extractText(content);
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("VISION model response did not contain choices[0].message.content");
        }
        return text;
    }

    private String parseAsrText(String response) {
        JsonNode root = parseResponse(response, ModelService.CAPABILITY_ASR);
        String text = root.path("text").asText(null);
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("ASR model response did not contain text");
        }
        return text;
    }

    private JsonNode parseResponse(String response, String capability) {
        if (response == null || response.isBlank()) {
            throw new IllegalStateException(capability + " model returned an empty response");
        }
        try {
            return objectMapper.readTree(response);
        } catch (IOException e) {
            throw new IllegalStateException(capability + " model returned invalid JSON: " + e.getMessage(), e);
        }
    }

    private String extractText(JsonNode content) {
        if (content.isTextual()) {
            return content.asText();
        }
        if (content.isArray()) {
            StringBuilder text = new StringBuilder();
            for (JsonNode item : content) {
                String value = item.path("text").asText("");
                if (!value.isBlank()) {
                    if (!text.isEmpty()) {
                        text.append('\n');
                    }
                    text.append(value);
                }
            }
            return text.toString();
        }
        return null;
    }

    private IllegalStateException httpFailure(String capability, RestClientResponseException error) {
        String responseBody = error.getResponseBodyAsString();
        if (responseBody.length() > MAX_ERROR_BODY_LENGTH) {
            responseBody = responseBody.substring(0, MAX_ERROR_BODY_LENGTH) + "...";
        }
        String detail = responseBody.isBlank() ? error.getStatusText() : responseBody;
        return new IllegalStateException(capability + " model request failed with HTTP "
                + error.getStatusCode().value() + ": " + detail, error);
    }

    private String resolveEndpoint(String baseUrl, String path) {
        String normalizedBase = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        return normalizedBase.endsWith(path) ? normalizedBase : normalizedBase + path;
    }

    private void requireContent(byte[] content, String type) {
        if (content == null || content.length == 0) {
            throw new IllegalStateException(type + " content must not be empty");
        }
    }

    private String requireMimeType(String mimeType, String type) {
        if (mimeType == null || mimeType.isBlank()) {
            throw new IllegalStateException(type + " MIME type must not be empty");
        }
        try {
            return MediaType.parseMediaType(mimeType).toString();
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid " + type + " MIME type: " + mimeType, e);
        }
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String fileName;

        private NamedByteArrayResource(byte[] content, String fileName) {
            super(content);
            this.fileName = fileName;
        }

        @Override
        public String getFilename() {
            return fileName;
        }
    }
}
