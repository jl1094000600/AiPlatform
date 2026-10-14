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
import com.aipal.service.memory.MemoryProjectService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutomationPipelineServiceTest {

    @Test
    void createsPipelineWithEightStandardStagesIncludingTestCaseGeneration() {
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
                mock(AutomationDeploymentExecutionService.class), mock(CodeQualityService.class), mock(AiOutputGovernanceService.class), mock(UserMemoryService.class), mock(BadCaseService.class), mock(MemoryProjectService.class),
                mock(ModelGateway.class), mock(CodeGenerationService.class));
        AutomationPipelineRequest request = new AutomationPipelineRequest();
        request.setProductLine("Core");
        request.setProjectName("AI Platform");
        request.setRequirementTitle("Automated delivery");

        AutomationPipeline pipeline = service.createPipeline(request);

        assertNotNull(pipeline.getPipelineCode());
        assertEquals("ai-platform", pipeline.getProjectKey());
        assertEquals("ai-platform", request.getProjectKey());
        assertEquals("default-prd-template.md", pipeline.getTemplateFile());
        assertEquals(8, insertedStages.size());
        assertEquals(1L, insertedStages.get(0).getTenantId());
        assertEquals("requirement_analysis", insertedStages.get(0).getStageKey());
        assertEquals("QUEUED", insertedStages.get(0).getStatus());
        assertEquals("test_case_generation", insertedStages.get(2).getStageKey());
        assertEquals("delivery_report", insertedStages.get(7).getStageKey());
        verify(stageRunMapper, times(8)).insert(any());
        verify(generationJobMapper, times(1)).insert(any());
    }

    @Test
    void createsStableSafeProjectKeyForChineseProjectName() {
        AutomationPipelineMapper pipelineMapper = mock(AutomationPipelineMapper.class);
        AutomationStageRunMapper stageRunMapper = mock(AutomationStageRunMapper.class);
        AutomationGenerationJobMapper generationJobMapper = mock(AutomationGenerationJobMapper.class);

        doAnswer(invocation -> {
            AutomationPipeline pipeline = invocation.getArgument(0);
            pipeline.setId(1L);
            return 1;
        }).when(pipelineMapper).insert(any());
        doAnswer(invocation -> 1).when(stageRunMapper).insert(any());

        AutomationPipelineService service = new AutomationPipelineService(pipelineMapper, stageRunMapper, mock(AutomationApprovalMapper.class),
                generationJobMapper, mock(AutomationGeneratedCodeBatchMapper.class), mock(AutomationGeneratedCodeFileMapper.class),
                mock(AutomationCodeRequirementFeedbackMapper.class), mock(AiModelMapper.class), mock(SkillService.class), mock(AutomationBuildTestExecutionService.class), mock(AutomationDeployProfileService.class),
                mock(AutomationDeploymentExecutionService.class), mock(CodeQualityService.class), mock(AiOutputGovernanceService.class), mock(UserMemoryService.class), mock(BadCaseService.class), mock(MemoryProjectService.class),
                mock(ModelGateway.class), mock(CodeGenerationService.class));
        AutomationPipelineRequest firstRequest = new AutomationPipelineRequest();
        firstRequest.setProductLine("Core");
        firstRequest.setProjectName("自动分析数据agent");
        firstRequest.setRequirementTitle("Automated delivery");
        AutomationPipelineRequest secondRequest = new AutomationPipelineRequest();
        secondRequest.setProductLine("Core");
        secondRequest.setProjectName("自动分析数据agent");
        secondRequest.setRequirementTitle("Automated delivery");

        AutomationPipeline first = service.createPipeline(firstRequest);
        AutomationPipeline second = service.createPipeline(secondRequest);

        assertTrue(first.getProjectKey().matches("project-[0-9a-f]{10}"));
        assertEquals(first.getProjectKey(), second.getProjectKey());
        assertEquals(first.getProjectKey(), firstRequest.getProjectKey());
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
                mock(AutomationDeploymentExecutionService.class), mock(CodeQualityService.class), mock(AiOutputGovernanceService.class), mock(UserMemoryService.class), mock(BadCaseService.class), mock(MemoryProjectService.class),
                mock(ModelGateway.class), mock(CodeGenerationService.class));
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
                mock(AutomationDeploymentExecutionService.class), codeQualityService, mock(AiOutputGovernanceService.class), mock(UserMemoryService.class), mock(BadCaseService.class), mock(MemoryProjectService.class),
                mock(ModelGateway.class), mock(CodeGenerationService.class));
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
        assertEquals(9, insertedStages.size());
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
                mock(UserMemoryService.class), mock(BadCaseService.class), mock(MemoryProjectService.class), mock(ModelGateway.class), mock(CodeGenerationService.class)
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
                mock(UserMemoryService.class), mock(BadCaseService.class), mock(MemoryProjectService.class), mock(ModelGateway.class), mock(CodeGenerationService.class)
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
                mock(UserMemoryService.class), mock(BadCaseService.class), mock(MemoryProjectService.class), mock(ModelGateway.class), mock(CodeGenerationService.class)
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
                mock(UserMemoryService.class), mock(BadCaseService.class), mock(MemoryProjectService.class), mock(ModelGateway.class), mock(CodeGenerationService.class)
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
                mock(UserMemoryService.class), mock(BadCaseService.class), mock(MemoryProjectService.class), mock(ModelGateway.class), mock(CodeGenerationService.class)
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
                mock(UserMemoryService.class), mock(BadCaseService.class), mock(MemoryProjectService.class), mock(ModelGateway.class), mock(CodeGenerationService.class)
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
        // Legacy pipeline: no test_case_generation stage row; lookup returns null and is skipped.
        when(stageRunMapper.selectOne(any())).thenReturn(null, buildStage, testStage, testStage);
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
                mock(UserMemoryService.class), mock(BadCaseService.class), mock(MemoryProjectService.class), mock(ModelGateway.class), mock(CodeGenerationService.class)
        );
        AutomationApprovalRequest request = new AutomationApprovalRequest();
        request.setStatus("SUCCESS");

        service.approve(7L, request);

        verify(buildTestExecutionService, times(1)).executeBuild(any(), any());
        verify(buildTestExecutionService, times(1)).executeTest(any(), any());
    }

    @Test
    void approvingCodeGenerationRunsTestCaseGenerationOnNewPipelines() {
        AutomationPipelineMapper pipelineMapper = mock(AutomationPipelineMapper.class);
        AutomationStageRunMapper stageRunMapper = mock(AutomationStageRunMapper.class);
        AutomationApprovalMapper approvalMapper = mock(AutomationApprovalMapper.class);
        AutomationBuildTestExecutionService buildTestExecutionService = mock(AutomationBuildTestExecutionService.class);
        AutomationApproval approval = new AutomationApproval();
        approval.setId(8L);
        approval.setPipelineId(2L);
        approval.setStageRunId(20L);
        approval.setStatus("PENDING");
        AutomationStageRun codeStage = stage(20L, 2L, "code_generation", 2, "WAITING_APPROVAL");
        try {
            codeStage.setArtifactPath(java.nio.file.Files.createTempDirectory("gen-code").toString());
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        AutomationStageRun testCasesStage = stage(21L, 2L, "test_case_generation", 3, "PENDING");
        AutomationStageRun buildStage = stage(22L, 2L, "build_compile", 4, "PENDING");
        AutomationStageRun testStage = stage(23L, 2L, "test_execution", 5, "PENDING");
        AutomationPipeline pipeline = pipeline(2L, "code_generation", "RUNNING");

        when(approvalMapper.selectById(8L)).thenReturn(approval);
        when(stageRunMapper.selectById(20L)).thenReturn(codeStage);
        when(pipelineMapper.selectById(2L)).thenReturn(pipeline);
        when(stageRunMapper.selectList(any())).thenReturn(List.of(codeStage, testCasesStage, buildStage, testStage));
        // Order of selectOne lookups:
        // 1) test_case_generation stage  2) code_generation (artifact root)  3) build_compile
        // 4) test_execution  5) test_execution (auto-deploy check)
        when(stageRunMapper.selectOne(any())).thenReturn(testCasesStage, codeStage, buildStage, testStage, testStage);
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
                mock(UserMemoryService.class), mock(BadCaseService.class), mock(MemoryProjectService.class), mock(ModelGateway.class), mock(CodeGenerationService.class)
        );
        AutomationApprovalRequest request = new AutomationApprovalRequest();
        request.setStatus("SUCCESS");

        service.approve(8L, request);

        assertEquals("SUCCESS", testCasesStage.getStatus());
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
                mock(UserMemoryService.class), mock(BadCaseService.class), mock(MemoryProjectService.class), mock(ModelGateway.class), mock(CodeGenerationService.class)
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
                mock(UserMemoryService.class), mock(BadCaseService.class), mock(MemoryProjectService.class), mock(ModelGateway.class), mock(CodeGenerationService.class)
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
    void projectDirectoryTreeOnlyReturnsGeneratedOutputRoots() {
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
                mock(UserMemoryService.class), mock(BadCaseService.class), mock(MemoryProjectService.class), mock(ModelGateway.class), mock(CodeGenerationService.class)
        );

        Map<String, Object> tree = service.getProjectDirectoryTree();

        assertEquals(true, tree.get("root"));
        String flattened = tree.toString();
        assertEquals(true, flattened.contains("front/src/generated"));
        assertEquals(true, flattened.contains("backend/src/main/java/com/aipal/generated"));
        assertEquals(false, flattened.contains("node_modules"));
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
                mock(UserMemoryService.class), mock(BadCaseService.class), mock(MemoryProjectService.class), mock(ModelGateway.class), mock(CodeGenerationService.class)
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
    void codeGenerationSnapshotKeepsOptionalIdsNullable() throws Exception {
        AutomationPipelineMapper pipelineMapper = mock(AutomationPipelineMapper.class);
        AutomationPipeline base = pipeline(1L, "code_generation", "RUNNING");
        base.setProjectKey("delivery-project");
        base.setProjectName("Delivery Project");
        base.setRequirementTitle("Delivery request");
        base.setGenerateFrontend(1);
        base.setGenerateBackend(1);
        when(pipelineMapper.selectById(1L)).thenReturn(base);

        AutomationPipelineService service = new AutomationPipelineService(
                pipelineMapper,
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
                mock(UserMemoryService.class), mock(BadCaseService.class), mock(MemoryProjectService.class), mock(ModelGateway.class), mock(CodeGenerationService.class)
        );
        AutomationGenerationJob job = generationJob(3L);
        job.setPipelineId(1L);
        job.setContextSnapshot("""
                {
                  "projectKey": "delivery-project",
                  "projectName": "Delivery Project",
                  "requirementTitle": "Delivery request",
                  "skillId": null,
                  "deployProfileId": null,
                  "codeQualityStandardId": null
                }
                """);

        Method method = AutomationPipelineService.class.getDeclaredMethod("pipelineFromCodeSnapshot", AutomationGenerationJob.class);
        method.setAccessible(true);
        AutomationPipeline pipeline = (AutomationPipeline) method.invoke(service, job);

        assertNull(pipeline.getSkillId());
        assertNull(pipeline.getDeployProfileId());
        assertNull(pipeline.getCodeQualityStandardId());
        assertNull(pipeline.getAutoDeployEnabled());
        assertNull(pipeline.getCodeQualityEnabled());
    }

    @Test
    void approvalDocumentMasksAbsoluteArtifactPath() {
        AutomationApprovalMapper approvalMapper = mock(AutomationApprovalMapper.class);
        AutomationStageRunMapper stageRunMapper = mock(AutomationStageRunMapper.class);
        AiOutputGovernanceService governanceService = mock(AiOutputGovernanceService.class);
        AutomationApproval approval = new AutomationApproval();
        approval.setId(9L);
        approval.setPipelineId(1L);
        approval.setStageRunId(2L);
        AutomationStageRun stage = stage(2L, 1L, "requirement_analysis", 1, "WAITING_APPROVAL");
        stage.setArtifactPath(Paths.get("").toAbsolutePath().resolve("marketDoc/generated-prd/pipeline-1-prd.md").toString());
        stage.setArtifactContent("prd content");
        when(approvalMapper.selectById(9L)).thenReturn(approval);
        when(stageRunMapper.selectById(2L)).thenReturn(stage);

        AutomationPipelineService service = new AutomationPipelineService(
                mock(AutomationPipelineMapper.class),
                stageRunMapper,
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
                governanceService,
                mock(UserMemoryService.class), mock(BadCaseService.class), mock(MemoryProjectService.class), mock(ModelGateway.class), mock(CodeGenerationService.class)
        );

        Map<String, Object> document = service.getApprovalDocument(9L);

        assertEquals("marketDoc/generated-prd/pipeline-1-prd.md", document.get("artifactPath"));
        assertEquals("prd content", document.get("content"));
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
                mock(UserMemoryService.class), mock(BadCaseService.class), mock(MemoryProjectService.class), mock(ModelGateway.class), mock(CodeGenerationService.class)
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
