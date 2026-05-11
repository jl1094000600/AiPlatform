package com.aipal.service;

import com.aipal.dto.AutomationPipelineRequest;
import com.aipal.entity.AutomationApproval;
import com.aipal.entity.AutomationGenerationJob;
import com.aipal.entity.AiModel;
import com.aipal.entity.AutomationPipeline;
import com.aipal.entity.AutomationStageRun;
import com.aipal.mapper.AiModelMapper;
import com.aipal.mapper.AutomationApprovalMapper;
import com.aipal.mapper.AutomationGenerationJobMapper;
import com.aipal.mapper.AutomationPipelineMapper;
import com.aipal.mapper.AutomationStageRunMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutomationPipelineServiceTest {

    @Test
    void createsPipelineWithSevenStandardStages() {
        AutomationPipelineMapper pipelineMapper = mock(AutomationPipelineMapper.class);
        AutomationStageRunMapper stageRunMapper = mock(AutomationStageRunMapper.class);
        AutomationApprovalMapper approvalMapper = mock(AutomationApprovalMapper.class);
        AutomationGenerationJobMapper generationJobMapper = mock(AutomationGenerationJobMapper.class);
        AiModelMapper modelMapper = mock(AiModelMapper.class);
        List<AutomationStageRun> insertedStages = new ArrayList<>();

        doAnswer(invocation -> {
            AutomationPipeline pipeline = invocation.getArgument(0);
            pipeline.setId(1L);
            return 1;
        }).when(pipelineMapper).insert(any());
        doAnswer(invocation -> {
            AutomationStageRun stage = invocation.getArgument(0);
            insertedStages.add(stage);
            return 1;
        }).when(stageRunMapper).insert(any());

        AutomationPipelineService service = new AutomationPipelineService(pipelineMapper, stageRunMapper, approvalMapper, generationJobMapper, modelMapper);
        AutomationPipelineRequest request = new AutomationPipelineRequest();
        request.setProductLine("Core");
        request.setProjectName("AI Platform");
        request.setRequirementTitle("Automated delivery");

        AutomationPipeline pipeline = service.createPipeline(request);

        assertNotNull(pipeline.getPipelineCode());
        assertEquals("default-prd-template.md", pipeline.getTemplateFile());
        assertEquals(7, insertedStages.size());
        assertEquals("requirement_analysis", insertedStages.get(0).getStageKey());
        assertEquals("QUEUED", insertedStages.get(0).getStatus());
        assertEquals("delivery_report", insertedStages.get(6).getStageKey());
        verify(stageRunMapper, times(7)).insert(any());
        verify(generationJobMapper, times(1)).insert(any());
    }

    @Test
    void rejectsUnsafePrdTemplateNames() {
        AutomationPipelineService service = new AutomationPipelineService(
                mock(AutomationPipelineMapper.class),
                mock(AutomationStageRunMapper.class),
                mock(AutomationApprovalMapper.class),
                mock(AutomationGenerationJobMapper.class),
                mock(AiModelMapper.class)
        );
        AutomationPipelineRequest request = new AutomationPipelineRequest();
        request.setProductLine("Core");
        request.setProjectName("AI Platform");
        request.setRequirementTitle("Automated delivery");
        request.setTemplateFile("../default-code-template.md");

        assertThrows(IllegalArgumentException.class, () -> service.createPipeline(request));
    }

    @Test
    void blocksRunningLaterStageBeforePreviousApproval() {
        AutomationPipelineMapper pipelineMapper = mock(AutomationPipelineMapper.class);
        AutomationStageRunMapper stageRunMapper = mock(AutomationStageRunMapper.class);
        AutomationPipelineService service = new AutomationPipelineService(
                pipelineMapper,
                stageRunMapper,
                mock(AutomationApprovalMapper.class),
                mock(AutomationGenerationJobMapper.class),
                mock(AiModelMapper.class)
        );
        AutomationPipeline pipeline = pipeline(1L, "build_compile", "RUNNING");
        AutomationStageRun requirement = stage(10L, 1L, "requirement_analysis", 1, "WAITING_APPROVAL");
        AutomationStageRun build = stage(12L, 1L, "build_compile", 3, "PENDING");

        when(stageRunMapper.selectById(12L)).thenReturn(build);
        when(pipelineMapper.selectById(1L)).thenReturn(pipeline);
        when(stageRunMapper.selectList(any())).thenReturn(List.of(requirement, build));

        assertThrows(IllegalStateException.class, () -> service.runStage(12L));
    }

    @Test
    void allowsOnlyRejectedStageWhenPipelineBlocked() {
        AutomationPipelineMapper pipelineMapper = mock(AutomationPipelineMapper.class);
        AutomationStageRunMapper stageRunMapper = mock(AutomationStageRunMapper.class);
        AutomationPipelineService service = new AutomationPipelineService(
                pipelineMapper,
                stageRunMapper,
                mock(AutomationApprovalMapper.class),
                mock(AutomationGenerationJobMapper.class),
                mock(AiModelMapper.class)
        );
        AutomationPipeline pipeline = pipeline(1L, "build_compile", "BLOCKED");
        AutomationStageRun rejectedBuild = stage(12L, 1L, "build_compile", 3, "REJECTED");
        AutomationStageRun test = stage(13L, 1L, "test_execution", 4, "PENDING");

        when(stageRunMapper.selectById(13L)).thenReturn(test);
        when(pipelineMapper.selectById(1L)).thenReturn(pipeline);
        when(stageRunMapper.selectList(any())).thenReturn(List.of(rejectedBuild, test));

        assertThrows(IllegalStateException.class, () -> service.runStage(13L));
    }

    @Test
    void rejectsAlreadyReviewedApproval() {
        AutomationApprovalMapper approvalMapper = mock(AutomationApprovalMapper.class);
        AutomationPipelineService service = new AutomationPipelineService(
                mock(AutomationPipelineMapper.class),
                mock(AutomationStageRunMapper.class),
                approvalMapper,
                mock(AutomationGenerationJobMapper.class),
                mock(AiModelMapper.class)
        );
        AutomationApproval approval = new AutomationApproval();
        approval.setId(7L);
        approval.setStatus("SUCCESS");
        when(approvalMapper.selectById(7L)).thenReturn(approval);

        assertThrows(IllegalStateException.class, () -> service.approve(7L, null));
    }

    @Test
    void projectDirectoryTreeStartsAtRootAndExcludesBuildArtifacts() {
        AutomationPipelineService service = new AutomationPipelineService(
                mock(AutomationPipelineMapper.class),
                mock(AutomationStageRunMapper.class),
                mock(AutomationApprovalMapper.class),
                mock(AutomationGenerationJobMapper.class),
                mock(AiModelMapper.class)
        );

        Map<String, Object> tree = service.getProjectDirectoryTree();

        assertEquals(true, tree.get("root"));
        String flattened = tree.toString();
        assertEquals(true, flattened.contains("backend"));
        assertEquals(false, flattened.contains("node_modules"));
        assertEquals(false, flattened.contains("generated-code"));
        assertEquals(false, flattened.contains("target"));
    }

    @Test
    void staleCodeGenerationJobDoesNotUpdateStageOrCreateApproval() throws Exception {
        AutomationStageRunMapper stageRunMapper = mock(AutomationStageRunMapper.class);
        AutomationApprovalMapper approvalMapper = mock(AutomationApprovalMapper.class);
        AutomationGenerationJobMapper generationJobMapper = mock(AutomationGenerationJobMapper.class);
        AutomationPipelineService service = new AutomationPipelineService(
                mock(AutomationPipelineMapper.class),
                stageRunMapper,
                approvalMapper,
                generationJobMapper,
                mock(AiModelMapper.class)
        );
        AutomationGenerationJob stale = generationJob(1L);
        AutomationGenerationJob latest = generationJob(2L);
        when(generationJobMapper.selectOne(any())).thenReturn(latest);

        Method method = AutomationPipelineService.class.getDeclaredMethod("completeCodeGeneration", AutomationGenerationJob.class);
        method.setAccessible(true);
        method.invoke(service, stale);

        verify(stageRunMapper, never()).updateById(any());
        verify(approvalMapper, never()).insert(any());
    }

    @Test
    void capsMiniMaxMaxTokensAtProviderLimit() {
        AutomationPipelineService service = new AutomationPipelineService(
                mock(AutomationPipelineMapper.class),
                mock(AutomationStageRunMapper.class),
                mock(AutomationApprovalMapper.class),
                mock(AutomationGenerationJobMapper.class),
                mock(AiModelMapper.class)
        );
        AiModel model = new AiModel();
        model.setProvider("MiniMax");
        model.setMaxTokens(200000);

        assertEquals(8192, service.resolveMaxTokens(model));
    }

    private AutomationPipeline pipeline(Long id, String currentStage, String status) {
        AutomationPipeline pipeline = new AutomationPipeline();
        pipeline.setId(id);
        pipeline.setCurrentStage(currentStage);
        pipeline.setStatus(status);
        return pipeline;
    }

    private AutomationStageRun stage(Long id, Long pipelineId, String key, int order, String status) {
        AutomationStageRun stage = new AutomationStageRun();
        stage.setId(id);
        stage.setPipelineId(pipelineId);
        stage.setStageKey(key);
        stage.setStageName(key);
        stage.setStageOrder(order);
        stage.setStatus(status);
        stage.setRequiresApproval(1);
        return stage;
    }

    private AutomationGenerationJob generationJob(Long id) {
        AutomationGenerationJob job = new AutomationGenerationJob();
        job.setId(id);
        job.setPipelineId(1L);
        job.setStageRunId(2L);
        job.setJobType("CODE");
        job.setContextSnapshot("{}");
        return job;
    }
}
