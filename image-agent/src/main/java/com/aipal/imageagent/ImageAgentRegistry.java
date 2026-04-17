package com.aipal.imageagent;

import com.aipal.dto.A2AMessage;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;

@Component
public class ImageAgentRegistry {

    private final ImageRecognitionAgent imageAgent;

    public ImageAgentRegistry(ImageRecognitionAgent imageAgent) {
        this.imageAgent = imageAgent;
        registerHandlers();
    }

    private void registerHandlers() {
        imageAgent.registerHandler("recognize", message -> {
            A2AMessage response = new A2AMessage();
            response.setSourceAgent(imageAgent.getAgentCode());
            response.setTargetAgent(message.getSourceAgent());
            response.setSessionId(message.getSessionId());
            response.setAction(A2AMessage.Action.respond);
            response.setPayload(Map.of(
                "status", "success",
                "message", "Image recognition handler registered",
                "agent", imageAgent.getAgentName()
            ));
            return response;
        });

        imageAgent.registerHandler("process", message -> {
            A2AMessage response = new A2AMessage();
            response.setSourceAgent(imageAgent.getAgentCode());
            response.setTargetAgent(message.getSourceAgent());
            response.setSessionId(message.getSessionId());
            response.setAction(A2AMessage.Action.respond);
            response.setPayload(Map.of(
                "status", "success",
                "message", "Document processing handler registered",
                "agent", imageAgent.getAgentName()
            ));
            return response;
        });

        imageAgent.registerHandler("health", message -> {
            A2AMessage response = new A2AMessage();
            response.setSourceAgent(imageAgent.getAgentCode());
            response.setTargetAgent(message.getSourceAgent());
            response.setSessionId(message.getSessionId());
            response.setAction(A2AMessage.Action.respond);
            response.setPayload(Map.of(
                "status", "success",
                "agent", imageAgent.getAgentName(),
                "agentCode", imageAgent.getAgentCode(),
                "health", "healthy"
            ));
            return response;
        });
    }

    public ImageRecognitionAgent getImageAgent() {
        return imageAgent;
    }

    public A2AMessage.Handler getHandler(String action) {
        return imageAgent.getHandler(action);
    }

    public Map<String, A2AMessage.Handler> getAllHandlers() {
        return imageAgent.getAllHandlers();
    }
}
