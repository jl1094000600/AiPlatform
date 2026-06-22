package com.aipal.service;

import com.aipal.dto.BadCaseCaptureRequest;
import com.aipal.entity.AutomationApproval;
import com.aipal.entity.AutomationCodeRequirementFeedback;
import com.aipal.entity.AutomationGeneratedCodeBatch;
import com.aipal.entity.AutomationPipeline;
import com.aipal.entity.AutomationStageRun;
import com.aipal.entity.BadCaseRecord;
import com.aipal.mapper.BadCaseRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BadCaseService {
    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String STAGE_PRD = "PRD";
    public static final String STAGE_CODE = "CODE";

    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STAGE_REQUIREMENT_ANALYSIS = "requirement_analysis";
    private static final String STAGE_CODE_GENERATION = "code_generation";

    private final BadCaseRecordMapper badCaseRecordMapper;

    public Page<BadCaseRecord> list(int pageNum, int pageSize, String stage, String badcaseType,
                                    String severity, String sourceType, String keyword) {
        LambdaQueryWrapper<BadCaseRecord> wrapper = new LambdaQueryWrapper<BadCaseRecord>()
                .eq(hasText(stage), BadCaseRecord::getStage, stage)
                .eq(hasText(badcaseType), BadCaseRecord::getBadcaseType, badcaseType)
                .eq(hasText(severity), BadCaseRecord::getSeverity, severity)
                .eq(hasText(sourceType), BadCaseRecord::getSourceType, sourceType)
                .and(hasText(keyword), w -> w
                        .like(BadCaseRecord::getCaseCode, keyword)
                        .or().like(BadCaseRecord::getRequirementTitle, keyword)
                        .or().like(BadCaseRecord::getProjectName, keyword)
                        .or().like(BadCaseRecord::getFailureReason, keyword))
                .orderByDesc(BadCaseRecord::getCreateTime)
                .orderByDesc(BadCaseRecord::getId);
        return badCaseRecordMapper.selectPage(new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100)), wrapper);
    }

    public Map<String, Object> statistics() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", badCaseRecordMapper.selectCount(null));
        result.put("byStage", groupedCount("stage"));
        result.put("bySeverity", groupedCount("severity"));
        result.put("byType", groupedCount("badcase_type"));
        result.put("bySource", groupedCount("source_type"));
        return result;
    }

    @Transactional
    public BadCaseRecord capture(BadCaseCaptureRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("badcase request is required");
        }
        if (!hasText(request.getStage()) || !hasText(request.getBadcaseType()) || !hasText(request.getFailureReason())) {
            throw new IllegalArgumentException("stage, badcaseType, and failureReason are required");
        }
        BadCaseRecord record = new BadCaseRecord();
        record.setCaseCode(nextCaseCode(request.getSourceType()));
        record.setSourceType(defaultText(request.getSourceType(), SOURCE_MANUAL));
        record.setStage(request.getStage());
        record.setBadcaseType(request.getBadcaseType());
        record.setSeverity(defaultText(request.getSeverity(), "P1"));
        record.setProjectName(request.getProjectName());
        record.setRequirementTitle(request.getRequirementTitle());
        record.setInputPrompt(request.getInputPrompt());
        record.setGeneratedPrd(request.getGeneratedPrd());
        record.setGeneratedCode(request.getGeneratedCode());
        record.setExpectedBehavior(request.getExpectedBehavior());
        record.setFailureReason(request.getFailureReason());
        record.setReviewedBy(request.getReviewedBy());
        record.setPipelineId(request.getPipelineId());
        record.setStageRunId(request.getStageRunId());
        record.setBatchId(request.getBatchId());
        record.setFeedbackId(request.getFeedbackId());
        record.setApprovalId(request.getApprovalId());
        record.setTags(request.getTags());
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        badCaseRecordMapper.insert(record);
        return record;
    }

    @Transactional
    public void captureRejectedApproval(AutomationPipeline pipeline, AutomationStageRun stage, AutomationApproval approval) {
        if (pipeline == null || stage == null || approval == null || !STATUS_REJECTED.equals(approval.getStatus())) {
            return;
        }
        String badcaseStage = resolveBadcaseStage(stage.getStageKey());
        if (badcaseStage == null) {
            return;
        }
        BadCaseCaptureRequest request = new BadCaseCaptureRequest();
        request.setSourceType(SOURCE_MANUAL);
        request.setStage(badcaseStage);
        request.setBadcaseType(STAGE_PRD.equals(badcaseStage) ? "ACCEPTANCE_REJECTED" : "REQUIREMENT_ALIGNMENT_FAILED");
        request.setSeverity("P1");
        request.setProjectName(pipeline.getProjectName());
        request.setRequirementTitle(pipeline.getRequirementTitle());
        request.setInputPrompt(pipeline.getRequirementSummary());
        request.setGeneratedPrd(STAGE_PRD.equals(badcaseStage) ? stage.getArtifactContent() : null);
        request.setGeneratedCode(STAGE_CODE.equals(badcaseStage) ? artifactReference(stage) : null);
        request.setExpectedBehavior(STAGE_PRD.equals(badcaseStage)
                ? "PRD should satisfy product review criteria and be ready for code generation."
                : "Generated code should satisfy the reviewed PRD and be ready for build/test.");
        request.setFailureReason(defaultText(approval.getComment(), "Manual reviewer rejected the generated artifact."));
        request.setReviewedBy(approval.getReviewedBy());
        request.setPipelineId(pipeline.getId());
        request.setStageRunId(stage.getId());
        request.setApprovalId(approval.getId());
        request.setTags(STAGE_PRD.equals(badcaseStage) ? "manual,prd-review" : "manual,code-review");
        capture(request);
    }

    @Transactional
    public void captureFailedCodeFeedback(AutomationPipeline pipeline, AutomationGeneratedCodeBatch batch,
                                          AutomationCodeRequirementFeedback feedback) {
        if (pipeline == null || batch == null || feedback == null || !"FAILED".equals(feedback.getAlignmentStatus())) {
            return;
        }
        BadCaseCaptureRequest request = new BadCaseCaptureRequest();
        request.setSourceType(SOURCE_MANUAL);
        request.setStage(STAGE_CODE);
        request.setBadcaseType("REQUIREMENT_ALIGNMENT_FAILED");
        request.setSeverity("P1");
        request.setProjectName(pipeline.getProjectName());
        request.setRequirementTitle(pipeline.getRequirementTitle());
        request.setInputPrompt(pipeline.getRequirementSummary());
        request.setGeneratedCode(defaultText(batch.getManifestJson(), artifactReference(batch)));
        request.setExpectedBehavior("Generated code should match the approved PRD and pass manual requirement review.");
        request.setFailureReason(feedback.getFailureReason());
        request.setReviewedBy(feedback.getReviewedBy());
        request.setPipelineId(pipeline.getId());
        request.setStageRunId(batch.getStageRunId());
        request.setBatchId(batch.getId());
        request.setFeedbackId(feedback.getId());
        request.setTags("manual,code-feedback");
        capture(request);
    }

    private Map<String, Long> groupedCount(String column) {
        List<Map<String, Object>> rows = badCaseRecordMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<BadCaseRecord>()
                        .select(column + " AS name", "COUNT(*) AS total")
                        .groupBy(column)
        );
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object name = valueIgnoreCase(row, "name");
            Object total = valueIgnoreCase(row, "total");
            result.put(String.valueOf(name), total instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(total)));
        }
        return result;
    }

    private Object valueIgnoreCase(Map<String, Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String artifactReference(AutomationStageRun stage) {
        return stage.getArtifactPath() == null ? null : "Generated code artifact path: " + stage.getArtifactPath();
    }

    private String artifactReference(AutomationGeneratedCodeBatch batch) {
        return batch.getArtifactPath() == null ? null : "Generated code artifact path: " + batch.getArtifactPath();
    }

    private String resolveBadcaseStage(String stageKey) {
        if (STAGE_REQUIREMENT_ANALYSIS.equals(stageKey)) {
            return STAGE_PRD;
        }
        if (STAGE_CODE_GENERATION.equals(stageKey)) {
            return STAGE_CODE;
        }
        return null;
    }

    private String nextCaseCode(String sourceType) {
        String prefix = "SEED".equals(sourceType) ? "BC-SEED-" : "BC-MANUAL-";
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String defaultText(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }
}
