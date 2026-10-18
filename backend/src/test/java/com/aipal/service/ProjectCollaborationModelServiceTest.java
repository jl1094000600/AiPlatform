package com.aipal.service;

import com.aipal.common.BizException;
import com.aipal.common.ErrorCode;
import com.aipal.dto.ProjectModelGatewayDtos.CoordinatorResult;
import com.aipal.dto.ProjectModelGatewayDtos.PrivateAskResult;
import com.aipal.dto.ProjectModelGatewayDtos.SafeProjectContext;
import com.aipal.entity.AiModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProjectCollaborationModelServiceTest {

    private ModelService modelService;
    private ModelGateway modelGateway;
    private ProjectCollaborationModelService service;
    private AiModel model;

    @BeforeEach
    void setUp() {
        modelService = mock(ModelService.class);
        modelGateway = mock(ModelGateway.class);
        service = new ProjectCollaborationModelService(modelService, modelGateway, new ObjectMapper());
        model = new AiModel();
        model.setModelCode("chat-model");
        model.setCapabilityType(ModelService.CAPABILITY_CHAT);
        model.setEndpoint("https://model.example.invalid/v1");
        model.setStatus(1);
        when(modelService.getDefaultEnabledModel(ModelService.CAPABILITY_CHAT)).thenReturn(model);
    }

    @Test
    void coordinatorFailsWith503WhenNoDefaultChatModelExists() {
        when(modelService.getDefaultEnabledModel(ModelService.CAPABILITY_CHAT)).thenReturn(null);

        BizException error = assertThrows(BizException.class, () -> service.coordinate(context()));

        assertEquals(ErrorCode.MODEL_NOT_CONFIGURED.getCode(), error.getCode());
        verifyNoInteractions(modelGateway);
    }

    @Test
    void privateAskFailsWith503WhenNoDefaultChatModelExists() {
        when(modelService.getDefaultEnabledModel(ModelService.CAPABILITY_CHAT)).thenReturn(null);

        BizException error = assertThrows(BizException.class,
                () -> service.privateAsk(context(), "what is the status?", List.of()));

        assertEquals(ErrorCode.MODEL_NOT_CONFIGURED.getCode(), error.getCode());
        verifyNoInteractions(modelGateway);
    }

    @Test
    void coordinatorRejectsNonJsonResponse() {
        modelReturns("this is not json");

        assertInvalid(() -> service.coordinate(context()));
    }

    @Test
    void coordinatorRejectsUnknownFields() {
        modelReturns("""
                {"summary":"ok","risks":[],"questions":[],"taskDrafts":[],"executed":true}
                """);

        assertInvalid(() -> service.coordinate(context()));
    }

    @Test
    void coordinatorRejectsMissingRequiredFields() {
        modelReturns("""
                {"summary":"ok","risks":[],"taskDrafts":[]}
                """);

        assertInvalid(() -> service.coordinate(context()));
    }

    @Test
    void coordinatorRejectsBlankSummary() {
        modelReturns("""
                {"summary":"   ","risks":[],"questions":[],"taskDrafts":[]}
                """);

        assertInvalid(() -> service.coordinate(context()));
    }

    @Test
    void coordinatorRejectsInvalidTaskDraftAndAcceptsNoPartialResult() {
        modelReturns("""
                {"summary":"ok","risks":["r"],"questions":[],
                 "taskDrafts":[{"title":"valid","description":"d"},
                               {"title":"  ","description":"d"}]}
                """);

        assertInvalid(() -> service.coordinate(context()));
    }

    @Test
    void coordinatorRejectsTaskDraftWithUnknownField() {
        modelReturns("""
                {"summary":"ok","risks":[],"questions":[],
                 "taskDrafts":[{"title":"t","description":"d","businessType":"AGENT_RUN"}]}
                """);

        assertInvalid(() -> service.coordinate(context()));
    }

    @Test
    void coordinatorRejectsOversizedSummary() {
        modelReturns("{\"summary\":\"" + "x".repeat(4_001)
                + "\",\"risks\":[],\"questions\":[],\"taskDrafts\":[]}");

        assertInvalid(() -> service.coordinate(context()));
    }

    @Test
    void coordinatorMapsValidJsonIncludingCodeFence() {
        modelReturns("""
                ```json
                {"summary":"On track","risks":["Approval delay"],"questions":["Who reviews?"],
                 "taskDrafts":[{"title":"Add review","description":"Add a reviewer",
                                "suggestedAgentId":12}]}
                ```
                """);

        CoordinatorResult result = service.coordinate(context());

        assertEquals("On track", result.summary());
        assertEquals(List.of("Approval delay"), result.risks());
        assertEquals(List.of("Who reviews?"), result.questions());
        assertEquals(1, result.taskDrafts().size());
        assertEquals("Add review", result.taskDrafts().get(0).title());
        assertEquals("Add a reviewer", result.taskDrafts().get(0).description());
        assertEquals(12L, result.taskDrafts().get(0).suggestedAgentId());
    }

    @Test
    void coordinatorAllowsNullableOptionalDraftFields() {
        modelReturns("""
                {"summary":"ok","risks":[],"questions":[],"taskDrafts":[{"title":"only title"}]}
                """);

        CoordinatorResult result = service.coordinate(context());

        assertEquals(1, result.taskDrafts().size());
        assertEquals("only title", result.taskDrafts().get(0).title());
        org.junit.jupiter.api.Assertions.assertNull(result.taskDrafts().get(0).description());
        org.junit.jupiter.api.Assertions.assertNull(result.taskDrafts().get(0).suggestedAgentId());
    }

    @Test
    void privateAskRejectsNonJsonResponse() {
        modelReturns("not json at all");

        assertInvalid(() -> service.privateAsk(context(), "what is the status?", List.of()));
    }

    @Test
    void privateAskRejectsUnknownFields() {
        modelReturns("""
                {"answer":"ok","sources":[],"limitations":[],"private":true}
                """);

        assertInvalid(() -> service.privateAsk(context(), "what is the status?", List.of()));
    }

    @Test
    void privateAskRejectsBlankAnswer() {
        modelReturns("""
                {"answer":"   ","sources":[],"limitations":[]}
                """);

        assertInvalid(() -> service.privateAsk(context(), "what is the status?", List.of()));
    }

    @Test
    void privateAskMapsValidResponse() {
        modelReturns("""
                ```json
                {"answer":"The project is on track.","sources":[
                   {"type":"PROJECT_SUMMARY","reference":"overallStatus=GREEN"}],
                 "limitations":["Only visible project data is used."]}
                ```
                """);

        PrivateAskResult result = service.privateAsk(context(), "what is the status?",
                List.of(new com.aipal.dto.ProjectModelGatewayDtos.PrivateAskTurn("user", "and risks?")));

        assertEquals("The project is on track.", result.answer());
        assertEquals(1, result.sources().size());
        assertEquals("PROJECT_SUMMARY", result.sources().get(0).type());
        assertEquals("overallStatus=GREEN", result.sources().get(0).reference());
        assertEquals(List.of("Only visible project data is used."), result.limitations());
    }

    @Test
    void privateAskRejectsOversizedAnswer() {
        modelReturns("{\"answer\":\"" + "x".repeat(8_001)
                + "\",\"sources\":[],\"limitations\":[]}");

        assertInvalid(() -> service.privateAsk(context(), "what is the status?", List.of()));
    }

    @Test
    void privateAskRejectsBlankQuestionBeforeCallingTheModel() {
        BizException error = assertThrows(BizException.class,
                () -> service.privateAsk(context(), "  ", List.of()));

        assertEquals(ErrorCode.PRIVATE_ASK_QUESTION_REQUIRED.getCode(), error.getCode());
        verifyNoInteractions(modelGateway);
    }

    private void modelReturns(String content) {
        when(modelGateway.chat(any(AiModel.class), anyString(), anyString()))
                .thenReturn(new ModelCallResult(content, 10, 10, 20));
    }

    private void assertInvalid(org.junit.jupiter.api.function.Executable executable) {
        BizException error = assertThrows(BizException.class, executable);
        assertEquals(ErrorCode.MODEL_RESPONSE_INVALID.getCode(), error.getCode());
        assertTrue(error.getMessage().contains("模型") || error.getMessage().contains("约定"),
                error.getMessage());
    }

    private SafeProjectContext context() {
        return new SafeProjectContext("proj-1", "Project One", "CUSTOMER_PROJECT", "ACTIVE", 9L,
                "GREEN", 0, 0, 0, 0, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
