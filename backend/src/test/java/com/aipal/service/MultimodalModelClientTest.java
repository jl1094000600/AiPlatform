package com.aipal.service;

import com.aipal.entity.AiModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MultimodalModelClientTest {

    @Test
    void sendsVisionRequestWithDataUrlAndReturnsMessageContent() {
        ModelService modelService = mock(ModelService.class);
        when(modelService.getDefaultEnabledModel(ModelService.CAPABILITY_VISION))
                .thenReturn(model(ModelService.CAPABILITY_VISION, "vision-1", "https://models.example/v1"));
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://models.example/v1/chat/completions"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer secret"))
                .andExpect(content().string(containsString("data:image/png;base64,aW1hZ2U=")))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"a dashboard screenshot\"}}]}",
                        MediaType.APPLICATION_JSON));
        MultimodalModelClient client = new MultimodalModelClient(modelService, new ObjectMapper(), builder);

        String result = client.analyzeImage("image".getBytes(StandardCharsets.UTF_8), "image/png", "screen.png");

        assertEquals("a dashboard screenshot", result);
        server.verify();
    }

    @Test
    void sendsMultipartAsrRequestAndReturnsTranscription() {
        ModelService modelService = mock(ModelService.class);
        when(modelService.getDefaultEnabledModel(ModelService.CAPABILITY_ASR))
                .thenReturn(model(ModelService.CAPABILITY_ASR, "whisper-1", "https://models.example/v1/"));
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://models.example/v1/audio/transcriptions"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer secret"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, containsString("multipart/form-data")))
                .andExpect(content().string(containsString("speech.wav")))
                .andExpect(content().string(containsString("whisper-1")))
                .andRespond(withSuccess("{\"text\":\"hello from audio\"}", MediaType.APPLICATION_JSON));
        MultimodalModelClient client = new MultimodalModelClient(modelService, new ObjectMapper(), builder);

        String result = client.transcribeAudio(
                "audio".getBytes(StandardCharsets.UTF_8), "audio/wav", "speech.wav");

        assertEquals("hello from audio", result);
        server.verify();
    }

    @Test
    void reportsMissingDefaultModelClearly() {
        ModelService modelService = mock(ModelService.class);
        when(modelService.getDefaultEnabledModel(ModelService.CAPABILITY_VISION)).thenReturn(null);
        MultimodalModelClient client = new MultimodalModelClient(
                modelService, new ObjectMapper(), RestClient.builder());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> client.analyzeImage(new byte[]{1}, "image/png", "image.png"));

        assertTrue(error.getMessage().contains("No enabled default VISION model"));
    }

    private AiModel model(String capability, String code, String endpoint) {
        AiModel model = new AiModel();
        model.setCapabilityType(capability);
        model.setModelCode(code);
        model.setEndpoint(endpoint);
        model.setApiKey("secret");
        model.setStatus(1);
        model.setDefaultForCapability(1);
        return model;
    }
}
