package com.aipal.service;

import com.aipal.entity.AiModel;
import com.aipal.security.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashScopeRealtimeAsrClient {
    private static final int MAX_ERROR_LENGTH = 500;

    private final ModelService modelService;
    private final ObjectMapper objectMapper;

    @Value("${aipal.asr.dashscope.endpoint:wss://dashscope.aliyuncs.com/api-ws/v1/inference}")
    private String defaultEndpoint;
    @Value("${aipal.asr.dashscope.model:paraformer-realtime-v2}")
    private String defaultModelCode;
    @Value("${aipal.asr.realtime.sample-rate:16000}")
    private int defaultSampleRate;
    @Value("${aipal.asr.realtime.connect-timeout-seconds:10}")
    private long connectTimeoutSeconds;
    @Value("${aipal.asr.realtime.max-duration-seconds:300}")
    private long maxDurationSeconds;
    @Value("${aipal.asr.realtime.max-audio-bytes:15728640}")
    private long maxAudioBytes;
    @Value("${aipal.asr.realtime.max-frame-bytes:32768}")
    private int maxFrameBytes;

    public StreamSession open(TenantContext.Context context, Integer sampleRate, StreamListener listener) {
        AiModel model = defaultAsrModel(context);
        String endpoint = valueOrDefault(model.getEndpoint(), defaultEndpoint);
        String modelCode = valueOrDefault(model.getModelCode(), defaultModelCode);
        String apiKey = model.getApiKey();
        if (apiKey == null || apiKey.isBlank() || "******".equals(apiKey)) {
            throw new IllegalStateException("ASR default model API key is not configured");
        }
        int effectiveSampleRate = sampleRate == null || sampleRate <= 0 ? defaultSampleRate : sampleRate;
        StreamSession session = new StreamSession(endpoint, apiKey, modelCode, effectiveSampleRate, listener);
        session.connect();
        return session;
    }

    private AiModel defaultAsrModel(TenantContext.Context context) {
        AtomicReference<AiModel> selected = new AtomicReference<>();
        TenantContext.runWithContext(context, () -> selected.set(modelService.getDefaultEnabledModel(ModelService.CAPABILITY_ASR)));
        AiModel model = selected.get();
        if (model == null) {
            throw new IllegalStateException("No enabled default ASR model is configured for the current tenant");
        }
        return model;
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public interface StreamListener {
        void onReady();
        void onTranscript(String text, boolean sentenceEnd);
        void onCompleted();
        void onError(String message);
    }

    public final class StreamSession implements AutoCloseable {
        private final String endpoint;
        private final String apiKey;
        private final String modelCode;
        private final int sampleRate;
        private final StreamListener listener;
        private final String taskId = UUID.randomUUID().toString();
        private final Queue<byte[]> pendingAudio = new ConcurrentLinkedQueue<>();
        private final AtomicBoolean taskStarted = new AtomicBoolean(false);
        private final AtomicBoolean stopping = new AtomicBoolean(false);
        private final AtomicBoolean completed = new AtomicBoolean(false);
        private final AtomicLong receivedAudioBytes = new AtomicLong(0);
        private final Instant startedAt = Instant.now();
        private volatile WebSocket webSocket;

        private StreamSession(String endpoint, String apiKey, String modelCode, int sampleRate,
                              StreamListener listener) {
            this.endpoint = endpoint;
            this.apiKey = apiKey;
            this.modelCode = modelCode;
            this.sampleRate = sampleRate;
            this.listener = listener;
        }

        private void connect() {
            try {
                webSocket = HttpClient.newHttpClient()
                        .newWebSocketBuilder()
                        .header("Authorization", "Bearer " + apiKey)
                        .buildAsync(URI.create(endpoint), new ProviderListener())
                        .get(Math.max(3, connectTimeoutSeconds), TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to connect DashScope realtime ASR: " + e.getMessage(), e);
            }
        }

        public void sendAudio(byte[] frame) {
            if (frame == null || frame.length == 0 || stopping.get()) {
                return;
            }
            if (frame.length > maxFrameBytes) {
                throw new IllegalArgumentException("Audio frame is too large");
            }
            if (Duration.between(startedAt, Instant.now()).toSeconds() > maxDurationSeconds) {
                throw new IllegalStateException("Realtime ASR session exceeded max duration");
            }
            if (receivedAudioBytes.addAndGet(frame.length) > maxAudioBytes) {
                throw new IllegalStateException("Realtime ASR session exceeded max audio bytes");
            }
            if (!taskStarted.get()) {
                pendingAudio.offer(frame);
                return;
            }
            sendBinary(frame);
        }

        public void finish() {
            if (!stopping.compareAndSet(false, true)) {
                return;
            }
            WebSocket ws = webSocket;
            if (ws != null) {
                ws.sendText(json(Map.of(
                        "header", Map.of(
                                "action", "finish-task",
                                "task_id", taskId,
                                "streaming", "duplex"
                        ),
                        "payload", Map.of("input", Map.of())
                )), true);
            }
        }

        @Override
        public void close() {
            stopping.set(true);
            WebSocket ws = webSocket;
            if (ws != null) {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "closed");
            }
            pendingAudio.clear();
        }

        private void sendRunTask(WebSocket ws) {
            ws.sendText(json(Map.of(
                    "header", Map.of(
                            "action", "run-task",
                            "task_id", taskId,
                            "streaming", "duplex"
                    ),
                    "payload", Map.of(
                            "task_group", "audio",
                            "task", "asr",
                            "function", "recognition",
                            "model", modelCode,
                            "parameters", Map.of(
                                    "format", "pcm",
                                    "sample_rate", sampleRate,
                                    "language_hints", List.of("zh", "en"),
                                    "punctuation_prediction_enabled", true,
                                    "inverse_text_normalization_enabled", true,
                                    "semantic_punctuation_enabled", false,
                                    "max_sentence_silence", 800
                            ),
                            "input", Map.of()
                    )
            )), true);
        }

        private void flushPendingAudio() {
            byte[] frame;
            while ((frame = pendingAudio.poll()) != null && taskStarted.get() && !stopping.get()) {
                sendBinary(frame);
            }
        }

        private void sendBinary(byte[] frame) {
            WebSocket ws = webSocket;
            if (ws != null) {
                ws.sendBinary(ByteBuffer.wrap(frame), true);
            }
        }

        private String json(Object value) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to build DashScope ASR message", e);
            }
        }

        private final class ProviderListener implements WebSocket.Listener {
            private final StringBuilder textBuffer = new StringBuilder();

            @Override
            public void onOpen(WebSocket ws) {
                ws.request(1);
                sendRunTask(ws);
            }

            @Override
            public CompletableFuture<?> onText(WebSocket ws, CharSequence data, boolean last) {
                textBuffer.append(data);
                if (last) {
                    String message = textBuffer.toString();
                    textBuffer.setLength(0);
                    handleProviderMessage(message);
                }
                ws.request(1);
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<?> onClose(WebSocket ws, int statusCode, String reason) {
                if (!stopping.get()) {
                    listener.onError("DashScope ASR connection closed: " + statusCode);
                }
                completeOnce();
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void onError(WebSocket ws, Throwable error) {
                log.warn("DashScope realtime ASR error", error);
                listener.onError(limitError(error.getMessage()));
            }
        }

        private void handleProviderMessage(String message) {
            try {
                JsonNode root = objectMapper.readTree(message);
                String event = root.path("header").path("event").asText("");
                if ("task-started".equals(event)) {
                    taskStarted.set(true);
                    listener.onReady();
                    flushPendingAudio();
                    return;
                }
                if ("result-generated".equals(event)) {
                    JsonNode sentence = root.path("payload").path("output").path("sentence");
                    String text = sentence.path("text").asText("");
                    if (!text.isBlank()) {
                        listener.onTranscript(text, sentence.path("sentence_end").asBoolean(false));
                    }
                    return;
                }
                if ("task-finished".equals(event)) {
                    stopping.set(true);
                    completeOnce();
                    close();
                    return;
                }
                if ("task-failed".equals(event)) {
                    listener.onError(limitError(root.path("header").path("error_message").asText("ASR task failed")));
                    close();
                }
            } catch (Exception e) {
                listener.onError("Invalid DashScope ASR response");
            }
        }

        private String limitError(String message) {
            String value = message == null || message.isBlank() ? "DashScope ASR request failed" : message;
            return value.substring(0, Math.min(MAX_ERROR_LENGTH, value.length()));
        }

        private void completeOnce() {
            if (completed.compareAndSet(false, true)) {
                listener.onCompleted();
            }
        }
    }
}
