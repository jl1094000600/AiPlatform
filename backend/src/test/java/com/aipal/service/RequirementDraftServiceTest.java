package com.aipal.service;

import com.aipal.dto.AutomationPipelineRequest;
import com.aipal.dto.RequirementDraftMergeRequest;
import com.aipal.dto.RequirementPrdRequest;
import com.aipal.entity.AiModel;
import com.aipal.entity.AutomationPipeline;
import com.aipal.entity.RequirementAttachment;
import com.aipal.entity.RequirementParseTask;
import com.aipal.mapper.AiModelMapper;
import com.aipal.mapper.AutomationPipelineMapper;
import com.aipal.mapper.AutomationStageRunMapper;
import com.aipal.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequirementDraftServiceTest {
    @Mock private RequirementAttachmentService attachmentService;
    @Mock private AutomationPipelineService automationPipelineService;
    @Mock private AutomationPipelineMapper pipelineMapper;
    @Mock private AutomationStageRunMapper stageMapper;
    @Mock private AiModelMapper modelMapper;

    private RequirementDraftService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(new TenantContext.Context(7L, "creator", 3L, "tenant-3",
                false, Set.of("developer"), Set.of()));
        service = new RequirementDraftService(attachmentService, automationPipelineService,
                pipelineMapper, stageMapper, modelMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void mergeUsesEditedAttachmentResult() {
        RequirementAttachment attachment = new RequirementAttachment();
        attachment.setId(11L);
        attachment.setAttachmentType("IMAGE");
        attachment.setOriginalFileName("prototype.png");
        RequirementParseTask task = new RequirementParseTask();
        task.setRawResult("raw OCR");
        task.setEditedResult("用户修订后的原型需求");
        when(attachmentService.requireOwnedAttachment(11L)).thenReturn(attachment);
        when(attachmentService.latestSuccessfulTask(11L)).thenReturn(task);

        RequirementDraftMergeRequest request = new RequirementDraftMergeRequest();
        request.setOriginalText("原始文字需求");
        request.setAttachmentIds(List.of(11L));
        Map<String, String> result = service.merge(request);

        assertEquals(result.get("draftText"), result.get("mergedText"));
        assertFalse(result.get("draftText").contains("raw OCR"));
        assertEquals(true, result.get("draftText").contains("用户修订后的原型需求"));
    }

    @Test
    void createPrdReusesExistingPipelineServiceAndDefaultChatModel() {
        AiModel model = new AiModel();
        model.setId(21L);
        model.setModelCode("chat-default");
        when(modelMapper.selectOne(any())).thenReturn(model);
        AutomationPipeline pipeline = new AutomationPipeline();
        pipeline.setId(31L);
        pipeline.setStatus("RUNNING");
        when(automationPipelineService.createPipeline(any())).thenReturn(pipeline);

        RequirementPrdRequest request = new RequirementPrdRequest();
        request.setTitle("智能饮食助手");
        request.setRequirementText("帮助用户制定健康饮食计划");
        Map<String, Object> result = service.createPrd(request);

        ArgumentCaptor<AutomationPipelineRequest> captor = ArgumentCaptor.forClass(AutomationPipelineRequest.class);
        verify(automationPipelineService).createPipeline(captor.capture());
        assertEquals(31L, result.get("pipelineId"));
        assertEquals(21L, captor.getValue().getModelId());
        assertEquals("chat-default", captor.getValue().getAiModelCode());
        assertEquals(false, captor.getValue().getGenerateFrontend());
        assertEquals(false, captor.getValue().getGenerateBackend());
        assertEquals(7L, captor.getValue().getInitiatorUserId());
    }
}
