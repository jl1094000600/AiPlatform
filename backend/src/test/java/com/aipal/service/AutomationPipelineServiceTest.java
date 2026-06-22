package com.aipal.service;

import com.aipal.dto.AutomationApprovalRequest;
import com.aipal.dto.AutomationCodeFeedbackRequest;
import com.aipal.dto.AutomationPipelineRequest;
import com.aipal.entity.AutomationApproval;
import com.aipal.entity.AutomationCodeRequirementFeedback;
import com.aipal.entity.AutomationGenerationJob;
import com.aipal.entity.AutomationGeneratedCodeBatch;
import com.aipal.entity.AiModel;
import com.aipal.entity.AutomationPipeline;
import com.aipal.entity.AutomationStageRun;
import com.aipal.mapper.AiModelMapper;
import com.aipal.mapper.AutomationApprovalMapper;
import com.aipal.mapper.AutomationCodeRequirementFeedbackMapper;
import com.aipal.mapper.AutomationGenerationJobMapper;
import com.aipal.mapper.AutomationGeneratedCodeBatchMapper;
import com.aipal.mapper.AutomationGeneratedCodeFileMapper;
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

        AutomationPipelineService service = new AutomationPipelineService(pipelineMapper, stageRunMapper, approvalMapper,
                generationJobMapper, mock(AutomationGeneratedCodeBatchMapper.class), mock(AutomationGeneratedCodeFileMapper.class),
                mock(AutomationCodeRequirementFeedbackMapper.class), modelMapper, mock(SkillService.class), mock(AutomationBuildTestExecutionService.class), mock(AutomationDeployProfileService.class),
                mock(AutomationDeploymentExecutionService.class), mock(CodeQualityService.class), mock(AiOutputGovernanceService.class), mock(UserMemoryService.class), mock(BadCaseService.class));
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
    void createsPipelineWithOptionalSkillSnapshot() {
        AutomationPipelineMapper pipelineMapper = mock(AutomationPipelineMapper.class);
        AutomationStageRunMapper stageRunMapper = mock(AutomationStageRunMapper.class);
        AutomationApprovalMapper approvalMapper = mock(AutomationApprovalMapper.class);
        AutomationGenerationJobMapper generationJobMapper = mock(AutomationGenerationJobMapper.class);
        AiModelMapper modelMapper = mock(AiModelMapper.class);
        SkillService skillService = mock(SkillService.class);
        List<AutomationPipeline> insertedPipelines = new ArrayList<>();
        List<AutomationGenerationJob> insertedJobs = new ArrayList<>();
        String snapshot = "{\"skillName\":\"Delivery Skill\",\"functionDefinitions\":[]}";

        when(skillService.requireEnabledSkillSnapshot(9L)).thenReturn(snapshot);
        doAnswer(invocation -> {
            AutomationPipeline pipeline = invocation.getArgument(0);
            pipeline.setId(1L);
            insertedPipelines.add(pipeline);
            return 1;
        }).when(pipelineMapper).insert(any());
        doAnswer(invocation -> {
            AutomationStageRun stage = invocation.getArgument(0);
            stage.setId((long) stage.getStageOrder());
            return 1;
        }).when(stageRunMapper).insert(any());
        doAnswer(invocation -> {
            insertedJobs.add(invocation.getArgument(0));
            return 1;
        }).when(generationJobMapper).insert(any());

        AutomationPipelineService service = new AutomationPipelineService(pipelineMapper, stageRunMapper, approvalMapper,
                generationJobMapper, mock(AutomationGeneratedCodeBatchMapper.class), mock(AutomationGeneratedCodeFileMapper.class),
                mock(AutomationCodeRequirementFeedbackMapper.class), modelMapper, skillService, mock(AutomationBuildTestExecutionService.class), mock(AutomationDeployProfileService.class),
                mock(AutomationDeploymentExecutionService.class), mock(CodeQualityService.class), mock(AiOutputGovernanceService.class), mock(UserMemoryService.class), mock(BadCaseService.class));
        AutomationPipelineRequest request = new AutomationPipelineRequest();
        request.setProductLine("Core");
        request.setProjectName("AI Platform");
        request.setRequirementTitle("Automated delivery");
        request.setSkillId(9L);

        AutomationPipeline pipeline = service.createPipeline(request);

        assertEquals(9L, pipeline.getSkillId());
        assertEquals(snapshot, pipeline.getSkillSnapshot());
        assertEquals(snapshot, request.getSkillSnapshot());
        assertEquals(snapshot, insertedPipelines.get(0).getSkillSnapshot());
        assertEquals(true, insertedJobs.get(0).getContextSnapshot().contains("Delivery Skill"));
    }

    @Test
    void createsCodeQualityStageWithSeparateQualityModel() {
        AutomationPipelineMapper pipelineMapper = mock(AutomationPipelineMapper.class);
        AutomationStageRunMapper stageRunMapper = mock(AutomationStageRunMapper.class);
        AutomationApprovalMapper approvalMapper = mock(AutomationApprovalMapper.class);
        AutomationGenerationJobMapper generationJobMapper = mock(AutomationGenerationJobMapper.class);
        AiModelMapper modelMapper = mock(AiModelMapper.class);
        CodeQualityService codeQualityService = mock(CodeQualityService.class);
        List<AutomationStageRun> insertedStages = new ArrayList<>();

        when(codeQualityService.requireEnabledStandardSnapshot(3L))
                .thenReturn(new CodeQualityService.StandardSnapshot(3L, "{\"standardName\":\"Default\"}", "{\"overallScoreMin\":80}"));
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

        AutomationPipelineService service = new AutomationPipelineService(pipelineMapper, stageRunMapper, approvalMapper,
                generationJobMapper, mock(AutomationGeneratedCodeBatchMapper.class), mock(AutomationGeneratedCodeFileMapper.class),
                mock(AutomationCodeRequirementFeedbackMapper.class), modelMapper, mock(SkillService.class), mock(AutomationBuildTestExecutionService.class), mock(AutomationDeployProfileService.class),
                mock(AutomationDeploymentExecutionService.class), codeQualityService, mock(AiOutputGovernanceService.class), mock(UserMemoryService.class), mock(BadCaseService.class));
        AutomationPipelineRequest request = new AutomationPipelineRequest();
        request.setProductLine("Core");
        request.setProjectName("AI Platform");
        request.setRequirementTitle("Automated delivery");
        request.setAiModelCode("generation-model");
        request.setCodeQualityEnabled(true);
        request.setCodeQualityStandardId(3L);
        request.setQualityModelCode("quality-model");

        AutomationPipeline pipeline = service.createPipeline(request);

        assertEquals("quality-model", pipeline.getQualityModelCode());
        assertEquals(8, insertedStages.size());
        AutomationStageRun codeStage = insertedStages.stream()
                .filter(stage -> "code_generation".equals(stage.getStageKey()))
                .findFirst()
                .orElseThrow();
        AutomationStageRun qualityStage = insertedStages.stream()
                .filter(stage -> "code_quality_evaluation".equals(stage.getStageKey()))
                .findFirst()
                .orElseThrow();
        assertEquals("generation-model", codeStage.getAiModelCode());
        assertEquals("quality-model", qualityStage.getAiModelCode());
    }

    @Test
    void rejectsCodeQualityWithoutQualityModel() {
        AutomationPipelineService service = new AutomationPipelineService(
                mock(AutomationPipelineMapper.class),
                mock(AutomationStageRunMapper.class),
                mock(AutomationApprovalMapper.class),
                mock(AutomationGenerationJobMapper.class),
                mock(AutomationGeneratedCodeBatchMapper.class),
                mock(AutomationGeneratedCodeFileMapper.class),
                mock(AutomationCodeRequirementFeedbackMapper.class),
                mock(AiModelMapper.class),
                mock(SkillService.class),
                mock(AutomationBuildTestExecutionService.class),
                mock(AutomationDeployProfileService.class),
                mock(AutomationDeploymentExecutionService.class),
                mock(CodeQualityService.class),
                mock(AiOutputGovernanceService.class),
                mock(UserMemoryService.class), mock(BadCaseService.class)
        );
        AutomationPipelineRequest request = new AutomationPipelineRequest();
        request.setProductLine("Core");
        request.setProjectName("AI Platform");
        request.setRequirementTitle("Automated delivery");
        request.setCodeQualityEnabled(true);
        request.setCodeQualityStandardId(3L);

        assertThrows(IllegalArgumentException.class, () -> service.createPipeline(request));
    }

    @Test
    void rejectsUnavailableSkillWhenCreatingPipeline() {
        SkillService skillService = mock(SkillService.class);
        when(skillService.requireEnabledSkillSnapshot(9L)).thenThrow(new IllegalArgumentException("Skill is disabled: 9"));
        AutomationPipelineService service = new AutomationPipelineService(
                mock(AutomationPipelineMapper.class),
                mock(AutomationStageRunMapper.class),
                mock(AutomationApprovalMapper.class),
                mock(AutomationGenerationJobMapper.class),
                mock(AutomationGeneratedCodeBatchMapper.class),
                mock(AutomationGeneratedCodeFileMapper.class),
                mock(AutomationCodeRequirementFeedbackMapper.class),
                mock(AiModelMapper.class),
                skillService,
                mock(AutomationBuildTestExecutionService.class),
                mock(AutomationDeployProfileService.class),
                mock(AutomationDeploymentExecutionService.class),
                mock(CodeQualityService.class),
                mock(AiOutputGovernanceService.class),
                mock(UserMemoryService.class), mock(BadCaseService.class)
        );
        AutomationPipelineRequest request = new AutomationPipelineRequest();
        request.setProductLine("Core");
        request.setProjectName("AI Platform");
        request.setRequirementTitle("Automated delivery");
        request.setSkillId(9L);

        assertThrows(IllegalArgumentException.class, () -> service.createPipeline(request));
    }

    @Test
    void rejectsUnsafePrdTemplateNames() {
        AutomationPipelineService service = new AutomationPipelineService(
                mock(AutomationPipelineMapper.class),
                mock(AutomationStageRunMapper.class),
                mock(AutomationApprovalMapper.class),
                mock(AutomationGenerationJobMapper.class),
                mock(AutomationGeneratedCodeBatchMapper.class),
                mock(AutomationGeneratedCodeFileMapper.class),
                mock(AutomationCodeRequirementFeedbackMapper.class),
                mock(AiModelMapper.class),
                mock(SkillService.class),
                mock(AutomationBuildTestExecutionService.class),
                mock(AutomationDeployProfileService.class),
                mock(AutomationDeploymentExecutionService.class),
                mock(CodeQualityService.class),
                mock(AiOutputGovernanceService.class),
                mock(UserMemoryService.class), mock(BadCaseService.class)
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
                mock(AutomationGeneratedCodeBatchMapper.class),
                mock(AutomationGeneratedCodeFileMapper.class),
                mock(AutomationCodeRequirementFeedbackMapper.class),
                mock(AiModelMapper.class),
                mock(SkillService.class),
                mock(AutomationBuildTestExecutionService.class),
                mock(AutomationDeployProfileService.class),
                mock(AutomationDeploymentExecutionService.class),
                mock(CodeQualityService.class),
                mock(AiOutputGovernanceService.class),
                mock(UserMemoryService.class), mock(BadCaseService.class)
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
                mock(AutomationGeneratedCodeBatchMapper.class),
                mock(AutomationGeneratedCodeFileMapper.class),
                mock(AutomationCodeRequirementFeedbackMapper.class),
                mock(AiModelMapper.class),
                mock(SkillService.class),
                mock(AutomationBuildTestExecutionService.class),
                mock(AutomationDeployProfileService.class),
                mock(AutomationDeploymentExecutionService.class),
                mock(CodeQualityService.class),
                mock(AiOutputGovernanceService.class),
                mock(UserMemoryService.class), mock(BadCaseService.class)
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
                mock(AutomationGeneratedCodeBatchMapper.class),
                mock(AutomationGeneratedCodeFileMapper.class),
                mock(AutomationCodeRequirementFeedbackMapper.class),
                mock(AiModelMapper.class),
                mock(SkillService.class),
                mock(AutomationBuildTestExecutionService.class),
                mock(AutomationDeployProfileService.class),
                mock(AutomationDeploymentExecutionService.class),
                mock(CodeQualityService.class),
                mock(AiOutputGovernanceService.class),
                mock(UserMemoryService.class), mock(BadCaseService.class)
        );
        AutomationApproval approval = new AutomationApproval();
        approval.setId(7L);
        approval.setStatus("SUCCESS");
        when(approvalMapper.selectById(7L)).thenReturn(approval);

        assertThrows(IllegalStateException.class, () -> service.approve(7L, null));
    }

    @Test
    void approvingCodeGenerationStartsBuildAndTestStages() {
        AutomationPipelineMapper pipelineMapper = mock(AutomationPipelineMapper.class);
        AutomationStageRunMapper stageRunMapper = mock(AutomationStageRunMapper.class);
        AutomationApprovalMapper approvalMapper = mock(AutomationApprovalMapper.class);
        AutomationBuildTestExecutionService buildTestExecutionService = mock(AutomationBuildTestExecutionService.class);
        AutomationApproval approval = new AutomationApproval();
        approval.setId(7L);
        approval.setPipelineId(1L);
        approval.setStageRunId(2L);
        approval.setStatus("PENDING");
        AutomationStageRun codeStage = stage(2L, 1L, "code_generation", 2, "WAITING_APPROVAL");
        AutomationStageRun buildStage = stage(3L, 1L, "build_compile", 3, "PENDING");
        AutomationStageRun testStage = stage(4L, 1L, "test_execution", 4, "PENDING");
        AutomationPipeline pipeline = pipeline(1L, "code_generation", "RUNNING");

        when(approvalMapper.selectById(7L)).thenReturn(approval);
        when(stageRunMapper.selectById(2L)).thenReturn(codeStage);
        when(pipelineMapper.selectById(1L)).thenReturn(pipeline);
        when(stageRunMapper.selectList(any())).thenReturn(List.of(codeStage, buildStage, testStage));
        when(buildTestExecutionService.executeBuild(any(), any())).thenAnswer(invocation -> {
            AutomationStageRun stage = invocation.getArgument(1);
            stage.setStatus("SUCCESS");
            return stage;
        });
        when(buildTestExecutionService.executeTest(any(), any())).thenAnswer(invocation -> {
            AutomationStageRun stage = invocation.getArgument(1);
            stage.setStatus("SUCCESS");
            return stage;
        });

        AutomationPipelineService service = new AutomationPipelineService(
                pipelineMapper,
                stageRunMapper,
                approvalMapper,
                mock(AutomationGenerationJobMapper.class),
                mock(AutomationGeneratedCodeBatchMapper.class),
                mock(AutomationGeneratedCodeFileMapper.class),
                mock(AutomationCodeRequirementFeedbackMapper.class),
                mock(AiModelMapper.class),
                mock(SkillService.class),
                buildTestExecutionService,
                mock(AutomationDeployProfileService.class),
                mock(AutomationDeploymentExecutionService.class),
                mock(CodeQualityService.class),
                mock(AiOutputGovernanceService.class),
                mock(UserMemoryService.class), mock(BadCaseService.class)
        );
        AutomationApprovalRequest request = new AutomationApprovalRequest();
        request.setStatus("SUCCESS");

        service.approve(7L, request);

        verify(buildTestExecutionService, times(1)).executeBuild(any(), any());
        verify(buildTestExecutionService, times(1)).executeTest(any(), any());
    }

    @Test
    void rejectsManualFailedFeedbackWithoutReason() {
        AutomationGeneratedCodeBatchMapper batchMapper = mock(AutomationGeneratedCodeBatchMapper.class);
        AutomationCodeRequirementFeedbackMapper feedbackMapper = mock(AutomationCodeRequirementFeedbackMapper.class);
        AutomationGeneratedCodeBatch batch = codeBatch();
        when(batchMapper.selectById(30L)).thenReturn(batch);
        AutomationPipelineService service = new AutomationPipelineService(
                mock(AutomationPipelineMapper.class),
                mock(AutomationStageRunMapper.class),
                mock(AutomationApprovalMapper.class),
                mock(AutomationGenerationJobMapper.class),
                batchMapper,
                mock(AutomationGeneratedCodeFileMapper.class),
                feedbackMapper,
                mock(AiModelMapper.class),
                mock(SkillService.class),
                mock(AutomationBuildTestExecutionService.class),
                mock(AutomationDeployProfileService.class),
                mock(AutomationDeploymentExecutionService.class),
                mock(CodeQualityService.class),
                mock(AiOutputGovernanceService.class),
                mock(UserMemoryService.class), mock(BadCaseService.class)
        );
        AutomationCodeFeedbackRequest request = new AutomationCodeFeedbackRequest();
        request.setBatchId(30L);
        request.setAlignmentStatus("FAILED");

        assertThrows(IllegalArgumentException.class, () -> service.submitCodeFeedback(1L, request));
        verify(feedbackMapper, never()).insert(any());
    }

    @Test
    void blocksCodeGenerationWhenAiAndManualFeedbackBothFail() {
        AutomationPipelineMapper pipelineMapper = mock(AutomationPipelineMapper.class);
        AutomationStageRunMapper stageRunMapper = mock(AutomationStageRunMapper.class);
        AutomationApprovalMapper approvalMapper = mock(AutomationApprovalMapper.class);
        AutomationGeneratedCodeBatchMapper batchMapper = mock(AutomationGeneratedCodeBatchMapper.class);
        AutomationCodeRequirementFeedbackMapper feedbackMapper = mock(AutomationCodeRequirementFeedbackMapper.class);
        AutomationGeneratedCodeBatch batch = codeBatch();
        AutomationStageRun codeStage = stage(2L, 1L, "code_generation", 2, "WAITING_APPROVAL");
        AutomationPipeline pipeline = pipeline(1L, "code_generation", "RUNNING");
        AutomationCodeRequirementFeedback ai = feedback(30L, "AI", "FAILED", "AI reason");
        AutomationCodeRequirementFeedback manual = feedback(30L, "MANUAL", "FAILED", "Manual reason");

        when(batchMapper.selectById(30L)).thenReturn(batch);
        when(feedbackMapper.selectOne(any())).thenReturn(ai, manual, ai, manual, ai, manual, ai, manual, ai, manual);
        when(stageRunMapper.selectById(2L)).thenReturn(codeStage);
        when(pipelineMapper.selectById(1L)).thenReturn(pipeline);
        when(stageRunMapper.selectList(any())).thenReturn(List.of(codeStage));
        when(approvalMapper.selectList(any())).thenReturn(List.of());

        AutomationPipelineService service = new AutomationPipelineService(
                pipelineMapper,
                stageRunMapper,
                approvalMapper,
                mock(AutomationGenerationJobMapper.class),
                batchMapper,
                mock(AutomationGeneratedCodeFileMapper.class),
                feedbackMapper,
                mock(AiModelMapper.class),
                mock(SkillService.class),
                mock(AutomationBuildTestExecutionService.class),
                mock(AutomationDeployProfileService.class),
                mock(AutomationDeploymentExecutionService.class),
                mock(CodeQualityService.class),
                mock(AiOutputGovernanceService.class),
                mock(UserMemoryService.class), mock(BadCaseService.class)
        );
        AutomationCodeFeedbackRequest request = new AutomationCodeFeedbackRequest();
        request.setBatchId(30L);
        request.setAlignmentStatus("FAILED");
        request.setFailureReason("Manual reason");

        Map<String, Object> result = service.submitCodeFeedback(1L, request);

        assertEquals(true, result.get("doubleFailed"));
        assertEquals("REJECTED", codeStage.getStatus());
        assertEquals(true, codeStage.getErrorMessage().contains("AI reason"));
        assertEquals(true, codeStage.getErrorMessage().contains("Manual reason"));
        verify(feedbackMapper, times(1)).insert(any());
        verify(stageRunMapper, times(1)).updateById(codeStage);
    }

    @Test
    void projectDirectoryTreeStartsAtRootAndExcludesBuildArtifacts() {
        AutomationPipelineService service = new AutomationPipelineService(
                mock(AutomationPipelineMapper.class),
                mock(AutomationStageRunMapper.class),
                mock(AutomationApprovalMapper.class),
                mock(AutomationGenerationJobMapper.class),
                mock(AutomationGeneratedCodeBatchMapper.class),
                mock(AutomationGeneratedCodeFileMapper.class),
                mock(AutomationCodeRequirementFeedbackMapper.class),
                mock(AiModelMapper.class),
                mock(SkillService.class),
                mock(AutomationBuildTestExecutionService.class),
                mock(AutomationDeployProfileService.class),
                mock(AutomationDeploymentExecutionService.class),
                mock(CodeQualityService.class),
                mock(AiOutputGovernanceService.class),
                mock(UserMemoryService.class), mock(BadCaseService.class)
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
                mock(AutomationGeneratedCodeBatchMapper.class),
                mock(AutomationGeneratedCodeFileMapper.class),
                mock(AutomationCodeRequirementFeedbackMapper.class),
                mock(AiModelMapper.class),
                mock(SkillService.class),
                mock(AutomationBuildTestExecutionService.class),
                mock(AutomationDeployProfileService.class),
                mock(AutomationDeploymentExecutionService.class),
                mock(CodeQualityService.class),
                mock(AiOutputGovernanceService.class),
                mock(UserMemoryService.class), mock(BadCaseService.class)
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
                mock(AutomationGeneratedCodeBatchMapper.class),
                mock(AutomationGeneratedCodeFileMapper.class),
                mock(AutomationCodeRequirementFeedbackMapper.class),
                mock(AiModelMapper.class),
                mock(SkillService.class),
                mock(AutomationBuildTestExecutionService.class),
                mock(AutomationDeployProfileService.class),
                mock(AutomationDeploymentExecutionService.class),
                mock(CodeQualityService.class),
                mock(AiOutputGovernanceService.class),
                mock(UserMemoryService.class), mock(BadCaseService.class)
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

    private AutomationGeneratedCodeBatch codeBatch() {
        AutomationGeneratedCodeBatch batch = new AutomationGeneratedCodeBatch();
        batch.setId(30L);
        batch.setPipelineId(1L);
        batch.setStageRunId(2L);
        batch.setGenerationJobId(3L);
        return batch;
    }

    private AutomationCodeRequirementFeedback feedback(Long batchId, String source, String status, String failureReason) {
        AutomationCodeRequirementFeedback feedback = new AutomationCodeRequirementFeedback();
        feedback.setBatchId(batchId);
        feedback.setFeedbackSource(source);
        feedback.setAlignmentStatus(status);
        feedback.setFailureReason(failureReason);
        return feedback;
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
