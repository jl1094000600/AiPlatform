package com.aipal.imageagent;

import com.aipal.dto.A2AMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ImageRecognitionAgentTest {

    @Mock
    private ChatClient chatClient;

    @Test
    void testGetAgentCode() {
        ImageRecognitionAgent agent = new ImageRecognitionAgent(chatClient, null);
        assertEquals("image-agent", agent.getAgentCode());
    }

    @Test
    void testGetAgentName() {
        ImageRecognitionAgent agent = new ImageRecognitionAgent(chatClient, null);
        assertEquals("图像识别Agent", agent.getAgentName());
    }

    @Test
    void testHandleMessageWithNullPayload() {
        ImageRecognitionAgent agent = new ImageRecognitionAgent(chatClient, null);
        A2AMessage message = new A2AMessage();
        message.setSourceAgent("test-source");
        message.setSessionId("test-session");

        A2AMessage response = agent.handleMessage(message);

        assertEquals("image-agent", response.getSourceAgent());
        assertEquals("test-source", response.getTargetAgent());
        assertEquals(A2AMessage.Action.respond, response.getAction());
        assertEquals("success", response.getPayload().get("status"));
    }

    @Test
    void testHandleHealthAction() {
        ImageRecognitionAgent agent = new ImageRecognitionAgent(chatClient, null);
        A2AMessage message = new A2AMessage();
        message.setSourceAgent("test-source");
        message.setSessionId("test-session");
        message.setPayload(Map.of("action", "health"));

        A2AMessage response = agent.handleMessage(message);

        assertEquals("success", response.getPayload().get("status"));
        assertEquals("图像识别Agent", response.getPayload().get("agent"));
        assertEquals("image-agent", response.getPayload().get("agentCode"));
        assertEquals("healthy", response.getPayload().get("health"));
    }

    @Test
    void testRegisterHandler() {
        ImageRecognitionAgent agent = new ImageRecognitionAgent(chatClient, null);

        agent.registerHandler("custom", msg -> {
            A2AMessage resp = new A2AMessage();
            resp.setSourceAgent(agent.getAgentCode());
            resp.setAction(A2AMessage.Action.respond);
            return resp;
        });

        assertNotNull(agent.getHandler("custom"));
    }

    @Test
    void testRecognizeImageWithInvalidExtension() {
        ImageRecognitionAgent agent = new ImageRecognitionAgent(chatClient, null);

        assertThrows(IllegalArgumentException.class, () -> {
            agent.recognizeImage(null);
        });
    }

    @Test
    void testProcessDocumentWithInvalidExtension() {
        ImageRecognitionAgent agent = new ImageRecognitionAgent(chatClient, null);

        assertThrows(IllegalArgumentException.class, () -> {
            agent.processDocument(null);
        });
    }
}
