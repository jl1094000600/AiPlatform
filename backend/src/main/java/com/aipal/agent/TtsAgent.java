package com.aipal.agent;

import cn.hutool.core.util.IdUtil;
import com.aipal.dto.A2AMessage;
import com.aipal.dto.TtsRequest;
import com.aipal.dto.TtsResponse;
import com.aipal.service.A2AMessageService;
import com.aipal.service.AgentRegistry;
import com.aipal.service.HeartbeatService;
import com.aipal.service.TtsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;

@Slf4j
@Component
public class TtsAgent implements ApplicationRunner {

    private final TtsService ttsService;
    private final AgentRegistry agentRegistry;
    private final A2AMessageService a2aMessageService;
    private final HeartbeatService heartbeatService;

    @Value("${tts.agent.code:tts-agent}")
    private String agentCode;

    @Value("${tts.agent.name:TTS语音合成Agent}")
    private String agentName;

    @Value("${tts.agent.enabled:true}")
    private boolean enabled;

    public TtsAgent(TtsService ttsService,
                    AgentRegistry agentRegistry,
                    A2AMessageService a2aMessageService,
                    HeartbeatService heartbeatService) {
        this.ttsService = ttsService;
        this.agentRegistry = agentRegistry;
        this.a2aMessageService = a2aMessageService;
        this.heartbeatService = heartbeatService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("TTS Agent is disabled");
            return;
        }

        Function<A2AMessage, A2AMessage> handler = this::handleMessage;
        a2aMessageService.registerHandler(agentCode, handler);

        log.info("TTS Agent registered with code: {}", agentCode);
    }

    private A2AMessage handleMessage(A2AMessage message) {
        A2AMessage response = new A2AMessage();
        response.setSourceAgent(agentCode);
        response.setTargetAgent(message.getSourceAgent());
        response.setSessionId(message.getSessionId());
        response.setCorrelationId(message.getMessageId());
        response.setAction(A2AMessage.Action.respond);

        try {
            Map<String, Object> payload = message.getPayload();
            if (payload == null) {
                response.setPayload(Map.of("status", "error", "error", "Empty payload"));
                return response;
            }

            String intent = payload.get("intent") != null ? payload.get("intent").toString() : "synthesize";

            switch (intent) {
                case "synthesize" -> handleSynthesize(message, response, payload);
                case "get_voices" -> handleGetVoices(message, response);
                case "health_check" -> handleHealthCheck(message, response);
                default -> response.setPayload(Map.of("status", "error", "error", "Unknown intent: " + intent));
            }
        } catch (Exception e) {
            log.error("Error handling TTS message", e);
            response.setPayload(Map.of("status", "error", "error", e.getMessage()));
        }

        return response;
    }

    private void handleSynthesize(A2AMessage message, A2AMessage response, Map<String, Object> payload) {
        String text = payload.get("text") != null ? payload.get("text").toString() : "";
        String voiceId = payload.get("voiceId") != null ? payload.get("voiceId").toString() : null;
        Float speed = payload.get("speed") != null ? Float.parseFloat(payload.get("speed").toString()) : null;
        Integer volume = payload.get("volume") != null ? Integer.parseInt(payload.get("volume").toString()) : null;

        TtsRequest request = TtsRequest.builder()
                .text(text)
                .voiceId(voiceId)
                .speed(speed)
                .volume(volume)
                .sessionId(message.getSessionId())
                .build();

        TtsResponse ttsResponse = ttsService.synthesize(request);

        if ("success".equals(ttsResponse.getStatus())) {
            response.setPayload(Map.of(
                    "status", "success",
                    "taskId", ttsResponse.getTaskId(),
                    "audioUrl", ttsResponse.getAudioUrl(),
                    "format", ttsResponse.getFormat(),
                    "voiceId", ttsResponse.getVoiceId()
            ));
        } else {
            response.setPayload(Map.of(
                    "status", "error",
                    "error", ttsResponse.getErrorMessage()
            ));
        }
    }

    private void handleGetVoices(A2AMessage message, A2AMessage response) {
        var voices = ttsService.getAvailableVoices();
        response.setPayload(Map.of(
                "status", "success",
                "voices", voices
        ));
    }

    private void handleHealthCheck(A2AMessage message, A2AMessage response) {
        response.setPayload(Map.of(
                "status", "success",
                "agent", agentCode,
                "agentName", agentName,
                "online", true
        ));
    }
}
