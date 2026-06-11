package com.aipal.service;

import com.aipal.dto.AutomationApprovalRequest;
import com.aipal.dto.AutomationCodeFeedbackRequest;
import com.aipal.dto.AutomationPipelineDetail;
import com.aipal.dto.AutomationPipelineRequest;
import com.aipal.entity.AiModel;
import com.aipal.entity.AutomationApproval;
import com.aipal.entity.AutomationCodeRequirementFeedback;
import com.aipal.entity.AutomationGenerationJob;
import com.aipal.entity.AutomationGeneratedCodeBatch;
import com.aipal.entity.AutomationGeneratedCodeFile;
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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class AutomationPipelineService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_QUEUED = "QUEUED";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_WAITING_APPROVAL = "WAITING_APPROVAL";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_BLOCKED = "BLOCKED";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STAGE_REQUIREMENT_ANALYSIS = "requirement_analysis";
    private static final String STAGE_CODE_GENERATION = "code_generation";
    private static final String STAGE_CODE_QUALITY_EVALUATION = "code_quality_evaluation";
    private static final int MAX_CODE_FILE_COUNT = 40;
    private static final int MAX_CODE_FILE_CHARS = 120_000;
    private static final int MAX_CODE_PREVIEW_CHARS = 200_000;
    private static final int MINIMAX_OUTPUT_MAX_TOKENS = 8192;
    private static final String DEFAULT_TEMPLATE_FILE = "default-code-template.md";
    private static final String DEFAULT_PRD_TEMPLATE_FILE = "default-prd-template.md";
    private static final String DEFAULT_FRONTEND_OUTPUT_PATH = "front/src/generated";
    private static final String DEFAULT_BACKEND_OUTPUT_PATH = "backend/src/main/java/com/aipal/generated";
    private static final String JOB_TYPE_PRD = "PRD";
    private static final String JOB_TYPE_CODE = "CODE";
    private static final String FEEDBACK_SOURCE_AI = "AI";
    private static final String FEEDBACK_SOURCE_MANUAL = "MANUAL";
    private static final String FEEDBACK_PASSED = "PASSED";
    private static final String FEEDBACK_PARTIAL = "PARTIAL";
    private static final String FEEDBACK_FAILED = "FAILED";
    private static final String FEEDBACK_ERROR = "ERROR";
    private static final int MAX_RUNNING_GENERATION_JOBS = 2;
    private static final int MAX_DIRECTORY_TREE_DEPTH = 8;
    private static final Set<String> EXCLUDED_DIRECTORY_NAMES = Set.of(
            ".git", ".idea", ".claude", "node_modules", "target", "dist", "build", ".cache", ".vite"
    );
    private static final Set<String> EXCLUDED_DIRECTORY_PATHS = Set.of(
            "marketDoc/generated-code", "marketDoc/generated-prd"
    );

    private final AutomationPipelineMapper pipelineMapper;
    private final AutomationStageRunMapper stageRunMapper;
    private final AutomationApprovalMapper approvalMapper;
    private final AutomationGenerationJobMapper generationJobMapper;
    private final AutomationGeneratedCodeBatchMapper generatedCodeBatchMapper;
    private final AutomationGeneratedCodeFileMapper generatedCodeFileMapper;
    private final AutomationCodeRequirementFeedbackMapper codeRequirementFeedbackMapper;
    private final AiModelMapper modelMapper;
    private final SkillService skillService;
    private final AutomationBuildTestExecutionService buildTestExecutionService;
    private final AutomationDeployProfileService deployProfileService;
    private final AutomationDeploymentExecutionService deploymentExecutionService;
    private final CodeQualityService codeQualityService;
    private final AiOutputGovernanceService outputGovernanceService;
    private final UserMemoryService userMemoryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Page<AutomationPipeline> listPipelines(int pageNum, int pageSize, String status) {
        LambdaQueryWrapper<AutomationPipeline> wrapper = new LambdaQueryWrapper<AutomationPipeline>()
                .eq(status != null && !status.isBlank(), AutomationPipeline::getStatus, status)
                .orderByDesc(AutomationPipeline::getCreateTime);
        return pipelineMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Transactional
    public AutomationPipeline createPipeline(AutomationPipelineRequest request) {
        validateCreateRequest(request);
        LocalDateTime now = LocalDateTime.now();
        AutomationPipeline pipeline = new AutomationPipeline();
        pipeline.setPipelineCode("AUTO_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        pipeline.setProductLine(request.getProductLine().trim());
        pipeline.setProjectName(request.getProjectName().trim());
        pipeline.setRequirementTitle(request.getRequirementTitle().trim());
        pipeline.setRequirementSummary(request.getRequirementSummary());
        pipeline.setOwnerRole("project_manager");
        pipeline.setInitiator(request.getInitiator());
        pipeline.setInitiatorUserId(request.getInitiatorUserId());
        pipeline.setInitiatorUsername(request.getInitiatorUsername());
        pipeline.setTemplateFile(resolvePrdTemplateFile(request.getTemplateFile()));
        pipeline.setProjectMode(isBlank(request.getProjectMode()) ? "scratch" : request.getProjectMode().trim());
        pipeline.setCodeLevel(isBlank(request.getCodeLevel()) ? "module" : request.getCodeLevel().trim());
        pipeline.setGenerateFrontend(Boolean.FALSE.equals(request.getGenerateFrontend()) ? 0 : 1);
        pipeline.setGenerateBackend(Boolean.FALSE.equals(request.getGenerateBackend()) ? 0 : 1);
        pipeline.setFrontendOutputPath(normalizeGenerateRoot(request.getFrontendOutputPath(), DEFAULT_FRONTEND_OUTPUT_PATH));
        pipeline.setBackendOutputPath(normalizeGenerateRoot(request.getBackendOutputPath(), DEFAULT_BACKEND_OUTPUT_PATH));
        String skillSnapshot = request.getSkillId() == null ? null : skillService.requireEnabledSkillSnapshot(request.getSkillId());
        pipeline.setSkillId(request.getSkillId());
        pipeline.setSkillSnapshot(skillSnapshot);
        boolean autoDeployEnabled = Boolean.TRUE.equals(request.getAutoDeployEnabled());
        String deployProfileSnapshot = autoDeployEnabled ? deployProfileService.requireEnabledSnapshot(request.getDeployProfileId()) : null;
        pipeline.setAutoDeployEnabled(autoDeployEnabled ? 1 : 0);
        pipeline.setDeployProfileId(autoDeployEnabled ? request.getDeployProfileId() : null);
        pipeline.setDeployProfileSnapshot(deployProfileSnapshot);
        boolean codeQualityEnabled = Boolean.TRUE.equals(request.getCodeQualityEnabled());
        CodeQualityService.StandardSnapshot qualitySnapshot = codeQualityEnabled
                ? codeQualityService.requireEnabledStandardSnapshot(request.getCodeQualityStandardId())
                : null;
        pipeline.setCodeQualityEnabled(codeQualityEnabled ? 1 : 0);
        pipeline.setCodeQualityStandardId(codeQualityEnabled ? qualitySnapshot.standardId() : null);
        pipeline.setCodeQualityStandardSnapshot(codeQualityEnabled ? qualitySnapshot.standardSnapshot() : null);
        pipeline.setCodeQualityGateSnapshot(codeQualityEnabled ? qualitySnapshot.gateSnapshot() : null);
        pipeline.setQualityModelCode(codeQualityEnabled ? resolveQualityModelCode(request) : null);
        List<StageDefinition> definitions = stageDefinitions(codeQualityEnabled);
        pipeline.setStatus(STATUS_RUNNING);
        pipeline.setCurrentStage(definitions.get(0).key());
        pipeline.setTotalStages(definitions.size());
        pipeline.setPassedStages(0);
        pipeline.setFailedStages(0);
        pipeline.setApprovalRequired(1);
        pipeline.setCreateTime(now);
        pipeline.setUpdateTime(now);
        pipeline.setIsDeleted(0);
        pipelineMapper.insert(pipeline);
        request.setSkillSnapshot(skillSnapshot);
        request.setAutoDeployEnabled(autoDeployEnabled);
        request.setDeployProfileSnapshot(deployProfileSnapshot);
        request.setCodeQualityEnabled(codeQualityEnabled);
        request.setCodeQualityStandardId(pipeline.getCodeQualityStandardId());
        request.setCodeQualityStandardSnapshot(pipeline.getCodeQualityStandardSnapshot());
        request.setCodeQualityGateSnapshot(pipeline.getCodeQualityGateSnapshot());
        request.setQualityModelCode(pipeline.getQualityModelCode());

        AutomationStageRun firstStage = null;
        for (StageDefinition definition : definitions) {
            AutomationStageRun stage = new AutomationStageRun();
            stage.setPipelineId(pipeline.getId());
            stage.setStageKey(definition.key());
            stage.setStageName(definition.name());
            stage.setStageOrder(definition.order());
            stage.setExecutorType("AI");
            stage.setAiModelCode(STAGE_CODE_QUALITY_EVALUATION.equals(definition.key())
                    ? pipeline.getQualityModelCode()
                    : resolveModelCode(request));
            stage.setStatus(definition.order() == 1 ? STATUS_QUEUED : STATUS_PENDING);
            boolean autoDeployStage = autoDeployEnabled && deploymentExecutionService.isDeploymentStage(definition.key());
            stage.setExecutorType(autoDeployStage ? "DEPLOY" : "AI");
            stage.setRequiresApproval(autoDeployStage || STAGE_CODE_QUALITY_EVALUATION.equals(definition.key()) ? 0 : 1);
            stage.setInputSummary(inputForStage(request, definition));
            if (definition.order() == 1) {
                stage.setOutputSummary("PRD 生成任务已进入队列。");
            }
            stage.setCreateTime(now);
            stage.setUpdateTime(now);
            stageRunMapper.insert(stage);
            if (definition.order() == 1) {
                firstStage = stage;
            }
        }

        if (firstStage != null) {
            enqueuePrdGenerationJob(pipeline, firstStage, request);
        }
        return pipeline;
    }

    public AutomationPipelineDetail getDetail(Long pipelineId) {
        AutomationPipelineDetail detail = new AutomationPipelineDetail();
        detail.setPipeline(requirePipeline(pipelineId));
        List<AutomationStageRun> stages = listStages(pipelineId);
        stages.forEach(stage -> stage.setArtifactContent(null));
        detail.setStages(stages);
        detail.setApprovals(approvalMapper.selectList(
                new LambdaQueryWrapper<AutomationApproval>()
                        .eq(AutomationApproval::getPipelineId, pipelineId)
                        .orderByDesc(AutomationApproval::getCreateTime)
        ));
        return detail;
    }

    public Map<String, Object> getApprovalDocument(Long approvalId) {
        AutomationApproval approval = approvalMapper.selectById(approvalId);
        if (approval == null) {
            throw new IllegalArgumentException("Approval does not exist: " + approvalId);
        }
        AutomationStageRun stage = requireStage(approval.getStageRunId());
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("approval", approval);
        document.put("stage", stage);
        document.put("artifactPath", stage.getArtifactPath() == null ? "" : stage.getArtifactPath());
        document.put("content", stage.getArtifactContent() == null ? "" : stage.getArtifactContent());
        document.put("governanceRecords", outputGovernanceService.listStageRecords(stage.getPipelineId(), stage.getId()));
        document.put("governanceBlocked", outputGovernanceService.hasBlockingRecords(stage.getPipelineId(), stage.getId()));
        return document;
    }

    @Transactional
    public AutomationStageRun regeneratePrd(Long pipelineId) {
        AutomationPipeline pipeline = requirePipeline(pipelineId);
        AutomationStageRun stage = stageRunMapper.selectOne(
                new LambdaQueryWrapper<AutomationStageRun>()
                        .eq(AutomationStageRun::getPipelineId, pipelineId)
                        .eq(AutomationStageRun::getStageKey, STAGE_REQUIREMENT_ANALYSIS)
                        .last("LIMIT 1")
        );
        if (stage == null) {
            throw new IllegalArgumentException("Requirement analysis stage does not exist: " + pipelineId);
        }

        AutomationPipelineRequest request = new AutomationPipelineRequest();
        request.setProductLine(pipeline.getProductLine());
        request.setProjectName(pipeline.getProjectName());
        request.setRequirementTitle(pipeline.getRequirementTitle());
        request.setRequirementSummary(pipeline.getRequirementSummary());
        request.setInitiator(pipeline.getInitiator());
        request.setInitiatorUserId(pipeline.getInitiatorUserId());
        request.setInitiatorUsername(pipeline.getInitiatorUsername());
        request.setTemplateFile(pipeline.getTemplateFile());
        request.setProjectMode(pipeline.getProjectMode());
        request.setCodeLevel(pipeline.getCodeLevel());
        request.setGenerateFrontend(pipeline.getGenerateFrontend() == null || pipeline.getGenerateFrontend() == 1);
        request.setGenerateBackend(pipeline.getGenerateBackend() == null || pipeline.getGenerateBackend() == 1);
        request.setFrontendOutputPath(pipeline.getFrontendOutputPath());
        request.setBackendOutputPath(pipeline.getBackendOutputPath());
        request.setSkillId(pipeline.getSkillId());
        request.setSkillSnapshot(pipeline.getSkillSnapshot());
        request.setAutoDeployEnabled(pipeline.getAutoDeployEnabled() != null && pipeline.getAutoDeployEnabled() == 1);
        request.setDeployProfileId(pipeline.getDeployProfileId());
        request.setDeployProfileSnapshot(pipeline.getDeployProfileSnapshot());
        request.setCodeQualityEnabled(pipeline.getCodeQualityEnabled() != null && pipeline.getCodeQualityEnabled() == 1);
        request.setCodeQualityStandardId(pipeline.getCodeQualityStandardId());
        request.setCodeQualityStandardSnapshot(pipeline.getCodeQualityStandardSnapshot());
        request.setCodeQualityGateSnapshot(pipeline.getCodeQualityGateSnapshot());
        AiModel model = modelMapper.selectOne(
                new LambdaQueryWrapper<AiModel>()
                        .eq(stage.getAiModelCode() != null, AiModel::getModelCode, stage.getAiModelCode())
                        .last("LIMIT 1")
        );
        if (model != null) {
            request.setModelId(model.getId());
            request.setAiModelCode(model.getModelCode());
        } else {
            request.setAiModelCode(stage.getAiModelCode());
        }

        approvalMapper.selectList(
                new LambdaQueryWrapper<AutomationApproval>()
                        .eq(AutomationApproval::getPipelineId, pipelineId)
                        .eq(AutomationApproval::getStageRunId, stage.getId())
                        .eq(AutomationApproval::getStatus, STATUS_PENDING)
        ).forEach(approval -> {
            approval.setStatus(STATUS_REJECTED);
            approval.setComment("Superseded by PRD regeneration");
            approval.setReviewedTime(LocalDateTime.now());
            approval.setUpdateTime(LocalDateTime.now());
            approvalMapper.updateById(approval);
        });

        pipeline.setStatus(STATUS_RUNNING);
        pipeline.setFailedStages(0);
        pipeline.setCurrentStage(STAGE_REQUIREMENT_ANALYSIS);
        pipeline.setUpdateTime(LocalDateTime.now());
        pipelineMapper.updateById(pipeline);
        stage.setStatus(STATUS_QUEUED);
        stage.setStartTime(null);
        stage.setEndTime(null);
        stage.setDurationMs(null);
        stage.setErrorMessage(null);
        stage.setOutputSummary("PRD 重新生成任务已进入队列。");
        stage.setUpdateTime(LocalDateTime.now());
        stageRunMapper.updateById(stage);
        enqueuePrdGenerationJob(pipeline, stage, request);
        return stage;
    }

    @Transactional
    public AutomationStageRun runStage(Long stageId) {
        AutomationStageRun stage = requireStage(stageId);
        AutomationPipeline pipeline = requirePipeline(stage.getPipelineId());
        validateStageCanRun(pipeline, stage);
        if (STAGE_REQUIREMENT_ANALYSIS.equals(stage.getStageKey())) {
            throw new IllegalStateException("Requirement analysis runs automatically when a pipeline is created");
        }
        if (STAGE_CODE_GENERATION.equals(stage.getStageKey())) {
            return startCodeGeneration(stage.getPipelineId(), STATUS_REJECTED.equals(stage.getStatus()));
        }
        if (STAGE_CODE_QUALITY_EVALUATION.equals(stage.getStageKey())) {
            return runCodeQualityEvaluation(pipeline);
        }
        if ("build_compile".equals(stage.getStageKey())) {
            AutomationStageRun result = buildTestExecutionService.executeBuild(pipeline, stage);
            refreshPipelineProgress(pipeline.getId());
            if (STATUS_SUCCESS.equals(result.getStatus())) {
                runBuildAndTestStages(pipeline.getId(), "build_compile");
            }
            return result;
        }
        if ("test_execution".equals(stage.getStageKey())) {
            AutomationStageRun result = buildTestExecutionService.executeTest(pipeline, stage);
            refreshPipelineProgress(pipeline.getId());
            if (STATUS_SUCCESS.equals(result.getStatus()) && isAutoDeployEnabled(requirePipeline(pipeline.getId()))) {
                runAutoDeployStages(pipeline.getId(), "test_execution");
            }
            return result;
        }
        if (isAutoDeployEnabled(pipeline) && deploymentExecutionService.isDeploymentStage(stage.getStageKey())) {
            AutomationStageRun result = deploymentExecutionService.executeStage(pipeline, stage);
            refreshPipelineProgress(pipeline.getId());
            if (STATUS_SUCCESS.equals(result.getStatus())) {
                runAutoDeployStages(pipeline.getId(), stage.getStageKey());
            }
            return result;
        }

        LocalDateTime start = LocalDateTime.now();
        pipeline.setStatus(STATUS_RUNNING);
        pipeline.setCurrentStage(stage.getStageKey());
        pipeline.setUpdateTime(start);
        pipelineMapper.updateById(pipeline);

        stage.setStatus(STATUS_RUNNING);
        stage.setStartTime(start);
        stage.setEndTime(null);
        stage.setDurationMs(null);
        stage.setErrorMessage(null);
        stage.setOutputSummary(buildAiOutput(stage));
        stage.setEndTime(LocalDateTime.now());
        stage.setDurationMs((int) Duration.between(start, stage.getEndTime()).toMillis());
        stage.setStatus(stage.getRequiresApproval() != null && stage.getRequiresApproval() == 1
                ? STATUS_WAITING_APPROVAL : STATUS_SUCCESS);
        stage.setUpdateTime(LocalDateTime.now());
        stageRunMapper.updateById(stage);

        if (STATUS_WAITING_APPROVAL.equals(stage.getStatus())) {
            createApproval(pipeline, stage);
        } else {
            refreshPipelineProgress(pipeline.getId());
        }
        return stage;
    }

    @Transactional
    public AutomationApproval approve(Long approvalId, AutomationApprovalRequest request) {
        AutomationApprovalRequest review = request == null ? new AutomationApprovalRequest() : request;
        AutomationApproval approval = approvalMapper.selectById(approvalId);
        if (approval == null) {
            throw new IllegalArgumentException("Approval does not exist: " + approvalId);
        }
        if (!STATUS_PENDING.equals(approval.getStatus())) {
            throw new IllegalStateException("Only pending approvals can be reviewed");
        }
        String nextStatus = review.getStatus() == null ? STATUS_SUCCESS : review.getStatus().trim().toUpperCase();
        if (!STATUS_SUCCESS.equals(nextStatus) && !STATUS_REJECTED.equals(nextStatus)) {
            throw new IllegalArgumentException("Approval status must be SUCCESS or REJECTED");
        }

        LocalDateTime now = LocalDateTime.now();
        if (review.getArtifactContent() != null) {
            AutomationStageRun editableStage = requireStage(approval.getStageRunId());
            saveArtifact(editableStage, review.getArtifactContent());
        }
        AutomationStageRun stage = requireStage(approval.getStageRunId());
        if (STATUS_SUCCESS.equals(nextStatus)
                && outputGovernanceService.hasBlockingRecords(stage.getPipelineId(), stage.getId())) {
            throw new IllegalStateException("当前阶段存在被治理策略阻塞的 AI 产出，请修复后重新生成或重新评估后再审批通过");
        }
        if (STATUS_SUCCESS.equals(nextStatus) && STAGE_CODE_GENERATION.equals(stage.getStageKey())) {
            AutomationGeneratedCodeBatch latestBatch = latestCodeBatch(stage.getPipelineId());
            if (latestBatch != null && isDoubleFailed(latestBatch.getId())) {
                throw new IllegalStateException("AI 和人工均判定当前生成代码不符合需求，请根据不合格原因重新生成代码。");
            }
        }
        approval.setStatus(nextStatus);
        approval.setComment(review.getComment());
        approval.setReviewedBy(review.getReviewedBy());
        approval.setReviewedTime(now);
        approval.setUpdateTime(now);
        approvalMapper.updateById(approval);

        stage.setStatus(STATUS_SUCCESS.equals(nextStatus) ? STATUS_SUCCESS : STATUS_REJECTED);
        stage.setUpdateTime(now);
        stageRunMapper.updateById(stage);
        outputGovernanceService.markStageReviewed(stage.getPipelineId(), stage.getId(), STATUS_SUCCESS.equals(nextStatus));
        refreshPipelineProgress(stage.getPipelineId());
        if (STATUS_SUCCESS.equals(nextStatus) && STAGE_REQUIREMENT_ANALYSIS.equals(stage.getStageKey())) {
            startCodeGeneration(stage.getPipelineId(), true);
        } else if (STATUS_SUCCESS.equals(nextStatus) && STAGE_CODE_GENERATION.equals(stage.getStageKey())) {
            runBuildAndTestStages(stage.getPipelineId(), null);
        }
        return approval;
    }

    public Page<AutomationApproval> listApprovals(int pageNum, int pageSize, String status) {
        LambdaQueryWrapper<AutomationApproval> wrapper = new LambdaQueryWrapper<AutomationApproval>()
                .eq(status != null && !status.isBlank(), AutomationApproval::getStatus, status)
                .orderByDesc(AutomationApproval::getCreateTime);
        return approvalMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Transactional
    public AutomationStageRun regenerateCode(Long pipelineId) {
        return startCodeGeneration(pipelineId, true);
    }

    public Map<String, Object> getCodeTree(Long pipelineId) {
        AutomationStageRun stage = findStage(pipelineId, STAGE_CODE_GENERATION);
        if (stage == null) {
            throw new IllegalArgumentException("Code generation stage does not exist: " + pipelineId);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pipelineId", pipelineId);
        result.put("stageId", stage.getId());
        result.put("status", stage.getStatus());
        result.put("artifactPath", stage.getArtifactPath());
        result.put("files", readCodeManifest(stage));
        result.put("errorMessage", stage.getErrorMessage());
        AutomationGeneratedCodeBatch batch = latestCodeBatch(pipelineId);
        result.put("batch", batch);
        result.put("batchId", batch == null ? null : batch.getId());
        List<AutomationCodeRequirementFeedback> feedbacks = batch == null
                ? List.of()
                : listCodeFeedback(pipelineId, batch.getId());
        result.put("feedbacks", feedbacks);
        result.put("doubleFailed", batch != null && isDoubleFailed(batch.getId()));
        result.put("failureSummary", batch == null ? null : doubleFailedSummary(batch.getId()));
        return result;
    }

    public Map<String, Object> getCodeFile(Long pipelineId, String relativePath) {
        AutomationStageRun stage = findStage(pipelineId, STAGE_CODE_GENERATION);
        if (stage == null || stage.getArtifactPath() == null || stage.getArtifactPath().isBlank()) {
            throw new IllegalArgumentException("Generated code artifact does not exist: " + pipelineId);
        }
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
        try {
            Path root = Paths.get(stage.getArtifactPath()).toAbsolutePath().normalize();
            Path target = root.resolve(relativePath).normalize();
            if (!target.startsWith(root) || !Files.isRegularFile(target)) {
                throw new IllegalArgumentException("Invalid code file path: " + relativePath);
            }
            String content = Files.readString(target);
            boolean truncated = content.length() > MAX_CODE_PREVIEW_CHARS;
            if (truncated) {
                content = content.substring(0, MAX_CODE_PREVIEW_CHARS);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("path", normalizeRelativePath(relativePath));
            result.put("size", Files.size(target));
            result.put("truncated", truncated);
            result.put("content", content);
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read generated code file: " + e.getMessage(), e);
        }
    }

    public List<AutomationCodeRequirementFeedback> listCodeFeedback(Long pipelineId, Long batchId) {
        AutomationGeneratedCodeBatch batch = batchId == null ? latestCodeBatch(pipelineId) : requireCodeBatch(pipelineId, batchId);
        if (batch == null) {
            return List.of();
        }
        return codeRequirementFeedbackMapper.selectList(
                new LambdaQueryWrapper<AutomationCodeRequirementFeedback>()
                        .eq(AutomationCodeRequirementFeedback::getPipelineId, pipelineId)
                        .eq(AutomationCodeRequirementFeedback::getBatchId, batch.getId())
                        .orderByAsc(AutomationCodeRequirementFeedback::getCreateTime)
                        .orderByAsc(AutomationCodeRequirementFeedback::getId)
        );
    }

    @Transactional
    public Map<String, Object> submitCodeFeedback(Long pipelineId, AutomationCodeFeedbackRequest request) {
        AutomationCodeFeedbackRequest feedbackRequest = request == null ? new AutomationCodeFeedbackRequest() : request;
        AutomationGeneratedCodeBatch batch = requireCodeBatch(pipelineId, feedbackRequest.getBatchId());
        String status = normalizeFeedbackStatus(feedbackRequest.getAlignmentStatus());
        if (FEEDBACK_FAILED.equals(status) && isBlank(feedbackRequest.getFailureReason())) {
            throw new IllegalArgumentException("不合格原因不能为空");
        }
        AutomationCodeRequirementFeedback feedback = new AutomationCodeRequirementFeedback();
        feedback.setBatchId(batch.getId());
        feedback.setPipelineId(batch.getPipelineId());
        feedback.setStageRunId(batch.getStageRunId());
        feedback.setGenerationJobId(batch.getGenerationJobId());
        feedback.setFeedbackSource(FEEDBACK_SOURCE_MANUAL);
        feedback.setAlignmentStatus(status);
        feedback.setAlignmentScore(feedbackRequest.getAlignmentScore());
        feedback.setSummary(feedbackRequest.getSummary());
        feedback.setFailureReason(FEEDBACK_FAILED.equals(status)
                ? defaultFailureReason(feedbackRequest.getFailureReason())
                : feedbackRequest.getFailureReason());
        feedback.setDetailJson(feedbackRequest.getDetailJson());
        feedback.setReviewedBy(feedbackRequest.getReviewedBy());
        feedback.setCreateTime(LocalDateTime.now());
        codeRequirementFeedbackMapper.insert(feedback);

        boolean doubleFailed = isDoubleFailed(batch.getId());
        if (doubleFailed) {
            blockCodeGenerationForDoubleFailure(batch);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("feedback", feedback);
        result.put("feedbacks", listCodeFeedback(pipelineId, batch.getId()));
        result.put("doubleFailed", doubleFailed);
        result.put("failureSummary", doubleFailedSummary(batch.getId()));
        return result;
    }

    public List<Map<String, Object>> listCodeTemplates() {
        try {
            ensureDefaultCodeTemplate();
            return listMarkdownTemplates(codeTemplateRoot());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list code templates: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> listPrdTemplates() {
        try {
            ensureDefaultPrdTemplate();
            return listMarkdownTemplates(prdTemplateRoot());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list PRD templates: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> getCodeTemplate(String fileName) {
        try {
            ensureDefaultCodeTemplate();
            Path path = resolveTemplatePath(fileName);
            if (!Files.isRegularFile(path)) {
                throw new IllegalArgumentException("Template does not exist: " + fileName);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fileName", path.getFileName().toString());
            result.put("content", Files.readString(path));
            result.put("size", Files.size(path));
            result.put("updateTime", Files.getLastModifiedTime(path).toString());
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read code template: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> saveCodeTemplate(String fileName, String content) {
        try {
            Path path = resolveTemplatePath(fileName);
            Files.createDirectories(path.getParent());
            Files.writeString(path, content == null ? "" : content);
            return getCodeTemplate(path.getFileName().toString());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save code template: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> getPrdTemplate(String fileName) {
        try {
            ensureDefaultPrdTemplate();
            Path path = resolvePrdTemplatePath(fileName);
            if (!Files.isRegularFile(path)) {
                throw new IllegalArgumentException("PRD template does not exist: " + fileName);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fileName", path.getFileName().toString());
            result.put("content", Files.readString(path));
            result.put("size", Files.size(path));
            result.put("updateTime", Files.getLastModifiedTime(path).toString());
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read PRD template: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> savePrdTemplate(String fileName, String content) {
        try {
            Path path = resolvePrdTemplatePath(fileName);
            Files.createDirectories(path.getParent());
            Files.writeString(path, content == null ? "" : content);
            return getPrdTemplate(path.getFileName().toString());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save PRD template: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> getProjectDirectoryTree() {
        Path root = Paths.get("").toAbsolutePath().normalize();
        Map<String, Object> node = directoryNode(root, root.getFileName() == null ? root.toString() : root.getFileName().toString(), "", 0);
        node.put("root", true);
        return node;
    }

    public Map<String, Object> getReportSummary() {
        List<AutomationPipeline> pipelines = pipelineMapper.selectList(null);
        List<AutomationStageRun> stages = stageRunMapper.selectList(null);
        long total = pipelines.size();
        long running = pipelines.stream().filter(p -> STATUS_RUNNING.equals(p.getStatus())).count();
        long completed = pipelines.stream().filter(p -> STATUS_COMPLETED.equals(p.getStatus())).count();
        long blocked = pipelines.stream().filter(p -> STATUS_BLOCKED.equals(p.getStatus())).count();
        long waitingApprovals = approvalMapper.selectCount(
                new LambdaQueryWrapper<AutomationApproval>().eq(AutomationApproval::getStatus, STATUS_PENDING)
        );
        long successStages = stages.stream().filter(s -> STATUS_SUCCESS.equals(s.getStatus())).count();
        long terminalStages = stages.stream()
                .filter(s -> STATUS_SUCCESS.equals(s.getStatus()) || STATUS_REJECTED.equals(s.getStatus()))
                .count();

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalPipelines", total);
        summary.put("runningPipelines", running);
        summary.put("completedPipelines", completed);
        summary.put("blockedPipelines", blocked);
        summary.put("waitingApprovals", waitingApprovals);
        summary.put("stagePassRate", terminalStages == 0 ? 0 : round((double) successStages * 100 / terminalStages));
        summary.put("teamScale", Map.of("products", 20, "developers", 100, "testers", 10));
        return summary;
    }

    private void createApproval(AutomationPipeline pipeline, AutomationStageRun stage) {
        Long pendingCount = approvalMapper.selectCount(
                new LambdaQueryWrapper<AutomationApproval>()
                        .eq(AutomationApproval::getStageRunId, stage.getId())
                        .eq(AutomationApproval::getStatus, STATUS_PENDING)
        );
        boolean exists = pendingCount != null && pendingCount > 0;
        if (exists) {
            return;
        }
        AutomationApproval approval = new AutomationApproval();
        approval.setPipelineId(pipeline.getId());
        approval.setStageRunId(stage.getId());
        approval.setApprovalType(stage.getStageKey());
        approval.setReviewerRole(reviewerFor(stage.getStageKey()));
        approval.setStatus(STATUS_PENDING);
        approval.setCreateTime(LocalDateTime.now());
        approval.setUpdateTime(LocalDateTime.now());
        approvalMapper.insert(approval);
    }

    private void generatePrdAndRequestReview(AutomationPipeline pipeline, AutomationStageRun stage,
                                             AutomationPipelineRequest request, AutomationGenerationJob job) {
        LocalDateTime start = LocalDateTime.now();
        stage.setStatus(STATUS_RUNNING);
        stage.setStartTime(start);

        String prdContent = generatePrdContent(request, job);
        saveArtifact(stage, prdContent);
        stage.setOutputSummary("PRD 已生成：" + stage.getArtifactPath());
        stage.setEndTime(LocalDateTime.now());
        stage.setDurationMs((int) Duration.between(start, stage.getEndTime()).toMillis());
        stage.setStatus(STATUS_WAITING_APPROVAL);
        stage.setUpdateTime(LocalDateTime.now());
        stageRunMapper.updateById(stage);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("approvalRequired", true);
        metadata.put("contentLength", prdContent == null ? 0 : prdContent.length());
        metadata.put("requirementTitle", pipeline.getRequirementTitle());
        recordAutomationGovernance(pipeline, stage, job, "PRD", stage.getArtifactPath(),
                stage.getOutputSummary(), stage.getAiModelCode(), metadata);
        createApproval(pipeline, stage);
        appendPipelineMemory(pipeline, stage, job, "PRD", request.getRequirementSummary(), prdContent);
    }

    private AutomationGenerationJob enqueuePrdGenerationJob(AutomationPipeline pipeline, AutomationStageRun stage,
                                                            AutomationPipelineRequest request) {
        try {
            return enqueueGenerationJob(pipeline, stage, JOB_TYPE_PRD, resolveRequestUserId(request),
                    objectMapper.writeValueAsString(request));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to snapshot PRD generation context: " + e.getMessage(), e);
        }
    }

    private AutomationGenerationJob enqueueCodeGenerationJob(AutomationPipeline pipeline, AutomationStageRun stage) {
        try {
            AutomationStageRun prdStage = findStage(pipeline.getId(), STAGE_REQUIREMENT_ANALYSIS);
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("pipelineId", pipeline.getId());
            snapshot.put("stageRunId", stage.getId());
            snapshot.put("requirementTitle", pipeline.getRequirementTitle());
            snapshot.put("projectName", pipeline.getProjectName());
            snapshot.put("templateFile", DEFAULT_TEMPLATE_FILE);
            snapshot.put("templateContent", readTemplateContent(DEFAULT_TEMPLATE_FILE));
            snapshot.put("projectMode", pipeline.getProjectMode());
            snapshot.put("codeLevel", pipeline.getCodeLevel());
            snapshot.put("generateFrontend", pipeline.getGenerateFrontend());
            snapshot.put("generateBackend", pipeline.getGenerateBackend());
            snapshot.put("frontendOutputPath", pipeline.getFrontendOutputPath());
            snapshot.put("backendOutputPath", pipeline.getBackendOutputPath());
            snapshot.put("skillId", pipeline.getSkillId());
            snapshot.put("skillSnapshot", pipeline.getSkillSnapshot());
        snapshot.put("autoDeployEnabled", pipeline.getAutoDeployEnabled());
        snapshot.put("deployProfileId", pipeline.getDeployProfileId());
        snapshot.put("deployProfileSnapshot", pipeline.getDeployProfileSnapshot());
        snapshot.put("codeQualityEnabled", pipeline.getCodeQualityEnabled());
        snapshot.put("codeQualityStandardId", pipeline.getCodeQualityStandardId());
        snapshot.put("codeQualityStandardSnapshot", pipeline.getCodeQualityStandardSnapshot());
        snapshot.put("codeQualityGateSnapshot", pipeline.getCodeQualityGateSnapshot());
        snapshot.put("qualityModelCode", pipeline.getQualityModelCode());
        snapshot.put("prdContent", readStageArtifact(prdStage));
        snapshot.put("aiModelCode", stage.getAiModelCode());
            return enqueueGenerationJob(pipeline, stage, JOB_TYPE_CODE, resolvePipelineRequestUserId(pipeline),
                    objectMapper.writeValueAsString(snapshot));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to snapshot code generation context: " + e.getMessage(), e);
        }
    }

    private AutomationGenerationJob enqueueGenerationJob(AutomationPipeline pipeline, AutomationStageRun stage,
                                                         String jobType, String requestUserId, String contextSnapshot) {
        supersedeQueuedGenerationJobs(pipeline.getId(), stage.getId(), jobType);
        AutomationGenerationJob job = new AutomationGenerationJob();
        job.setPipelineId(pipeline.getId());
        job.setStageRunId(stage.getId());
        job.setJobType(jobType);
        job.setStatus(STATUS_QUEUED);
        job.setRequestUserId(requestUserId);
        job.setTraceId("GEN_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        job.setContextSnapshot(contextSnapshot);
        job.setCreateTime(LocalDateTime.now());
        job.setUpdateTime(LocalDateTime.now());
        generationJobMapper.insert(job);
        return job;
    }

    private String resolveRequestUserId(AutomationPipelineRequest request) {
        if (request.getInitiatorUserId() != null) {
            return String.valueOf(request.getInitiatorUserId());
        }
        if (request.getInitiatorUsername() != null && !request.getInitiatorUsername().isBlank()) {
            return request.getInitiatorUsername();
        }
        return request.getInitiator();
    }

    private String resolvePipelineRequestUserId(AutomationPipeline pipeline) {
        if (pipeline.getInitiatorUserId() != null) {
            return String.valueOf(pipeline.getInitiatorUserId());
        }
        if (pipeline.getInitiatorUsername() != null && !pipeline.getInitiatorUsername().isBlank()) {
            return pipeline.getInitiatorUsername();
        }
        return pipeline.getInitiator();
    }

    private String resolvePipelineUserKey(AutomationPipeline pipeline) {
        return userMemoryService.normalizeUserKey(null, pipeline.getInitiatorUserId(),
                pipeline.getInitiatorUsername() == null ? pipeline.getInitiator() : pipeline.getInitiatorUsername());
    }

    private void appendPipelineMemory(AutomationPipeline pipeline, AutomationStageRun stage, AutomationGenerationJob job,
                                      String memoryStage, String inputSummary, String outputSummary) {
        userMemoryService.appendPipelineMemory(
                resolvePipelineUserKey(pipeline),
                pipeline.getInitiatorUserId(),
                pipeline.getInitiatorUsername() == null ? pipeline.getInitiator() : pipeline.getInitiatorUsername(),
                pipeline.getId(),
                stage.getId(),
                memoryStage,
                inputSummary,
                outputSummary,
                job == null ? null : job.getInputTokens(),
                job == null ? null : job.getOutputTokens(),
                job == null ? null : job.getTotalTokens()
        );
    }

    private void supersedeQueuedGenerationJobs(Long pipelineId, Long stageRunId, String jobType) {
        AutomationGenerationJob update = new AutomationGenerationJob();
        update.setStatus(STATUS_BLOCKED);
        update.setErrorMessage("Superseded by a newer generation request");
        update.setUpdateTime(LocalDateTime.now());
        generationJobMapper.update(update,
                new LambdaUpdateWrapper<AutomationGenerationJob>()
                        .eq(AutomationGenerationJob::getPipelineId, pipelineId)
                        .eq(AutomationGenerationJob::getStageRunId, stageRunId)
                        .eq(AutomationGenerationJob::getJobType, jobType)
                        .eq(AutomationGenerationJob::getStatus, STATUS_QUEUED)
        );
    }

    private boolean isLatestGenerationJob(AutomationGenerationJob job) {
        AutomationGenerationJob latest = generationJobMapper.selectOne(
                new LambdaQueryWrapper<AutomationGenerationJob>()
                        .eq(AutomationGenerationJob::getPipelineId, job.getPipelineId())
                        .eq(AutomationGenerationJob::getStageRunId, job.getStageRunId())
                        .eq(AutomationGenerationJob::getJobType, job.getJobType())
                        .orderByDesc(AutomationGenerationJob::getCreateTime)
                        .orderByDesc(AutomationGenerationJob::getId)
                        .last("LIMIT 1")
        );
        return latest == null || job.getId() == null || job.getId().equals(latest.getId());
    }

    private AutomationStageRun startCodeGeneration(Long pipelineId, boolean force) {
        AutomationPipeline pipeline = requirePipeline(pipelineId);
        AutomationStageRun stage = findStage(pipelineId, STAGE_CODE_GENERATION);
        if (stage == null) {
            throw new IllegalArgumentException("Code generation stage does not exist: " + pipelineId);
        }
        if (!force && (STATUS_QUEUED.equals(stage.getStatus()) || STATUS_RUNNING.equals(stage.getStatus()) || STATUS_WAITING_APPROVAL.equals(stage.getStatus())
                || STATUS_SUCCESS.equals(stage.getStatus()))) {
            return stage;
        }

        LocalDateTime now = LocalDateTime.now();
        rejectPendingApprovals(pipelineId, stage.getId(), "Superseded by code regeneration");
        stage.setStatus(STATUS_QUEUED);
        stage.setStartTime(null);
        stage.setEndTime(null);
        stage.setDurationMs(null);
        stage.setErrorMessage(null);
        stage.setOutputSummary("代码生成任务已进入队列，完成后可查看前后端文件树。");
        stage.setArtifactPath(codeArtifactRoot(pipelineId).toAbsolutePath().toString());
        stage.setArtifactContent(null);
        stage.setUpdateTime(now);
        stageRunMapper.updateById(stage);

        AutomationStageRun qualityStage = findStage(pipelineId, STAGE_CODE_QUALITY_EVALUATION);
        if (qualityStage != null) {
            qualityStage.setStatus(STATUS_PENDING);
            qualityStage.setStartTime(null);
            qualityStage.setEndTime(null);
            qualityStage.setDurationMs(null);
            qualityStage.setErrorMessage(null);
            qualityStage.setOutputSummary("Waiting for generated code.");
            qualityStage.setArtifactPath(null);
            qualityStage.setArtifactContent(null);
            qualityStage.setUpdateTime(now);
            stageRunMapper.updateById(qualityStage);
        }

        pipeline.setStatus(STATUS_RUNNING);
        pipeline.setCurrentStage(STAGE_CODE_GENERATION);
        pipeline.setUpdateTime(now);
        pipelineMapper.updateById(pipeline);

        enqueueCodeGenerationJob(pipeline, stage);
        return stage;
    }

    @Scheduled(fixedDelay = 2000)
    public void dispatchGenerationJobs() {
        Long runningCount = generationJobMapper.selectCount(
                new LambdaQueryWrapper<AutomationGenerationJob>().eq(AutomationGenerationJob::getStatus, STATUS_RUNNING)
        );
        if (runningCount != null && runningCount >= MAX_RUNNING_GENERATION_JOBS) {
            return;
        }
        AutomationGenerationJob job = generationJobMapper.selectOne(
                new LambdaQueryWrapper<AutomationGenerationJob>()
                        .eq(AutomationGenerationJob::getStatus, STATUS_QUEUED)
                        .orderByAsc(AutomationGenerationJob::getCreateTime)
                        .last("LIMIT 1")
        );
        if (job == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        AutomationGenerationJob update = new AutomationGenerationJob();
        update.setStatus(STATUS_RUNNING);
        update.setStartTime(now);
        update.setUpdateTime(now);
        int claimed = generationJobMapper.update(update,
                new LambdaUpdateWrapper<AutomationGenerationJob>()
                        .eq(AutomationGenerationJob::getId, job.getId())
                        .eq(AutomationGenerationJob::getStatus, STATUS_QUEUED)
        );
        if (claimed == 1) {
            CompletableFuture.runAsync(() -> processGenerationJob(job.getId()));
        }
    }

    private void processGenerationJob(Long jobId) {
        AutomationGenerationJob job = generationJobMapper.selectById(jobId);
        if (job == null) {
            return;
        }
        LocalDateTime start = job.getStartTime() == null ? LocalDateTime.now() : job.getStartTime();
        try {
            if (JOB_TYPE_PRD.equals(job.getJobType())) {
                processPrdGenerationJob(job);
            } else if (JOB_TYPE_CODE.equals(job.getJobType())) {
                completeCodeGeneration(job);
            } else {
                throw new IllegalArgumentException("Unsupported generation job type: " + job.getJobType());
            }
            job.setStatus(STATUS_SUCCESS);
            job.setEndTime(LocalDateTime.now());
            job.setDurationMs((int) Duration.between(start, job.getEndTime()).toMillis());
            job.setErrorMessage(null);
            job.setUpdateTime(LocalDateTime.now());
            generationJobMapper.updateById(job);
        } catch (Exception e) {
            failGenerationJob(job, start, e);
        }
    }

    private void processPrdGenerationJob(AutomationGenerationJob job) throws IOException {
        if (!isLatestGenerationJob(job)) {
            return;
        }
        AutomationPipelineRequest request = objectMapper.readValue(job.getContextSnapshot(), AutomationPipelineRequest.class);
        AutomationPipeline pipeline = requirePipeline(job.getPipelineId());
        AutomationStageRun stage = requireStage(job.getStageRunId());
        generatePrdAndRequestReview(pipeline, stage, request, job);
        job.setArtifactPath(stage.getArtifactPath());
    }

    private void completeCodeGeneration(AutomationGenerationJob job) throws IOException {
        if (!isLatestGenerationJob(job)) {
            return;
        }
        Long pipelineId = job.getPipelineId();
        Long stageId = job.getStageRunId();
        LocalDateTime start = LocalDateTime.now();
        AutomationStageRun stage = requireStage(stageId);
        AutomationPipeline pipeline = pipelineFromCodeSnapshot(job);
        JsonNode snapshot = objectMapper.readTree(job.getContextSnapshot());
        String prdContent = snapshot.path("prdContent").asText("");
        String templateContent = snapshot.path("templateContent").asText("");
        String skillSnapshot = snapshot.path("skillSnapshot").asText("");
        List<GeneratedFile> files = generateCodeFiles(pipeline, stage, prdContent, templateContent, skillSnapshot, job);
        String manifest = writeGeneratedCode(pipelineId, job.getId(), files);

        stage = requireStage(stageId);
        stage.setArtifactPath(codeArtifactRoot(pipelineId, job.getId()).toAbsolutePath().toString());
        stage.setArtifactContent(manifest);
        boolean qualityEnabled = isCodeQualityEnabled(pipeline);
        stage.setOutputSummary(qualityEnabled
                ? "代码生成完成，共 " + files.size() + " 个文件，等待代码质量评估。"
                : "代码生成完成，共 " + files.size() + " 个文件，等待架构师审核。");
        stage.setStatus(qualityEnabled ? STATUS_SUCCESS : STATUS_WAITING_APPROVAL);
        stage.setEndTime(LocalDateTime.now());
        stage.setDurationMs((int) Duration.between(start, stage.getEndTime()).toMillis());
        stage.setErrorMessage(null);
        stage.setUpdateTime(LocalDateTime.now());
        stageRunMapper.updateById(stage);
        job.setArtifactPath(stage.getArtifactPath());
        AutomationGeneratedCodeBatch codeBatch = archiveGeneratedCode(pipeline, stage, job, files, manifest);
        recordAiRequirementFeedback(pipeline, stage, job, codeBatch, prdContent, files);
        AutomationPipeline latestPipeline = requirePipeline(pipelineId);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("fileCount", files.size());
        metadata.put("manifestLength", manifest == null ? 0 : manifest.length());
        metadata.put("codeQualityEnabled", qualityEnabled);
        metadata.put("projectMode", pipeline.getProjectMode());
        metadata.put("generatedCodeBatchId", codeBatch == null ? null : codeBatch.getId());
        recordAutomationGovernance(latestPipeline, stage, job, "CODE", stage.getArtifactPath(),
                stage.getOutputSummary(), stage.getAiModelCode(), metadata);
        if (codeBatch != null && isDoubleFailed(codeBatch.getId())) {
            appendPipelineMemory(latestPipeline, stage, job, "CODE", prdContent, manifest);
            return;
        }
        if (qualityEnabled) {
            runCodeQualityEvaluation(latestPipeline);
        } else {
            createApproval(latestPipeline, stage);
        }
        appendPipelineMemory(latestPipeline, stage, job, "CODE", prdContent, manifest);
    }

    private void failGenerationJob(AutomationGenerationJob job, LocalDateTime start, Exception e) {
        LocalDateTime now = LocalDateTime.now();
        job.setStatus(STATUS_BLOCKED);
        job.setErrorMessage(e.getMessage());
        job.setEndTime(now);
        job.setDurationMs((int) Duration.between(start, now).toMillis());
        job.setUpdateTime(now);
        generationJobMapper.updateById(job);

        AutomationStageRun stage = requireStage(job.getStageRunId());
        stage.setStatus(STATUS_BLOCKED);
        stage.setErrorMessage(e.getMessage());
        stage.setOutputSummary(("PRD".equals(job.getJobType()) ? "PRD" : "代码") + "生成失败：" + e.getMessage());
        stage.setEndTime(now);
        stage.setDurationMs((int) Duration.between(start, now).toMillis());
        stage.setUpdateTime(now);
        stageRunMapper.updateById(stage);

        AutomationPipeline pipeline = requirePipeline(job.getPipelineId());
        pipeline.setStatus(STATUS_BLOCKED);
        pipeline.setCurrentStage(stage.getStageKey());
        pipeline.setUpdateTime(now);
        pipelineMapper.updateById(pipeline);
    }

    private List<GeneratedFile> generateCodeFiles(AutomationPipeline pipeline, AutomationStageRun stage,
                                                  String prdContent, String templateContent, String skillSnapshot,
                                                  AutomationGenerationJob job) {
        AiModel model = modelMapper.selectOne(
                new LambdaQueryWrapper<AiModel>()
                        .eq(stage.getAiModelCode() != null, AiModel::getModelCode, stage.getAiModelCode())
                        .last("LIMIT 1")
        );
        if (model == null || model.getEndpoint() == null || model.getEndpoint().isBlank()
                || model.getApiKey() == null || model.getApiKey().isBlank()) {
            return fallbackCodeFiles(pipeline, prdContent, "Model is not configured or API key is missing");
        }

        try {
            String systemPrompt = "You are an enterprise full-stack engineer. Return pure JSON only, without Markdown fences.";
            String userPrompt = buildCodePrompt(pipeline, prdContent, templateContent, skillSnapshot);
            ModelCallResult result = callModel(model, systemPrompt, userPrompt);
            applyTokenUsage(job, result);
            String content = result.content();
            List<GeneratedFile> files = parseGeneratedCodeFiles(content, pipeline);
            return files.isEmpty() ? fallbackCodeFiles(pipeline, prdContent, "Model returned no parseable code files") : files;
        } catch (Exception e) {
            return fallbackCodeFiles(pipeline, prdContent, "Model call failed: " + e.getMessage());
        }
    }
    private ModelCallResult callModel(AiModel model, String systemPrompt, String userPrompt) throws IOException {
        String endpoint = model.getEndpoint().endsWith("/")
                ? model.getEndpoint() + "chat/completions"
                : model.getEndpoint() + "/chat/completions";
        Map<String, Object> body = Map.of(
                "model", model.getModelCode(),
                "temperature", model.getDefaultTemperature() == null ? BigDecimal.valueOf(0.3) : model.getDefaultTemperature(),
                "max_tokens", resolveMaxTokens(model),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );
        String response = RestClient.create()
                .post()
                .uri(endpoint)
                .header("Authorization", "Bearer " + model.getApiKey())
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(String.class);
        return extractModelCallResult(response, systemPrompt, userPrompt);
    }

    private String buildCodePrompt(AutomationPipeline pipeline, String prdContent, String templateContent, String skillSnapshot) {
        String effectiveTemplateContent = (templateContent == null ? "" : templateContent)
                + "\n\nSkill Context:\n" + buildSkillContext(skillSnapshot);
        return """
                请根据以下 PRD 生成最小可运行的前后端代码增量。
                项目：%s
                需求：%s
                项目模式：%s
                生成层级：%s
                是否生成前端：%s
                是否生成后端：%s
                前端输出目录：%s
                后端输出目录：%s

                Markdown 生成模板：
                %s

                输出格式必须是纯 JSON：
                {
                  "files": [
                    {"path": "%s/ExampleController.java", "content": "..."},
                    {"path": "%s/ExampleView.vue", "content": "..."}
                  ]
                }

                约束：
                - 只生成被启用端的代码。若前端未启用，不要返回前端文件；若后端未启用，不要返回后端文件。
                - 前端文件必须放在前端输出目录下，后端文件必须放在后端输出目录下。
                - 当前项目不是从零开始时，只生成与需求相关的增量文件，不要重写无关模块。
                - 单文件内容保持精简，避免生成依赖大段二进制或锁文件。
                - 后端使用 Spring Boot 风格，前端使用 Vue 单文件组件风格。
                - 不要返回解释文字，不要使用 Markdown 代码围栏。

                PRD：
                %s
                """.formatted(
                pipeline.getProjectName(),
                pipeline.getRequirementTitle(),
                pipeline.getProjectMode(),
                pipeline.getCodeLevel(),
                pipeline.getGenerateFrontend() != null && pipeline.getGenerateFrontend() == 1,
                pipeline.getGenerateBackend() != null && pipeline.getGenerateBackend() == 1,
                pipeline.getFrontendOutputPath(),
                pipeline.getBackendOutputPath(),
                effectiveTemplateContent,
                pipeline.getBackendOutputPath(),
                pipeline.getFrontendOutputPath(),
                prdContent == null ? "" : prdContent
        );
    }

    private List<GeneratedFile> parseGeneratedCodeFiles(String modelContent, AutomationPipeline pipeline) throws IOException {
        if (modelContent == null || modelContent.isBlank()) {
            return List.of();
        }
        String json = extractJsonPayload(modelContent);
        JsonNode filesNode = objectMapper.readTree(json).path("files");
        if (!filesNode.isArray()) {
            return List.of();
        }
        List<GeneratedFile> files = new ArrayList<>();
        for (JsonNode node : filesNode) {
            if (files.size() >= MAX_CODE_FILE_COUNT) {
                break;
            }
            String path = normalizeRelativePath(node.path("path").asText(""));
            String content = node.path("content").asText("");
            if (!isSafeGeneratedCodePath(path, pipeline) || content.isBlank()) {
                continue;
            }
            if (content.length() > MAX_CODE_FILE_CHARS) {
                content = content.substring(0, MAX_CODE_FILE_CHARS)
                        + "\n\n/* Truncated by AI Platform code artifact size guard. */\n";
            }
            files.add(new GeneratedFile(path, content));
        }
        return files;
    }

    private String extractJsonPayload(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private String writeGeneratedCode(Long pipelineId, Long jobId, List<GeneratedFile> files) throws IOException {
        Path root = codeArtifactRoot(pipelineId, jobId);
        Files.createDirectories(root);
        List<Map<String, Object>> manifestFiles = new ArrayList<>();
        for (GeneratedFile file : files) {
            Path target = root.resolve(file.path()).normalize();
            if (!target.startsWith(root)) {
                continue;
            }
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.content());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("path", file.path());
            entry.put("size", Files.size(target));
            entry.put("type", file.path().startsWith("backend/") ? "backend" : "front");
            manifestFiles.add(entry);
        }
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("jobId", jobId);
        manifest.put("files", manifestFiles);
        manifest.put("generatedAt", LocalDateTime.now().toString());
        return objectMapper.writeValueAsString(manifest);
    }

    private List<Map<String, Object>> readCodeManifest(AutomationStageRun stage) {
        if (stage.getArtifactContent() != null && !stage.getArtifactContent().isBlank()) {
            try {
                JsonNode filesNode = objectMapper.readTree(stage.getArtifactContent()).path("files");
                if (filesNode.isArray()) {
                    List<Map<String, Object>> files = new ArrayList<>();
                    for (JsonNode node : filesNode) {
                        Map<String, Object> file = new LinkedHashMap<>();
                        file.put("path", node.path("path").asText());
                        file.put("size", node.path("size").asLong());
                        file.put("type", node.path("type").asText());
                        files.add(file);
                    }
                    return files;
                }
            } catch (Exception ignored) {
                return List.of();
            }
        }
        return List.of();
    }

    private AutomationGeneratedCodeBatch archiveGeneratedCode(AutomationPipeline pipeline, AutomationStageRun stage,
                                                              AutomationGenerationJob job, List<GeneratedFile> files,
                                                              String manifest) {
        LocalDateTime now = LocalDateTime.now();
        long totalBytes = files.stream().mapToLong(file -> utf8Size(file.content())).sum();
        AutomationGeneratedCodeBatch batch = new AutomationGeneratedCodeBatch();
        batch.setPipelineId(pipeline.getId());
        batch.setStageRunId(stage.getId());
        batch.setGenerationJobId(job == null ? null : job.getId());
        batch.setArtifactPath(stage.getArtifactPath());
        batch.setManifestJson(manifest);
        batch.setFileCount(files.size());
        batch.setTotalBytes(totalBytes);
        batch.setModelCode(stage.getAiModelCode());
        batch.setCreateTime(now);
        batch.setUpdateTime(now);
        generatedCodeBatchMapper.insert(batch);

        int index = 0;
        for (GeneratedFile file : files) {
            AutomationGeneratedCodeFile codeFile = new AutomationGeneratedCodeFile();
            codeFile.setBatchId(batch.getId());
            codeFile.setPipelineId(pipeline.getId());
            codeFile.setStageRunId(stage.getId());
            codeFile.setGenerationJobId(job == null ? null : job.getId());
            codeFile.setFileIndex(index++);
            codeFile.setFilePath(file.path());
            codeFile.setFileType(fileType(file.path()));
            codeFile.setSizeBytes(utf8Size(file.content()));
            codeFile.setContentHash(sha256(file.content()));
            codeFile.setContent(file.content());
            codeFile.setCreateTime(now);
            generatedCodeFileMapper.insert(codeFile);
        }
        return batch;
    }

    private void recordAiRequirementFeedback(AutomationPipeline pipeline, AutomationStageRun stage,
                                             AutomationGenerationJob job, AutomationGeneratedCodeBatch batch,
                                             String prdContent, List<GeneratedFile> files) {
        if (batch == null) {
            return;
        }
        AiModel model = resolveRequirementFeedbackModel(pipeline, stage);
        if (model == null || isBlank(model.getEndpoint()) || isBlank(model.getApiKey())) {
            saveRequirementFeedback(batch, FEEDBACK_SOURCE_AI, FEEDBACK_ERROR, null,
                    "AI 需求符合度评价未执行：评价模型未配置。",
                    null, "{\"reason\":\"model_not_configured\"}", null, null);
            return;
        }
        try {
            String systemPrompt = "You are a senior software delivery reviewer. Return pure JSON only.";
            String userPrompt = buildRequirementFeedbackPrompt(pipeline, prdContent, files);
            ModelCallResult result = callModel(model, systemPrompt, userPrompt);
            ParsedRequirementFeedback parsed = parseRequirementFeedback(result.content());
            AutomationCodeRequirementFeedback feedback = saveRequirementFeedback(batch, FEEDBACK_SOURCE_AI, parsed.status(), parsed.score(),
                    parsed.summary(), parsed.failureReason(), parsed.detailJson(), result.content(), null);
            if (FEEDBACK_FAILED.equals(feedback.getAlignmentStatus()) && isDoubleFailed(batch.getId())) {
                blockCodeGenerationForDoubleFailure(batch);
            }
        } catch (Exception e) {
            saveRequirementFeedback(batch, FEEDBACK_SOURCE_AI, FEEDBACK_ERROR, null,
                    "AI 需求符合度评价失败：" + e.getMessage(),
                    null, "{\"reason\":\"model_call_failed\"}", e.getMessage(), null);
        }
    }

    private AiModel resolveRequirementFeedbackModel(AutomationPipeline pipeline, AutomationStageRun stage) {
        String modelCode = !isBlank(pipeline.getQualityModelCode()) ? pipeline.getQualityModelCode() : stage.getAiModelCode();
        if (isBlank(modelCode)) {
            return null;
        }
        return modelMapper.selectOne(new LambdaQueryWrapper<AiModel>()
                .eq(AiModel::getModelCode, modelCode)
                .last("LIMIT 1"));
    }

    private String buildRequirementFeedbackPrompt(AutomationPipeline pipeline, String prdContent, List<GeneratedFile> files) {
        StringBuilder codeContext = new StringBuilder();
        for (GeneratedFile file : files) {
            if (codeContext.length() >= MAX_CODE_PREVIEW_CHARS) {
                break;
            }
            codeContext.append("\n\n--- FILE: ").append(file.path()).append(" ---\n");
            String content = file.content() == null ? "" : file.content();
            int remaining = MAX_CODE_PREVIEW_CHARS - codeContext.length();
            codeContext.append(content, 0, Math.min(content.length(), Math.max(remaining, 0)));
        }
        return """
                请判断本次生成代码是否符合需求，并只返回 JSON。
                要求：
                - 评价语言使用中文。
                - 若 alignmentStatus 为 FAILED，failureReason 必须说明具体不合格原因，例如缺失需求、实现偏差、不可验收点或关键风险。
                - 不要因为代码片段不完整而直接给 0 分，请基于可见内容客观判断。

                返回结构：
                {
                  "alignmentStatus": "PASSED|PARTIAL|FAILED",
                  "alignmentScore": 0-100,
                  "summary": "中文摘要",
                  "failureReason": "不合格时必填",
                  "missingRequirements": ["缺失需求"],
                  "mismatches": ["实现偏差"],
                  "suggestedChanges": ["改进建议"]
                }

                项目：%s
                需求标题：%s
                需求描述：%s

                PRD：
                %s

                生成代码：
                %s
                """.formatted(
                nullToEmpty(pipeline.getProjectName()),
                nullToEmpty(pipeline.getRequirementTitle()),
                nullToEmpty(pipeline.getRequirementSummary()),
                nullToEmpty(prdContent),
                codeContext
        );
    }

    private ParsedRequirementFeedback parseRequirementFeedback(String rawContent) throws IOException {
        String json = extractJsonPayload(rawContent == null ? "" : rawContent);
        JsonNode root = objectMapper.readTree(json);
        String status = normalizeFeedbackStatus(firstText(root, "alignmentStatus", "alignment_status", "status"));
        Integer score = root.has("alignmentScore") ? root.path("alignmentScore").asInt()
                : root.has("alignment_score") ? root.path("alignment_score").asInt() : null;
        if (score != null) {
            score = Math.max(0, Math.min(100, score));
        }
        String summary = firstText(root, "summary", "comment", "message");
        String failureReason = FEEDBACK_FAILED.equals(status)
                ? defaultFailureReason(firstText(root, "failureReason", "failure_reason", "reason"))
                : firstText(root, "failureReason", "failure_reason", "reason");
        return new ParsedRequirementFeedback(status, score, summary, failureReason, objectMapper.writeValueAsString(root));
    }

    private AutomationCodeRequirementFeedback saveRequirementFeedback(AutomationGeneratedCodeBatch batch,
                                                                      String source,
                                                                      String status,
                                                                      Integer score,
                                                                      String summary,
                                                                      String failureReason,
                                                                      String detailJson,
                                                                      String rawResult,
                                                                      String reviewedBy) {
        AutomationCodeRequirementFeedback feedback = new AutomationCodeRequirementFeedback();
        feedback.setBatchId(batch.getId());
        feedback.setPipelineId(batch.getPipelineId());
        feedback.setStageRunId(batch.getStageRunId());
        feedback.setGenerationJobId(batch.getGenerationJobId());
        feedback.setFeedbackSource(source);
        feedback.setAlignmentStatus(normalizeFeedbackStatus(status));
        feedback.setAlignmentScore(score);
        feedback.setSummary(summary);
        feedback.setFailureReason(FEEDBACK_FAILED.equals(feedback.getAlignmentStatus())
                ? defaultFailureReason(failureReason)
                : failureReason);
        feedback.setDetailJson(detailJson);
        feedback.setRawResult(rawResult);
        feedback.setReviewedBy(reviewedBy);
        feedback.setCreateTime(LocalDateTime.now());
        codeRequirementFeedbackMapper.insert(feedback);
        return feedback;
    }

    private AutomationGeneratedCodeBatch latestCodeBatch(Long pipelineId) {
        return generatedCodeBatchMapper.selectOne(
                new LambdaQueryWrapper<AutomationGeneratedCodeBatch>()
                        .eq(AutomationGeneratedCodeBatch::getPipelineId, pipelineId)
                        .orderByDesc(AutomationGeneratedCodeBatch::getCreateTime)
                        .orderByDesc(AutomationGeneratedCodeBatch::getId)
                        .last("LIMIT 1")
        );
    }

    private AutomationGeneratedCodeBatch requireCodeBatch(Long pipelineId, Long batchId) {
        AutomationGeneratedCodeBatch batch = batchId == null ? latestCodeBatch(pipelineId) : generatedCodeBatchMapper.selectById(batchId);
        if (batch == null || !pipelineId.equals(batch.getPipelineId())) {
            throw new IllegalArgumentException("Generated code batch does not exist: " + batchId);
        }
        return batch;
    }

    private boolean isDoubleFailed(Long batchId) {
        AutomationCodeRequirementFeedback ai = latestFeedback(batchId, FEEDBACK_SOURCE_AI);
        AutomationCodeRequirementFeedback manual = latestFeedback(batchId, FEEDBACK_SOURCE_MANUAL);
        return ai != null && manual != null
                && FEEDBACK_FAILED.equals(ai.getAlignmentStatus())
                && FEEDBACK_FAILED.equals(manual.getAlignmentStatus());
    }

    private AutomationCodeRequirementFeedback latestFeedback(Long batchId, String source) {
        return codeRequirementFeedbackMapper.selectOne(
                new LambdaQueryWrapper<AutomationCodeRequirementFeedback>()
                        .eq(AutomationCodeRequirementFeedback::getBatchId, batchId)
                        .eq(AutomationCodeRequirementFeedback::getFeedbackSource, source)
                        .orderByDesc(AutomationCodeRequirementFeedback::getCreateTime)
                        .orderByDesc(AutomationCodeRequirementFeedback::getId)
                        .last("LIMIT 1")
        );
    }

    private Map<String, Object> doubleFailedSummary(Long batchId) {
        AutomationCodeRequirementFeedback ai = latestFeedback(batchId, FEEDBACK_SOURCE_AI);
        AutomationCodeRequirementFeedback manual = latestFeedback(batchId, FEEDBACK_SOURCE_MANUAL);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("aiFailureReason", ai == null ? null : ai.getFailureReason());
        summary.put("manualFailureReason", manual == null ? null : manual.getFailureReason());
        summary.put("message", isDoubleFailed(batchId)
                ? "AI 和人工均判定当前生成代码不符合需求，请重新生成代码。"
                : null);
        return summary;
    }

    private void blockCodeGenerationForDoubleFailure(AutomationGeneratedCodeBatch batch) {
        AutomationStageRun stage = requireStage(batch.getStageRunId());
        Map<String, Object> summary = doubleFailedSummary(batch.getId());
        String message = "AI 和人工均判定当前生成代码不符合需求，请重新生成代码。AI原因："
                + nullToEmpty((String) summary.get("aiFailureReason"))
                + "；人工原因：" + nullToEmpty((String) summary.get("manualFailureReason"));
        rejectPendingApprovals(batch.getPipelineId(), batch.getStageRunId(), "Rejected by AI and manual requirement feedback");
        stage.setStatus(STATUS_REJECTED);
        stage.setErrorMessage(message);
        stage.setOutputSummary(message);
        stage.setUpdateTime(LocalDateTime.now());
        stageRunMapper.updateById(stage);

        AutomationPipeline pipeline = requirePipeline(batch.getPipelineId());
        pipeline.setStatus(STATUS_BLOCKED);
        pipeline.setCurrentStage(STAGE_CODE_GENERATION);
        pipeline.setUpdateTime(LocalDateTime.now());
        pipelineMapper.updateById(pipeline);
        refreshPipelineProgress(batch.getPipelineId());
    }

    private String normalizeFeedbackStatus(String status) {
        if (status == null) {
            return FEEDBACK_PARTIAL;
        }
        String normalized = status.trim().toUpperCase();
        return switch (normalized) {
            case "PASS", "PASSED", "SUCCESS", "合格" -> FEEDBACK_PASSED;
            case "PARTIAL", "PARTLY", "部分符合" -> FEEDBACK_PARTIAL;
            case "FAIL", "FAILED", "REJECTED", "不合格" -> FEEDBACK_FAILED;
            case "ERROR" -> FEEDBACK_ERROR;
            default -> throw new IllegalArgumentException("Invalid feedback status: " + status);
        };
    }

    private String defaultFailureReason(String value) {
        return isBlank(value) ? "判定不合格，但未返回具体原因，请人工复核生成代码与需求差异。" : value.trim();
    }

    private String firstText(JsonNode root, String... fields) {
        for (String field : fields) {
            JsonNode value = root.path(field);
            if (!value.isMissingNode() && !value.isNull() && !value.asText("").isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    private String fileType(String path) {
        String normalized = normalizeRelativePath(path);
        if (normalized.startsWith("backend/")) {
            return "backend";
        }
        if (normalized.startsWith("front/") || normalized.startsWith("consumer-front/")) {
            return "front";
        }
        return "other";
    }

    private long utf8Size(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is not available", e);
        }
    }

    private List<GeneratedFile> fallbackCodeFiles(AutomationPipeline pipeline, String prdContent, String reason) {
        String title = sanitizeJavaIdentifier(pipeline.getRequirementTitle());
        List<GeneratedFile> files = new ArrayList<>();
        if (pipeline.getGenerateBackend() != null && pipeline.getGenerateBackend() == 1) {
            String packageName = packageFromBackendPath(pipeline.getBackendOutputPath());
            String controller = """
                package %s;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;

                import java.util.Map;

                @RestController
                @RequestMapping("/api/v1/generated/%s")
                public class %sController {

                    @GetMapping("/health")
                    public Map<String, Object> health() {
                        return Map.of("status", "ready", "requirement", "%s");
                    }
                }
                """.formatted(packageName, title.toLowerCase(), title, escapeJava(pipeline.getRequirementTitle()));
            files.add(new GeneratedFile(pipeline.getBackendOutputPath() + "/" + title + "Controller.java", controller));
        }
        if (pipeline.getGenerateFrontend() != null && pipeline.getGenerateFrontend() == 1) {
            String vue = """
                <template>
                  <section class="generated-feature">
                    <h2>%s</h2>
                    <p>Generated implementation scaffold is ready for review.</p>
                    <pre>%s</pre>
                  </section>
                </template>

                <script setup>
                const requirement = %s
                </script>

                <style scoped>
                .generated-feature { padding: 24px; }
                .generated-feature pre { white-space: pre-wrap; color: #475569; }
                </style>
                """.formatted(escapeHtml(pipeline.getRequirementTitle()), escapeHtml(reason), objectMapper.valueToTree(prdContent == null ? "" : prdContent).toString());
            files.add(new GeneratedFile(pipeline.getFrontendOutputPath() + "/" + title + "View.vue", vue));
        }
        String readme = """
                # Generated Code Artifact

                Requirement: %s

                This fallback scaffold was created locally because: %s
                Template: %s
                Project mode: %s
                Code level: %s
                Frontend enabled: %s
                Backend enabled: %s
                The selected model should normally return a JSON file list. Please check model configuration if this appears unexpectedly.
                """.formatted(
                pipeline.getRequirementTitle(),
                reason,
                pipeline.getTemplateFile(),
                pipeline.getProjectMode(),
                pipeline.getCodeLevel(),
                pipeline.getGenerateFrontend(),
                pipeline.getGenerateBackend()
        );
        files.add(new GeneratedFile("README.md", readme));
        return files;
    }

    private String generatePrdContent(AutomationPipelineRequest request, AutomationGenerationJob job) {
        AiModel model = request.getModelId() == null ? null : modelMapper.selectById(request.getModelId());
        String fallback = buildFallbackPrd(request);
        String templateContent = readPrdTemplateContent(request.getTemplateFile());
        if (model == null || model.getEndpoint() == null || model.getEndpoint().isBlank()
                || model.getApiKey() == null || model.getApiKey().isBlank()) {
            return fallback;
        }

        try {
            String endpoint = model.getEndpoint().endsWith("/")
                    ? model.getEndpoint() + "chat/completions"
                    : model.getEndpoint() + "/chat/completions";
            String systemPrompt = "You are a senior product manager. Return a structured Markdown PRD with background, goals, user stories, functional requirements, non-functional requirements, acceptance criteria, and risks.";
            String userPrompt = buildPrdPrompt(request, templateContent);
            Map<String, Object> body = Map.of(
                    "model", model.getModelCode(),
                    "temperature", model.getDefaultTemperature() == null ? BigDecimal.ONE : model.getDefaultTemperature(),
                    "max_tokens", resolveMaxTokens(model),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    )
            );
            String response = RestClient.create()
                    .post()
                    .uri(endpoint)
                    .header("Authorization", "Bearer " + model.getApiKey())
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(String.class);
            ModelCallResult result = extractModelCallResult(response, systemPrompt, userPrompt);
            applyTokenUsage(job, result);
            String content = result.content();
            return content == null || content.isBlank() ? fallback : content;
        } catch (Exception e) {
            return fallback + "\n\n> Model generation failed, fallback PRD was created locally. Reason: "
                    + e.getMessage();
        }
    }
    private String buildPrdPrompt(AutomationPipelineRequest request, String templateContent) {
        String effectiveTemplateContent = (templateContent == null ? "" : templateContent)
                + "\n\nSkill Context:\n" + buildSkillContext(request.getSkillSnapshot());
        return "PRD Template:\n" + effectiveTemplateContent + "\n\n" + """
                产品线：%s
                项目：%s
                需求标题：%s
                需求描述：%s
                请生成可供产品经理审核和修改的 Markdown PRD。
                """.formatted(
                request.getProductLine(),
                request.getProjectName(),
                request.getRequirementTitle(),
                request.getRequirementSummary() == null ? "" : request.getRequirementSummary()
        );
    }

    int resolveMaxTokens(AiModel model) {
        int configured = model.getMaxTokens() == null ? 4096 : model.getMaxTokens();
        int providerLimit = "MiniMax".equalsIgnoreCase(model.getProvider()) ? MINIMAX_OUTPUT_MAX_TOKENS : 200000;
        return Math.max(1, Math.min(configured, providerLimit));
    }

    private String buildFallbackPrd(AutomationPipelineRequest request) {
        return """
                # %s PRD

                ## 1. 背景
                %s

                ## 2. 目标
                - 将业务需求结构化为可开发、可测试、可验收的产品需求。
                - 后续阶段必须在本 PRD 审核通过后继续。

                ## 3. 用户故事
                - 作为业务负责人，我希望平台理解需求并形成清晰 PRD。
                - 作为产品经理，我希望可以审核、修改并通过 PRD。
                - 作为项目经理，我希望后续研发流程基于通过的 PRD 推进。

                ## 4. 功能需求
                - 支持需求解析。
                - 支持模型生成 PRD。
                - 支持产品审核、拒绝、修改后通过。

                ## 5. 非功能需求
                - PRD 文件需要保存到项目目录。
                - 所有审核动作需要留痕。

                ## 6. 验收标准
                - 新建流水线后自动生成 PRD。
                - 审核队列可查看 PRD。
                - 修改保存通过后，流水线进入下一阶段。

                ## 7. 风险
                - 模型不可用时需要保留可编辑的本地兜底 PRD。
                """.formatted(request.getRequirementTitle(),
                request.getRequirementSummary() == null || request.getRequirementSummary().isBlank()
                        ? "由业务输入生成。" : request.getRequirementSummary());
    }

    private ModelCallResult extractModelCallResult(String response, String systemPrompt, String userPrompt) throws IOException {
        JsonNode root = objectMapper.readTree(response);
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        String text = content.isMissingNode() ? "" : content.asText();
        JsonNode usage = root.path("usage");
        int promptTokens = firstInt(usage, "prompt_tokens", "input_tokens");
        int completionTokens = firstInt(usage, "completion_tokens", "output_tokens");
        int totalTokens = firstInt(usage, "total_tokens");
        if (totalTokens <= 0) {
            promptTokens = estimateTokens(systemPrompt) + estimateTokens(userPrompt);
            completionTokens = estimateTokens(text);
            totalTokens = promptTokens + completionTokens;
        } else if (promptTokens + completionTokens <= 0) {
            promptTokens = estimateTokens(systemPrompt) + estimateTokens(userPrompt);
            completionTokens = Math.max(0, totalTokens - promptTokens);
        }
        return new ModelCallResult(text, promptTokens, completionTokens, totalTokens);
    }

    private int firstInt(JsonNode node, String... names) {
        if (node == null || node.isMissingNode()) return 0;
        for (String name : names) {
            JsonNode value = node.path(name);
            if (value.isNumber()) return Math.max(0, value.asInt());
        }
        return 0;
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }

    private void applyTokenUsage(AutomationGenerationJob job, ModelCallResult result) {
        if (job == null || result == null) return;
        job.setInputTokens(result.inputTokens());
        job.setOutputTokens(result.outputTokens());
        job.setTotalTokens(result.totalTokens());
    }

    private void saveArtifact(AutomationStageRun stage, String content) {
        try {
            Path dir = Paths.get("marketDoc", "generated-prd");
            Files.createDirectories(dir);
            Path file = dir.resolve("pipeline-" + stage.getPipelineId() + "-prd.md");
            Files.writeString(file, content == null ? "" : content);
            stage.setArtifactPath(file.toAbsolutePath().toString());
            stage.setArtifactContent(content);
            stage.setUpdateTime(LocalDateTime.now());
            stageRunMapper.updateById(stage);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save PRD artifact: " + e.getMessage(), e);
        }
    }

    private void refreshPipelineProgress(Long pipelineId) {
        AutomationPipeline pipeline = requirePipeline(pipelineId);
        List<AutomationStageRun> stages = listStages(pipelineId);
        long passed = stages.stream().filter(s -> STATUS_SUCCESS.equals(s.getStatus())).count();
        long failed = stages.stream()
                .filter(s -> STATUS_REJECTED.equals(s.getStatus()) || STATUS_BLOCKED.equals(s.getStatus()))
                .count();

        pipeline.setPassedStages((int) passed);
        pipeline.setFailedStages((int) failed);
        if (failed > 0) {
            pipeline.setStatus(STATUS_BLOCKED);
        } else if (passed == stages.size()) {
            pipeline.setStatus(STATUS_COMPLETED);
            pipeline.setCurrentStage("done");
        } else {
            pipeline.setStatus(STATUS_RUNNING);
            stages.stream()
                    .filter(s -> STATUS_PENDING.equals(s.getStatus()))
                    .min(Comparator.comparing(AutomationStageRun::getStageOrder))
                    .ifPresent(next -> {
                        next.setStatus(STATUS_RUNNING);
                        next.setUpdateTime(LocalDateTime.now());
                        stageRunMapper.updateById(next);
                        pipeline.setCurrentStage(next.getStageKey());
                    });
        }
        pipeline.setUpdateTime(LocalDateTime.now());
        pipelineMapper.updateById(pipeline);
    }

    private void runAutoDeployStages(Long pipelineId, String afterStageKey) {
        AutomationPipeline pipeline = requirePipeline(pipelineId);
        if (!isAutoDeployEnabled(pipeline)) {
            return;
        }
        List<String> stageKeys = List.of("deployment_release", "operations_monitoring");
        boolean start = afterStageKey == null || "test_execution".equals(afterStageKey);
        for (String stageKey : stageKeys) {
            if (!start) {
                start = stageKey.equals(afterStageKey);
                continue;
            }
            AutomationStageRun stage = findStage(pipelineId, stageKey);
            if (stage == null || STATUS_SUCCESS.equals(stage.getStatus())) {
                continue;
            }
            if (!STATUS_PENDING.equals(stage.getStatus()) && !STATUS_RUNNING.equals(stage.getStatus())
                    && !STATUS_BLOCKED.equals(stage.getStatus())) {
                break;
            }
            AutomationStageRun result = deploymentExecutionService.executeStage(requirePipeline(pipelineId), stage);
            refreshPipelineProgress(pipelineId);
            if (!STATUS_SUCCESS.equals(result.getStatus())) {
                break;
            }
        }
    }

    private void runBuildAndTestStages(Long pipelineId, String afterStageKey) {
        List<String> stageKeys = List.of("build_compile", "test_execution");
        boolean start = afterStageKey == null;
        for (String stageKey : stageKeys) {
            if (!start) {
                start = stageKey.equals(afterStageKey);
                continue;
            }
            AutomationStageRun stage = findStage(pipelineId, stageKey);
            if (stage == null || STATUS_SUCCESS.equals(stage.getStatus())) {
                continue;
            }
            if (!STATUS_PENDING.equals(stage.getStatus()) && !STATUS_RUNNING.equals(stage.getStatus())
                    && !STATUS_BLOCKED.equals(stage.getStatus())) {
                break;
            }
            AutomationPipeline pipeline = requirePipeline(pipelineId);
            AutomationStageRun result = "build_compile".equals(stageKey)
                    ? buildTestExecutionService.executeBuild(pipeline, stage)
                    : buildTestExecutionService.executeTest(pipeline, stage);
            refreshPipelineProgress(pipelineId);
            if (!STATUS_SUCCESS.equals(result.getStatus())) {
                break;
            }
        }
        AutomationStageRun testStage = findStage(pipelineId, "test_execution");
        if (testStage != null && STATUS_SUCCESS.equals(testStage.getStatus())
                && isAutoDeployEnabled(requirePipeline(pipelineId))) {
            runAutoDeployStages(pipelineId, "test_execution");
        }
    }

    private boolean isAutoDeployEnabled(AutomationPipeline pipeline) {
        return pipeline != null && pipeline.getAutoDeployEnabled() != null && pipeline.getAutoDeployEnabled() == 1;
    }

    private boolean isCodeQualityEnabled(AutomationPipeline pipeline) {
        return pipeline != null && pipeline.getCodeQualityEnabled() != null && pipeline.getCodeQualityEnabled() == 1;
    }

    private AutomationStageRun runCodeQualityEvaluation(AutomationPipeline pipeline) {
        if (!isCodeQualityEnabled(pipeline)) {
            AutomationStageRun stage = findStage(pipeline.getId(), STAGE_CODE_QUALITY_EVALUATION);
            return stage == null ? findStage(pipeline.getId(), STAGE_CODE_GENERATION) : stage;
        }
        AutomationStageRun qualityStage = findStage(pipeline.getId(), STAGE_CODE_QUALITY_EVALUATION);
        AutomationStageRun codeStage = findStage(pipeline.getId(), STAGE_CODE_GENERATION);
        if (qualityStage == null) {
            throw new IllegalArgumentException("Code quality stage does not exist: " + pipeline.getId());
        }
        if (codeStage == null || codeStage.getArtifactPath() == null || codeStage.getArtifactPath().isBlank()) {
            throw new IllegalStateException("Generated code must exist before quality evaluation");
        }
        LocalDateTime start = LocalDateTime.now();
        pipeline.setStatus(STATUS_RUNNING);
        pipeline.setCurrentStage(STAGE_CODE_QUALITY_EVALUATION);
        pipeline.setUpdateTime(start);
        pipelineMapper.updateById(pipeline);

        qualityStage.setStatus(STATUS_RUNNING);
        qualityStage.setStartTime(start);
        qualityStage.setEndTime(null);
        qualityStage.setDurationMs(null);
        qualityStage.setErrorMessage(null);
        qualityStage.setOutputSummary("代码质量评估正在运行。");
        qualityStage.setUpdateTime(start);
        stageRunMapper.updateById(qualityStage);

        String qualityModelCode = !isBlank(qualityStage.getAiModelCode())
                ? qualityStage.getAiModelCode()
                : pipeline.getQualityModelCode();
        AiModel model = modelMapper.selectOne(new LambdaQueryWrapper<AiModel>()
                .eq(AiModel::getModelCode, qualityModelCode)
                .last("LIMIT 1"));
        CodeQualityService.EvaluationOutcome outcome = codeQualityService.evaluate(pipeline, qualityStage, codeStage, model);
        LocalDateTime now = LocalDateTime.now();
        qualityStage = requireStage(qualityStage.getId());
        qualityStage.setEndTime(now);
        qualityStage.setDurationMs((int) Duration.between(start, now).toMillis());
        qualityStage.setArtifactContent(codeQualityArtifact(outcome));
        qualityStage.setErrorMessage(outcome.passed() ? null : outcome.message());
        qualityStage.setOutputSummary(outcome.passed()
                ? "代码质量评估通过，评分：" + safeScore(outcome.run().getOverallScore())
                : "代码质量评估未通过。" + (outcome.message() == null ? "" : outcome.message()));
        qualityStage.setStatus(outcome.passed() ? STATUS_SUCCESS : STATUS_BLOCKED);
        qualityStage.setUpdateTime(now);
        stageRunMapper.updateById(qualityStage);
        recordCodeQualityGovernance(pipeline, qualityStage, outcome);

        if (outcome.passed()) {
            codeStage = requireStage(codeStage.getId());
            codeStage.setStatus(STATUS_WAITING_APPROVAL);
            codeStage.setOutputSummary("代码质量评估通过，等待架构师审核。");
            codeStage.setUpdateTime(now);
            stageRunMapper.updateById(codeStage);
            pipeline.setStatus(STATUS_RUNNING);
            pipeline.setCurrentStage(STAGE_CODE_GENERATION);
            pipeline.setUpdateTime(now);
            pipelineMapper.updateById(pipeline);
            createApproval(pipeline, codeStage);
        } else {
            pipeline.setStatus(STATUS_BLOCKED);
            pipeline.setCurrentStage(STAGE_CODE_QUALITY_EVALUATION);
            pipeline.setFailedStages((pipeline.getFailedStages() == null ? 0 : pipeline.getFailedStages()) + 1);
            pipeline.setUpdateTime(now);
            pipelineMapper.updateById(pipeline);
        }
        return qualityStage;
    }

    private void recordAutomationGovernance(AutomationPipeline pipeline, AutomationStageRun stage,
                                            AutomationGenerationJob job, String artifactType,
                                            String artifactPath, String artifactSummary,
                                            String modelCode, Map<String, Object> metadata) {
        try {
            outputGovernanceService.recordAutomationOutput(pipeline, stage, job, artifactType,
                    artifactPath, artifactSummary, modelCode, metadata);
        } catch (Exception ignored) {
            // Governance records are observability data and should not interrupt delivery execution.
        }
    }

    private void recordCodeQualityGovernance(AutomationPipeline pipeline, AutomationStageRun stage,
                                             CodeQualityService.EvaluationOutcome outcome) {
        try {
            int issueCount = outcome.issues() == null ? 0 : outcome.issues().size();
            outputGovernanceService.recordCodeQualityOutput(pipeline, stage, outcome.run(), issueCount,
                    outcome.passed(), outcome.message());
        } catch (Exception ignored) {
            // Governance records are observability data and should not interrupt delivery execution.
        }
    }

    private String codeQualityArtifact(CodeQualityService.EvaluationOutcome outcome) {
        try {
            Map<String, Object> artifact = new LinkedHashMap<>();
            artifact.put("run", outcome.run());
            artifact.put("issues", outcome.issues());
            artifact.put("passed", outcome.passed());
            artifact.put("message", outcome.message());
            return objectMapper.writeValueAsString(artifact);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private int safeScore(Integer score) {
        return score == null ? 0 : score;
    }

    private void validateStageCanRun(AutomationPipeline pipeline, AutomationStageRun stage) {
        if (STATUS_COMPLETED.equals(pipeline.getStatus())) {
            throw new IllegalStateException("Completed pipelines cannot be executed");
        }
        List<AutomationStageRun> stages = listStages(pipeline.getId());
        AutomationStageRun rejectedStage = stages.stream()
                .filter(item -> STATUS_REJECTED.equals(item.getStatus()) || STATUS_BLOCKED.equals(item.getStatus()))
                .min(Comparator.comparing(AutomationStageRun::getStageOrder))
                .orElse(null);
        if (rejectedStage != null && !rejectedStage.getId().equals(stage.getId())) {
            throw new IllegalStateException("Only the rejected stage can be modified while a pipeline is blocked");
        }
        if (!STATUS_REJECTED.equals(stage.getStatus())
                && !STATUS_BLOCKED.equals(stage.getStatus())
                && !STATUS_PENDING.equals(stage.getStatus())
                && !STATUS_RUNNING.equals(stage.getStatus())) {
            throw new IllegalStateException("Only pending, blocked, or rejected stages can be executed");
        }
        if (!STATUS_REJECTED.equals(stage.getStatus())
                && !STATUS_BLOCKED.equals(stage.getStatus())
                && pipeline.getCurrentStage() != null
                && !"done".equals(pipeline.getCurrentStage())
                && !pipeline.getCurrentStage().equals(stage.getStageKey())) {
            throw new IllegalStateException("Only the current stage can be executed");
        }
        boolean previousIncomplete = stages.stream()
                .filter(item -> item.getStageOrder() != null && stage.getStageOrder() != null
                        && item.getStageOrder() < stage.getStageOrder())
                .anyMatch(item -> !STATUS_SUCCESS.equals(item.getStatus()));
        if (previousIncomplete) {
            throw new IllegalStateException("Previous stages must be approved before this stage can run");
        }
    }

    private List<AutomationStageRun> listStages(Long pipelineId) {
        return stageRunMapper.selectList(
                new LambdaQueryWrapper<AutomationStageRun>()
                        .eq(AutomationStageRun::getPipelineId, pipelineId)
                        .orderByAsc(AutomationStageRun::getStageOrder)
        );
    }

    private AutomationStageRun findStage(Long pipelineId, String stageKey) {
        return stageRunMapper.selectOne(
                new LambdaQueryWrapper<AutomationStageRun>()
                        .eq(AutomationStageRun::getPipelineId, pipelineId)
                        .eq(AutomationStageRun::getStageKey, stageKey)
                        .last("LIMIT 1")
        );
    }

    private void rejectPendingApprovals(Long pipelineId, Long stageRunId, String comment) {
        approvalMapper.selectList(
                new LambdaQueryWrapper<AutomationApproval>()
                        .eq(AutomationApproval::getPipelineId, pipelineId)
                        .eq(AutomationApproval::getStageRunId, stageRunId)
                        .eq(AutomationApproval::getStatus, STATUS_PENDING)
        ).forEach(approval -> {
            approval.setStatus(STATUS_REJECTED);
            approval.setComment(comment);
            approval.setReviewedTime(LocalDateTime.now());
            approval.setUpdateTime(LocalDateTime.now());
            approvalMapper.updateById(approval);
        });
    }

    private String readStageArtifact(AutomationStageRun stage) {
        if (stage == null) {
            return "";
        }
        if (stage.getArtifactPath() != null && !stage.getArtifactPath().isBlank()) {
            try {
                Path path = Paths.get(stage.getArtifactPath());
                if (Files.isRegularFile(path)) {
                    return Files.readString(path);
                }
            } catch (IOException ignored) {
                return stage.getArtifactContent() == null ? "" : stage.getArtifactContent();
            }
        }
        return stage.getArtifactContent() == null ? "" : stage.getArtifactContent();
    }

    private Path codeArtifactRoot(Long pipelineId) {
        return Paths.get("marketDoc", "generated-code", "pipeline-" + pipelineId).toAbsolutePath().normalize();
    }

    private Path codeArtifactRoot(Long pipelineId, Long jobId) {
        return codeArtifactRoot(pipelineId).resolve("job-" + jobId).normalize();
    }

    private Path codeTemplateRoot() {
        return Paths.get("marketDoc", "code-templates").toAbsolutePath().normalize();
    }

    private Path prdTemplateRoot() {
        return Paths.get("marketDoc", "prd-templates").toAbsolutePath().normalize();
    }

    private List<Map<String, Object>> listMarkdownTemplates(Path root) throws IOException {
        List<Map<String, Object>> templates = new ArrayList<>();
        try (var paths = Files.list(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(path -> {
                        try {
                            Map<String, Object> template = new LinkedHashMap<>();
                            template.put("fileName", path.getFileName().toString());
                            template.put("size", Files.size(path));
                            template.put("updateTime", Files.getLastModifiedTime(path).toString());
                            templates.add(template);
                        } catch (IOException ignored) {
                            // Skip unreadable templates; the editor will surface concrete read errors by file.
                        }
                    });
        }
        return templates;
    }

    private void ensureDefaultCodeTemplate() throws IOException {
        Path root = codeTemplateRoot();
        Files.createDirectories(root);
        Path defaultTemplate = root.resolve(DEFAULT_TEMPLATE_FILE);
        if (!Files.exists(defaultTemplate)) {
            Files.writeString(defaultTemplate, """
                    # 默认代码生成模板

                    ## 生成目标
                    - 根据已审核 PRD 生成可读、可审查、可增量合并的代码。
                    - 优先保持项目现有结构，不做无关重构。

                    ## 输出要求
                    - 后端使用 Spring Boot Controller/Service/DTO 的清晰分层。
                    - 前端使用 Vue SFC，界面简洁，状态明确。
                    - 只返回 JSON 文件清单，不返回说明文字。

                    ## 质量要求
                    - 代码应包含必要的输入校验和错误处理。
                    - 避免大文件、锁文件、二进制内容和无关依赖。
                    """);
        }
    }

    private void ensureDefaultPrdTemplate() throws IOException {
        Path root = prdTemplateRoot();
        Files.createDirectories(root);
        Path defaultTemplate = root.resolve(DEFAULT_PRD_TEMPLATE_FILE);
        if (!Files.exists(defaultTemplate)) {
            Files.writeString(defaultTemplate, """
                    # Default PRD Template

                    ## Background
                    Describe the business context and the user problem.

                    ## Goals
                    List measurable outcomes and boundaries.

                    ## User Stories
                    Capture roles, needs, and expected value.

                    ## Functional Requirements
                    Describe the product behavior in clear, testable terms.

                    ## Non-functional Requirements
                    Include reliability, security, performance, and maintainability needs.

                    ## Acceptance Criteria
                    Define how reviewers decide the requirement is ready for delivery.

                    ## Risks
                    List delivery, model, data, or compliance risks.
                    """);
        }
    }

    private Path resolveTemplatePath(String fileName) {
        String safeName = resolveTemplateFile(fileName);
        Path root = codeTemplateRoot();
        Path path = root.resolve(safeName).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid template file: " + fileName);
        }
        return path;
    }

    private String resolveTemplateFile(String fileName) {
        if (isBlank(fileName)) {
            return DEFAULT_TEMPLATE_FILE;
        }
        String safeName = normalizeRelativePath(fileName).trim();
        if (safeName.contains("/") || safeName.contains("..") || !safeName.endsWith(".md")) {
            throw new IllegalArgumentException("Template file must be a markdown file name");
        }
        return safeName;
    }

    private Path resolvePrdTemplatePath(String fileName) {
        String safeName = resolvePrdTemplateFile(fileName);
        Path root = prdTemplateRoot();
        Path path = root.resolve(safeName).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid PRD template file: " + fileName);
        }
        return path;
    }

    private String resolvePrdTemplateFile(String fileName) {
        if (isBlank(fileName)) {
            return DEFAULT_PRD_TEMPLATE_FILE;
        }
        String safeName = normalizeRelativePath(fileName).trim();
        if (safeName.contains("/") || safeName.contains("..") || !safeName.endsWith(".md")) {
            throw new IllegalArgumentException("PRD template file must be a markdown file name");
        }
        return safeName;
    }

    private String readTemplateContent(String fileName) {
        try {
            ensureDefaultCodeTemplate();
            Path path = resolveTemplatePath(DEFAULT_TEMPLATE_FILE);
            return Files.readString(path);
        } catch (IOException e) {
            return "";
        }
    }

    private String readPrdTemplateContent(String fileName) {
        try {
            ensureDefaultPrdTemplate();
            Path path = resolvePrdTemplatePath(fileName);
            return Files.isRegularFile(path) ? Files.readString(path) : Files.readString(resolvePrdTemplatePath(DEFAULT_PRD_TEMPLATE_FILE));
        } catch (IOException e) {
            return "";
        }
    }

    private AutomationPipeline pipelineFromCodeSnapshot(AutomationGenerationJob job) throws IOException {
        AutomationPipeline base = requirePipeline(job.getPipelineId());
        JsonNode snapshot = objectMapper.readTree(job.getContextSnapshot());
        AutomationPipeline pipeline = new AutomationPipeline();
        pipeline.setId(base.getId());
        pipeline.setProjectName(snapshot.path("projectName").asText(base.getProjectName()));
        pipeline.setRequirementTitle(snapshot.path("requirementTitle").asText(base.getRequirementTitle()));
        pipeline.setTemplateFile(snapshot.path("templateFile").asText(base.getTemplateFile()));
        pipeline.setProjectMode(snapshot.path("projectMode").asText(base.getProjectMode()));
        pipeline.setCodeLevel(snapshot.path("codeLevel").asText(base.getCodeLevel()));
        pipeline.setGenerateFrontend(snapshot.path("generateFrontend").isMissingNode()
                ? base.getGenerateFrontend() : snapshot.path("generateFrontend").asInt());
        pipeline.setGenerateBackend(snapshot.path("generateBackend").isMissingNode()
                ? base.getGenerateBackend() : snapshot.path("generateBackend").asInt());
        pipeline.setFrontendOutputPath(snapshot.path("frontendOutputPath").asText(base.getFrontendOutputPath()));
        pipeline.setBackendOutputPath(snapshot.path("backendOutputPath").asText(base.getBackendOutputPath()));
        pipeline.setSkillId(snapshot.path("skillId").isMissingNode() || snapshot.path("skillId").isNull()
                ? base.getSkillId() : snapshot.path("skillId").asLong());
        pipeline.setSkillSnapshot(snapshot.path("skillSnapshot").asText(base.getSkillSnapshot()));
        pipeline.setAutoDeployEnabled(snapshot.path("autoDeployEnabled").isMissingNode()
                ? base.getAutoDeployEnabled() : snapshot.path("autoDeployEnabled").asInt());
        pipeline.setDeployProfileId(snapshot.path("deployProfileId").isMissingNode() || snapshot.path("deployProfileId").isNull()
                ? base.getDeployProfileId() : snapshot.path("deployProfileId").asLong());
        pipeline.setDeployProfileSnapshot(snapshot.path("deployProfileSnapshot").asText(base.getDeployProfileSnapshot()));
        pipeline.setCodeQualityEnabled(snapshot.path("codeQualityEnabled").isMissingNode()
                ? base.getCodeQualityEnabled() : snapshot.path("codeQualityEnabled").asInt());
        pipeline.setCodeQualityStandardId(snapshot.path("codeQualityStandardId").isMissingNode() || snapshot.path("codeQualityStandardId").isNull()
                ? base.getCodeQualityStandardId() : snapshot.path("codeQualityStandardId").asLong());
        pipeline.setCodeQualityStandardSnapshot(snapshot.path("codeQualityStandardSnapshot").asText(base.getCodeQualityStandardSnapshot()));
        pipeline.setCodeQualityGateSnapshot(snapshot.path("codeQualityGateSnapshot").asText(base.getCodeQualityGateSnapshot()));
        pipeline.setQualityModelCode(snapshot.path("qualityModelCode").asText(base.getQualityModelCode()));
        return pipeline;
    }

    private Map<String, Object> directoryNode(Path absolutePath, String label, String relativePath, int depth) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("label", label);
        node.put("value", relativePath);
        node.put("path", relativePath);
        node.put("disabled", relativePath.isBlank());
        node.put("children", depth >= MAX_DIRECTORY_TREE_DEPTH ? List.of() : listChildDirectories(absolutePath, depth + 1));
        return node;
    }

    private List<Map<String, Object>> listChildDirectories(Path parent, int depth) {
        if (!Files.isDirectory(parent)) {
            return List.of();
        }
        Path projectRoot = Paths.get("").toAbsolutePath().normalize();
        List<Map<String, Object>> children = new ArrayList<>();
        try (var paths = Files.list(parent)) {
            paths.filter(Files::isDirectory)
                    .filter(path -> !shouldExcludeDirectory(projectRoot, path))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(path -> {
                        String relativePath = normalizeRelativePath(projectRoot.relativize(path.toAbsolutePath().normalize()).toString());
                        children.add(directoryNode(path, path.getFileName().toString(), relativePath, depth));
                    });
        } catch (IOException ignored) {
            return List.of();
        }
        return children;
    }

    private boolean shouldExcludeDirectory(Path projectRoot, Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        if (EXCLUDED_DIRECTORY_NAMES.contains(name)) {
            return true;
        }
        String relativePath = normalizeRelativePath(projectRoot.relativize(path.toAbsolutePath().normalize()).toString());
        return EXCLUDED_DIRECTORY_PATHS.contains(relativePath);
    }

    private String buildSkillContext(String skillSnapshot) {
        if (skillSnapshot == null || skillSnapshot.isBlank()) {
            return "No skill selected.";
        }
        try {
            JsonNode skill = objectMapper.readTree(skillSnapshot);
            StringBuilder builder = new StringBuilder();
            builder.append("Skill Name: ").append(skill.path("skillName").asText("")).append('\n');
            builder.append("Skill Code: ").append(skill.path("skillCode").asText("")).append('\n');
            if (!skill.path("description").asText("").isBlank()) {
                builder.append("Description: ").append(skill.path("description").asText()).append('\n');
            }
            if (!skill.path("promptContent").asText("").isBlank()) {
                builder.append("Prompt: ").append(skill.path("promptContent").asText()).append('\n');
            }
            JsonNode functions = skill.path("functionDefinitions");
            if (functions.isArray() && functions.size() > 0) {
                builder.append("Readable Java function metadata:\n");
                for (JsonNode function : functions) {
                    if (function.path("enabled").isBoolean() && !function.path("enabled").asBoolean()) {
                        continue;
                    }
                    builder.append("- ").append(function.path("name").asText("")).append('\n');
                    if (!function.path("description").asText("").isBlank()) {
                        builder.append("  Description: ").append(function.path("description").asText()).append('\n');
                    }
                    if (!function.path("parametersJson").asText("").isBlank()) {
                        builder.append("  Parameters JSON: ").append(function.path("parametersJson").asText()).append('\n');
                    }
                    if (!function.path("returnSchema").asText("").isBlank()) {
                        builder.append("  Return: ").append(function.path("returnSchema").asText()).append('\n');
                    }
                    if (!function.path("javaSnippet").asText("").isBlank()) {
                        builder.append("  Java snippet:\n").append(function.path("javaSnippet").asText()).append('\n');
                    }
                }
            }
            return builder.toString();
        } catch (IOException e) {
            return skillSnapshot;
        }
    }

    private String normalizeRelativePath(String path) {
        return path.replace("\\", "/").replaceAll("^/+", "");
    }

    private String normalizeGenerateRoot(String path, String fallback) {
        String value = isBlank(path) ? fallback : normalizeRelativePath(path.trim());
        if (value.contains("..") || Paths.get(value).isAbsolute()) {
            throw new IllegalArgumentException("Generated code path must be a relative path inside the project");
        }
        return value.replaceAll("/+$", "");
    }

    private boolean isSafeGeneratedCodePath(String path, AutomationPipeline pipeline) {
        if (path == null || path.isBlank() || path.contains("..") || Paths.get(path).isAbsolute()) {
            return false;
        }
        if ("README.md".equals(path)) {
            return true;
        }
        boolean backendAllowed = pipeline.getGenerateBackend() != null && pipeline.getGenerateBackend() == 1
                && path.startsWith(pipeline.getBackendOutputPath() + "/");
        boolean frontendAllowed = pipeline.getGenerateFrontend() != null && pipeline.getGenerateFrontend() == 1
                && path.startsWith(pipeline.getFrontendOutputPath() + "/");
        return backendAllowed || frontendAllowed;
    }

    private String packageFromBackendPath(String backendPath) {
        String normalized = normalizeRelativePath(backendPath);
        int javaIndex = normalized.indexOf("src/main/java/");
        if (javaIndex < 0) {
            return "com.aipal.generated";
        }
        String packagePath = normalized.substring(javaIndex + "src/main/java/".length()).replace("/", ".");
        return packagePath.isBlank() ? "com.aipal.generated" : packagePath;
    }

    private String sanitizeJavaIdentifier(String value) {
        String base = value == null ? "GeneratedFeature" : value.replaceAll("[^A-Za-z0-9]", "");
        if (base.isBlank()) {
            base = "GeneratedFeature";
        }
        if (Character.isDigit(base.charAt(0))) {
            base = "Feature" + base;
        }
        return base.substring(0, 1).toUpperCase() + base.substring(1);
    }

    private String escapeJava(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private AutomationPipeline requirePipeline(Long pipelineId) {
        AutomationPipeline pipeline = pipelineMapper.selectById(pipelineId);
        if (pipeline == null) {
            throw new IllegalArgumentException("Pipeline does not exist: " + pipelineId);
        }
        return pipeline;
    }

    private AutomationStageRun requireStage(Long stageId) {
        AutomationStageRun stage = stageRunMapper.selectById(stageId);
        if (stage == null) {
            throw new IllegalArgumentException("Stage does not exist: " + stageId);
        }
        return stage;
    }

    private void validateCreateRequest(AutomationPipelineRequest request) {
        if (request == null || isBlank(request.getProductLine()) || isBlank(request.getProjectName())
                || isBlank(request.getRequirementTitle())) {
            throw new IllegalArgumentException("productLine, projectName, and requirementTitle are required");
        }
        if (Boolean.TRUE.equals(request.getAutoDeployEnabled()) && request.getDeployProfileId() == null) {
            throw new IllegalArgumentException("deployProfileId is required when auto deploy is enabled");
        }
        if (Boolean.TRUE.equals(request.getCodeQualityEnabled()) && request.getCodeQualityStandardId() == null) {
            throw new IllegalArgumentException("codeQualityStandardId is required when code quality is enabled");
        }
        if (Boolean.TRUE.equals(request.getCodeQualityEnabled()) && isBlank(request.getQualityModelCode())) {
            throw new IllegalArgumentException("qualityModelCode is required when code quality is enabled");
        }
    }

    private String inputForStage(AutomationPipelineRequest request, StageDefinition definition) {
        return stageIntro(definition.key(), request.getRequirementTitle());
    }

    private String resolveModelCode(AutomationPipelineRequest request) {
        if (request.getModelId() != null) {
            AiModel model = modelMapper.selectById(request.getModelId());
            if (model != null && model.getModelCode() != null && !model.getModelCode().isBlank()) {
                return model.getModelCode();
            }
        }
        return request.getAiModelCode() == null || request.getAiModelCode().isBlank()
                ? "default-open-model" : request.getAiModelCode();
    }

    private String resolveQualityModelCode(AutomationPipelineRequest request) {
        if (isBlank(request.getQualityModelCode())) {
            throw new IllegalArgumentException("qualityModelCode is required when code quality is enabled");
        }
        return request.getQualityModelCode().trim();
    }

    private String buildAiOutput(AutomationStageRun stage) {
        return stage.getStageName() + "已完成，流水线进入人工审核或下一阶段处理。";
    }

    private String stageIntro(String stageKey, String requirementTitle) {
        String title = isBlank(requirementTitle) ? "当前需求" : requirementTitle;
        return switch (stageKey) {
            case "requirement_analysis" -> "梳理「" + title + "」的业务目标、范围、验收标准与交付约束，生成 PRD 供审核。";
            case "code_generation" -> "根据已通过的 PRD 为「" + title + "」生成前后端代码与必要的工程文件。";
            case "code_quality_evaluation" -> "依据代码质量标准检查生成结果，识别风险、阻塞项和需要修复的问题。";
            case "build_compile" -> "对生成代码执行构建与编译检查，确认项目结构、依赖和基础语法可用。";
            case "test_execution" -> "执行测试验证核心流程，确认主要功能符合 PRD 和质量门禁要求。";
            case "deployment_release" -> "按部署配置发布构建产物，并记录镜像、容器或流水线执行结果。";
            case "operations_monitoring" -> "观察部署后的运行状态、健康检查和异常信息，确认服务可持续运行。";
            case "delivery_report" -> "汇总需求、代码、测试、部署和审核结果，形成最终交付报告。";
            default -> "处理「" + title + "」的流水线阶段任务。";
        };
    }

    private String reviewerFor(String stageKey) {
        return switch (stageKey) {
            case "requirement_analysis" -> "product_manager";
            case "code_generation" -> "architect";
            case "code_quality_evaluation" -> "architect";
            case "build_compile" -> "developer";
            case "test_execution" -> "tester";
            case "deployment_release" -> "ops";
            case "operations_monitoring" -> "ops";
            default -> "project_manager";
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private List<StageDefinition> stageDefinitions() {
        return stageDefinitions(false);
    }

    private List<StageDefinition> stageDefinitions(boolean codeQualityEnabled) {
        List<StageDefinition> stages = new ArrayList<>();
        stages.add(new StageDefinition("requirement_analysis", "需求分析", 1));
        stages.add(new StageDefinition("code_generation", "代码生成", 2));
        int order = 3;
        if (codeQualityEnabled) {
            stages.add(new StageDefinition("code_quality_evaluation", "代码质量评估", order++));
        }
        stages.add(new StageDefinition("build_compile", "构建编译", order++));
        stages.add(new StageDefinition("test_execution", "测试执行", order++));
        stages.add(new StageDefinition("deployment_release", "部署发布", order++));
        stages.add(new StageDefinition("operations_monitoring", "运维监控", order++));
        stages.add(new StageDefinition("delivery_report", "交付报告", order));
        return stages;
    }

    private record StageDefinition(String key, String name, int order) {
    }

    private record GeneratedFile(String path, String content) {
    }

    private record ParsedRequirementFeedback(String status, Integer score, String summary, String failureReason,
                                             String detailJson) {
    }

    private record ModelCallResult(String content, int inputTokens, int outputTokens, int totalTokens) {
    }
}
