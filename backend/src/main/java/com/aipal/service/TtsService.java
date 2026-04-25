package com.aipal.service;

import cn.hutool.core.util.IdUtil;
import com.aipal.dto.TtsRequest;
import com.aipal.dto.TtsResponse;
import com.aipal.dto.VoiceInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class TtsService {

    private final ObjectMapper objectMapper;

    @Value("${tts.provider:edge}")
    private String ttsProvider;

    @Value("${tts.edge.voice:zh-CN-XiaoxiaoNeural}")
    private String defaultEdgeVoice;

    @Value("${tts.output-dir:/tmp/tts}")
    private String outputDir;

    private ExecutorService wsExecutor;

    private static final Map<String, String> VOICE_ID_MAPPING = Map.of(
            "zh-CN-female-1", "zh-CN-XiaoxiaoNeural",
            "zh-CN-male-1", "zh-CN-YunxiNeural",
            "en-US-female-1", "en-US-JennyNeural",
            "en-US-male-1", "en-US-GuyNeural"
    );

    private static final List<VoiceInfo> AVAILABLE_VOICES = List.of(
            VoiceInfo.builder().voiceId("zh-CN-female-1").voiceName("晓晓").locale("zh-CN").gender("Female").description("通用场景").build(),
            VoiceInfo.builder().voiceId("zh-CN-male-1").voiceName("云飞").locale("zh-CN").gender("Male").description("新闻播报").build(),
            VoiceInfo.builder().voiceId("en-US-female-1").voiceName("晓秋").locale("en-US").gender("Female").description("英文教学").build(),
            VoiceInfo.builder().voiceId("en-US-male-1").voiceName("云杰").locale("en-US").gender("Male").description("商务英文").build()
    );

    @PostConstruct
    public void init() {
        wsExecutor = Executors.newCachedThreadPool();
        ensureOutputDir();
        log.info("TtsService initialized with provider: {}, output-dir: {}", ttsProvider, outputDir);
    }

    private void ensureOutputDir() {
        try {
            Path path = Path.of(outputDir);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
        } catch (IOException e) {
            log.error("Failed to create TTS output directory", e);
        }
    }

    public TtsResponse synthesize(TtsRequest request) {
        String taskId = IdUtil.fastSimpleUUID();
        String voiceId = request.getVoiceId() != null ? request.getVoiceId() : "zh-CN-female-1";
        String edgeVoice = VOICE_ID_MAPPING.getOrDefault(voiceId, defaultEdgeVoice);
        String text = request.getText();

        if (text == null || text.isBlank()) {
            return TtsResponse.builder()
                    .taskId(taskId)
                    .status("error")
                    .errorMessage("Text cannot be empty")
                    .build();
        }

        if (text.length() > 5000) {
            text = text.substring(0, 5000);
        }

        try {
            String audioFile = outputDir + "/" + taskId + ".mp3";
            synthesizeEdgeTts(text, edgeVoice, audioFile, request.getSpeed(), request.getVolume());

            return TtsResponse.builder()
                    .taskId(taskId)
                    .audioUrl("/api/tts/audio/" + taskId)
                    .format("mp3")
                    .voiceId(voiceId)
                    .status("success")
                    .build();
        } catch (Exception e) {
            log.error("TTS synthesis failed for task {}", taskId, e);
            return TtsResponse.builder()
                    .taskId(taskId)
                    .status("error")
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    public Flux<String> synthesizeStream(TtsRequest request) {
        String voiceId = request.getVoiceId() != null ? request.getVoiceId() : "zh-CN-female-1";
        String edgeVoice = VOICE_ID_MAPPING.getOrDefault(voiceId, defaultEdgeVoice);
        String text = request.getText();

        if (text == null || text.isBlank()) {
            return Flux.just("event: error\ndata: {\"error\":\"Text cannot be empty\"}\n\n");
        }

        if (text.length() > 5000) {
            text = text.substring(0, 5000);
        }

        String finalText = text;
        return Flux.create(sink -> {
            try {
                streamEdgeTts(finalText, edgeVoice, request.getSpeed(), request.getVolume(), chunk -> {
                    sink.next("event: audio\ndata: " + chunk + "\n\n");
                }, () -> {
                    sink.next("event: done\ndata: {}\n\n");
                    sink.complete();
                }, error -> {
                    sink.next("event: error\ndata: {\"error\":\"" + error.getMessage() + "\"}\n\n");
                    sink.error(error);
                });
            } catch (Exception e) {
                sink.next("event: error\ndata: {\"error\":\"" + e.getMessage() + "\"}\n\n");
                sink.error(e);
            }
        });
    }

    private void synthesizeEdgeTts(String text, String voice, String outputFile, Float speed, Integer volume) throws Exception {
        StringBuilder ssml = buildSsml(text, voice, speed, volume);

        CompletableFuture<String> audioDataFuture = new CompletableFuture<>();
        CountDownLatch latch = new CountDownLatch(1);

        URI edgeTtsUri = URI.create("wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?TrustedClientToken=6A5AA1D4EAFF4E9455472A1292565D8F");

        WebSocket webSocket = (WebSocket) HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .subprotocols("base64")
                .buildAsync(edgeTtsUri, new WebSocket.Listener() {
                    private final StringBuilder audioBuilder = new StringBuilder();
                    private boolean headersReceived = false;

                    @Override
                    public void onOpen(WebSocket ws) {
                        String request = "X-Timestamp: " + java.time.Instant.now().toString() + "\r\n" +
                                "Content-Type: application/ssml+xml\r\n" +
                                "X-RequestId: " + UUID.randomUUID().toString().replace("-", "") + "\r\n" +
                                "Max-Redirects: 3\r\n" +
                                "TrustedClientIds: 4bf61043-fac9-4480-853b-eb0801fd257c\r\n" +
                                "X-TFE-BinarySent: true\r\n\r\n";

                        ws.sendText(request, false);

                        String fullSsml = "Path: ssml\r\n" +
                                "X-Timestamp: " + java.time.Instant.now().toString() + "\r\n" +
                                "Content-Type: application/ssml+xml\r\n" +
                                "X-RequestId: " + UUID.randomUUID().toString().replace("-", "") + "\r\n\r\n" +
                                ssml;

                        ws.sendText(fullSsml, false);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean endOfMessage) {
                        String str = data.toString();
                        if (str.startsWith("Path:turn.end")) {
                            audioDataFuture.complete(audioBuilder.toString());
                            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done");
                            latch.countDown();
                            return CompletableFuture.completedFuture(null);
                        }
                        if (str.startsWith("Path:response")) {
                            headersReceived = true;
                        }
                        if (headersReceived && !str.startsWith("Path:")) {
                            audioBuilder.append(str.trim());
                        }
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public void onError(WebSocket ws, Throwable error) {
                        audioDataFuture.completeExceptionally(error);
                        latch.countDown();
                    }
                });

        webSocket.request(1);

        try {
            latch.await(30, TimeUnit.SECONDS);
            String audioBase64 = audioDataFuture.get(5, TimeUnit.SECONDS);

            byte[] audioBytes = Base64.getDecoder().decode(audioBase64);
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                fos.write(audioBytes);
            }
            log.debug("TTS audio saved to {}", outputFile);
        } catch (TimeoutException e) {
            throw new RuntimeException("TTS synthesis timed out");
        } catch (Exception e) {
            log.error("Edge TTS WebSocket error", e);
            throw new RuntimeException("Edge TTS failed: " + e.getMessage());
        }
    }

    private void streamEdgeTts(String text, String voice, Float speed, Integer volume,
                               Consumer<String> onChunk, Runnable onDone, Consumer<Throwable> onError) {
        StringBuilder ssml = buildSsml(text, voice, speed, volume);

        CompletableFuture<Void> connectedFuture = new CompletableFuture<>();
        CountDownLatch doneLatch = new CountDownLatch(1);

        URI edgeTtsUri = URI.create("wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?TrustedClientToken=6A5AA1D4EAFF4E9455472A1292565D8F");

        WebSocket webSocket = (WebSocket) HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .subprotocols("base64")
                .buildAsync(edgeTtsUri, new WebSocket.Listener() {
                    private final StringBuilder audioBuilder = new StringBuilder();
                    private boolean headersReceived = false;

                    @Override
                    public void onOpen(WebSocket ws) {
                        String request = "X-Timestamp: " + java.time.Instant.now().toString() + "\r\n" +
                                "Content-Type: application/ssml+xml\r\n" +
                                "X-RequestId: " + UUID.randomUUID().toString().replace("-", "") + "\r\n" +
                                "Max-Redirects: 3\r\n" +
                                "TrustedClientIds: 4bf61043-fac9-4480-853b-eb0801fd257c\r\n" +
                                "X-TFE-BinarySent: true\r\n\r\n";

                        ws.sendText(request, false);

                        String fullSsml = "Path: ssml\r\n" +
                                "X-Timestamp: " + java.time.Instant.now().toString() + "\r\n" +
                                "Content-Type: application/ssml+xml\r\n" +
                                "X-RequestId: " + UUID.randomUUID().toString().replace("-", "") + "\r\n\r\n" +
                                ssml;

                        ws.sendText(fullSsml, false);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean endOfMessage) {
                        String str = data.toString();
                        if (str.startsWith("Path:turn.end")) {
                            if (!audioBuilder.isEmpty()) {
                                onChunk.accept(audioBuilder.toString());
                            }
                            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done");
                            doneLatch.countDown();
                            return CompletableFuture.completedFuture(null);
                        }
                        if (str.startsWith("Path:response")) {
                            headersReceived = true;
                        }
                        if (headersReceived && !str.startsWith("Path:")) {
                            String chunk = str.trim();
                            if (!chunk.isEmpty()) {
                                audioBuilder.append(chunk);
                                if (audioBuilder.length() > 1000) {
                                    onChunk.accept(audioBuilder.toString());
                                    audioBuilder.setLength(0);
                                }
                            }
                        }
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public void onError(WebSocket ws, Throwable error) {
                        doneLatch.countDown();
                        onError.accept(error);
                    }
                });

        webSocket.request(1);

    }

    private StringBuilder buildSsml(String text, String voice, Float speed, Integer volume) {
        StringBuilder ssml = new StringBuilder();
        ssml.append("<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='zh-CN'>");
        ssml.append("<voice name='").append(voice).append("'>");

        String rateStr = "+0%";
        if (speed != null) {
            float effectiveSpeed = Math.max(0.5f, Math.min(2.0f, speed));
            int percent = (int) ((effectiveSpeed - 1) * 100);
            rateStr = (percent >= 0 ? "+" : "") + percent + "%";
        }

        String volumeStr = "+0%";
        if (volume != null) {
            int vol = Math.max(0, Math.min(100, volume));
            int percent = vol - 100;
            volumeStr = (percent >= 0 ? "+" : "") + percent + "%";
        }

        ssml.append("<prosody rate='").append(rateStr).append("' volume='").append(volumeStr).append("'>");
        ssml.append(escapeXml(text));
        ssml.append("</prosody></voice></speak>");
        return ssml;
    }

    private String escapeXml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    public List<VoiceInfo> getAvailableVoices() {
        return AVAILABLE_VOICES;
    }

    public List<VoiceInfo> getVoicesByLocale(String locale) {
        return AVAILABLE_VOICES.stream()
                .filter(v -> v.getLocale().equalsIgnoreCase(locale))
                .toList();
    }

    public Optional<Path> getAudioFile(String taskId) {
        Path file = Path.of(outputDir + "/" + taskId + ".mp3");
        if (Files.exists(file)) {
            return Optional.of(file);
        }
        return Optional.empty();
    }

    public boolean deleteAudioFile(String taskId) {
        try {
            Path file = Path.of(outputDir + "/" + taskId + ".mp3");
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            log.error("Failed to delete audio file for task {}", taskId, e);
            return false;
        }
    }
}
