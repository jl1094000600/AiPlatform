package com.aipal.agent.image.service;

import cn.hutool.core.util.IdUtil;
import com.aipal.agent.image.config.AgentConfig;
import com.aipal.agent.image.dto.ImageRecognitionRequest;
import com.aipal.agent.image.dto.ImageRecognitionResponse;
import com.aipal.agent.image.service.A2ACommunicationService.A2AMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final ImageRecognitionService imageRecognitionService;
    private final A2ACommunicationService a2aCommunicationService;
    private final AgentConfig agentConfig;

    public void registerAgent() {
        a2aCommunicationService.registerHandler(this::handleA2AMessage);
        log.info("Image Recognition Agent registered successfully");
    }

    private A2AMessage handleA2AMessage(A2AMessage message) {
        log.info("Received A2A message: {} from {}", message.getAction(), message.getSourceAgent());

        A2AMessage response = new A2AMessage();
        response.setSourceAgent(agentConfig.getAgentCode());
        response.setTargetAgent(message.getSourceAgent());
        response.setSessionId(message.getSessionId());
        response.setAction(A2AMessage.Action.respond);

        try {
            if (message.getPayload() != null) {
                String intent = (String) message.getPayload().get("intent");
                if ("recognize".equals(intent)) {
                    ImageRecognitionRequest request = new ImageRecognitionRequest();
                    request.setInputType((String) message.getPayload().get("inputType"));
                    request.setInputData((String) message.getPayload().get("inputData"));
                    request.setSessionId(message.getSessionId());

                    ImageRecognitionResponse result = imageRecognitionService.processRequest(request);

                    Map<String, Object> resultPayload = new HashMap<>();
                    resultPayload.put("status", "success");
                    resultPayload.put("taskId", result.getTaskId());
                    resultPayload.put("result", result.getResult());
                    resultPayload.put("fileType", result.getFileType());
                    response.setPayload(resultPayload);

                } else if ("parse".equals(intent)) {
                    ImageRecognitionRequest request = new ImageRecognitionRequest();
                    request.setInputType((String) message.getPayload().get("inputType"));
                    request.setInputData((String) message.getPayload().get("inputData"));
                    request.setSessionId(message.getSessionId());

                    ImageRecognitionResponse result = imageRecognitionService.processRequest(request);

                    Map<String, Object> resultPayload = new HashMap<>();
                    resultPayload.put("status", "success");
                    resultPayload.put("content", result.getResult());
                    response.setPayload(resultPayload);

                } else {
                    response.setPayload(Map.of(
                            "status", "success",
                            "message", "Agent " + agentConfig.getAgentName() + " received: " + intent,
                            "agentCode", agentConfig.getAgentCode()
                    ));
                }
            } else {
                response.setPayload(Map.of(
                        "status", "success",
                        "message", "Agent " + agentConfig.getAgentName() + " acknowledged",
                        "capabilities", java.util.List.of("imageRecognition", "fileParsing")
                ));
            }
        } catch (Exception e) {
            log.error("Failed to handle A2A message", e);
            response.setPayload(Map.of(
                    "status", "error",
                    "error", e.getMessage()
            ));
        }

        return response;
    }
}
