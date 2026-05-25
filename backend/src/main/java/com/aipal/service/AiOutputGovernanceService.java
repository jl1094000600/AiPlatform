package com.aipal.service;

import com.aipal.entity.AiOutputGovernanceRecord;
import com.aipal.entity.AiOutputGovernancePolicyTemplate;
import com.aipal.entity.AutomationCodeQualityRun;
import com.aipal.entity.AutomationGenerationJob;
import com.aipal.entity.AutomationPipeline;
import com.aipal.entity.AutomationStageRun;
import com.aipal.mapper.AiOutputGovernancePolicyTemplateMapper;
import com.aipal.mapper.AiOutputGovernanceRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AiOutputGovernanceService extends ServiceImpl<AiOutputGovernanceRecordMapper, AiOutputGovernanceRecord> {
    private static final String SOURCE_AUTOMATION_PIPELINE = "AUTOMATION_PIPELINE";
    private static final String STATUS_NEEDS_REVIEW = "NEEDS_REVIEW";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_BLOCKED = "BLOCKED";
    private static final String RISK_LOW = "LOW";
    private static final String RISK_MEDIUM = "MEDIUM";
    private static final String RISK_HIGH = "HIGH";

    private final ObjectMapper objectMapper;
    private final AiOutputGovernancePolicyTemplateMapper policyTemplateMapper;

    public Page<AiOutputGovernanceRecord> listRecords(int pageNum, int pageSize, Long pipelineId,
                                                       String artifactType, String riskLevel,
                                                       String governanceStatus) {
        LambdaQueryWrapper<AiOutputGovernanceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(pipelineId != null, AiOutputGovernanceRecord::getPipelineId, pipelineId);
        wrapper.eq(hasText(artifactType), AiOutputGovernanceRecord::getArtifactType, artifactType);
        wrapper.eq(hasText(riskLevel), AiOutputGovernanceRecord::getRiskLevel, riskLevel);
        wrapper.eq(hasText(governanceStatus), AiOutputGovernanceRecord::getGovernanceStatus, governanceStatus);
        wrapper.orderByDesc(AiOutputGovernanceRecord::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    public AiOutputGovernanceRecord getRecord(Long id) {
        return getById(id);
    }

    public Page<AiOutputGovernancePolicyTemplate> listPolicyTemplates(int pageNum, int pageSize,
                                                                      String category, Integer status) {
        LambdaQueryWrapper<AiOutputGovernancePolicyTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(hasText(category), AiOutputGovernancePolicyTemplate::getCategory, category);
        wrapper.eq(status != null, AiOutputGovernancePolicyTemplate::getStatus, status);
        wrapper.orderByDesc(AiOutputGovernancePolicyTemplate::getCreateTime);
        return policyTemplateMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public List<AiOutputGovernancePolicyTemplate> listEnabledPolicyTemplates() {
        return policyTemplateMapper.selectList(new LambdaQueryWrapper<AiOutputGovernancePolicyTemplate>()
                .eq(AiOutputGovernancePolicyTemplate::getStatus, 1)
                .orderByAsc(AiOutputGovernancePolicyTemplate::getId));
    }

    public AiOutputGovernancePolicyTemplate createPolicyTemplate(AiOutputGovernancePolicyTemplate request) {
        AiOutputGovernancePolicyTemplate policy = normalizePolicyTemplate(new AiOutputGovernancePolicyTemplate(), request);
        LocalDateTime now = LocalDateTime.now();
        policy.setCreateTime(now);
        policy.setUpdateTime(now);
        policyTemplateMapper.insert(policy);
        return policy;
    }

    public AiOutputGovernancePolicyTemplate updatePolicyTemplate(Long id, AiOutputGovernancePolicyTemplate request) {
        AiOutputGovernancePolicyTemplate existing = policyTemplateMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Policy template does not exist: " + id);
        }
        AiOutputGovernancePolicyTemplate policy = normalizePolicyTemplate(existing, request);
        policy.setUpdateTime(LocalDateTime.now());
        policyTemplateMapper.updateById(policy);
        return policy;
    }

    public boolean deletePolicyTemplate(Long id) {
        return policyTemplateMapper.deleteById(id) > 0;
    }

    public void recordAutomationOutput(AutomationPipeline pipeline, AutomationStageRun stage,
                                       AutomationGenerationJob job, String artifactType,
                                       String artifactPath, String artifactSummary,
                                       String modelCode, Map<String, Object> metadata) {
        AiOutputGovernanceRecord record = baseRecord(pipeline, stage);
        record.setGenerationJobId(job == null ? null : job.getId());
        record.setArtifactType(artifactType);
        record.setArtifactPath(artifactPath);
        record.setArtifactSummary(limit(artifactSummary, 1000));
        record.setModelCode(modelCode);
        record.setGovernanceStatus(STATUS_NEEDS_REVIEW);
        applyDefaultRisk(record, artifactType);
        record.setPolicySnapshot(buildPolicySnapshot(pipeline));
        record.setMetadataJson(toJson(metadata));
        applyPolicyTemplates(record, metadata);
        record.setInputTokens(job == null ? null : job.getInputTokens());
        record.setOutputTokens(job == null ? null : job.getOutputTokens());
        record.setTotalTokens(job == null ? null : job.getTotalTokens());
        save(record);
    }

    public void recordCodeQualityOutput(AutomationPipeline pipeline, AutomationStageRun stage,
                                        AutomationCodeQualityRun run, int issueCount, boolean passed,
                                        String message) {
        AiOutputGovernanceRecord record = baseRecord(pipeline, stage);
        record.setArtifactType("CODE_QUALITY");
        record.setArtifactPath(stage == null ? null : stage.getArtifactPath());
        String summary = message == null && run != null ? run.getSummary() : message;
        record.setArtifactSummary(limit(summary, 1000));
        record.setModelCode(run == null ? null : run.getModelCode());
        record.setGovernanceStatus(passed ? STATUS_APPROVED : STATUS_BLOCKED);
        applyQualityRisk(record, run, issueCount, passed);
        record.setPolicySnapshot(run == null ? "{}" : run.getGateSnapshot());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("runId", run == null ? null : run.getId());
        metadata.put("overallScore", run == null ? null : run.getOverallScore());
        metadata.put("passed", passed);
        metadata.put("issueCount", issueCount);
        metadata.put("standardId", run == null ? null : run.getStandardId());
        metadata.put("metrics", run == null ? null : run.getMetricsJson());
        record.setMetadataJson(toJson(metadata));
        applyPolicyTemplates(record, metadata);
        record.setInputTokens(run == null ? null : run.getInputTokens());
        record.setOutputTokens(run == null ? null : run.getOutputTokens());
        record.setTotalTokens(run == null ? null : run.getTotalTokens());
        save(record);
    }

    public boolean hasBlockingRecords(Long pipelineId, Long stageRunId) {
        Long count = count(new LambdaQueryWrapper<AiOutputGovernanceRecord>()
                .eq(AiOutputGovernanceRecord::getPipelineId, pipelineId)
                .eq(AiOutputGovernanceRecord::getStageRunId, stageRunId)
                .eq(AiOutputGovernanceRecord::getGovernanceStatus, STATUS_BLOCKED));
        return count != null && count > 0;
    }

    public List<AiOutputGovernanceRecord> listStageRecords(Long pipelineId, Long stageRunId) {
        return list(new LambdaQueryWrapper<AiOutputGovernanceRecord>()
                .eq(AiOutputGovernanceRecord::getPipelineId, pipelineId)
                .eq(AiOutputGovernanceRecord::getStageRunId, stageRunId)
                .orderByDesc(AiOutputGovernanceRecord::getCreateTime));
    }

    public void markStageReviewed(Long pipelineId, Long stageRunId, boolean approved) {
        AiOutputGovernanceRecord update = new AiOutputGovernanceRecord();
        update.setGovernanceStatus(approved ? STATUS_APPROVED : "REJECTED");
        update.setUpdateTime(LocalDateTime.now());
        update(update, new LambdaUpdateWrapper<AiOutputGovernanceRecord>()
                .eq(AiOutputGovernanceRecord::getPipelineId, pipelineId)
                .eq(AiOutputGovernanceRecord::getStageRunId, stageRunId)
                .eq(AiOutputGovernanceRecord::getGovernanceStatus, STATUS_NEEDS_REVIEW));
    }

    private AiOutputGovernancePolicyTemplate normalizePolicyTemplate(AiOutputGovernancePolicyTemplate target,
                                                                     AiOutputGovernancePolicyTemplate request) {
        if (request == null) {
            throw new IllegalArgumentException("Policy template request is required");
        }
        target.setPolicyCode(required(request.getPolicyCode(), "policyCode").trim());
        target.setPolicyName(required(request.getPolicyName(), "policyName").trim());
        target.setDescription(request.getDescription());
        target.setCategory(defaultText(request.getCategory(), "security"));
        target.setSeverity(defaultText(request.getSeverity(), "MAJOR").toUpperCase());
        target.setTargetArtifactType(defaultText(request.getTargetArtifactType(), "CODE").toUpperCase());
        target.setDetectorType(defaultText(request.getDetectorType(), "REGEX").toUpperCase());
        target.setConfigJson(validateJson(defaultText(request.getConfigJson(), "{}")));
        target.setBlockOnMatch(request.getBlockOnMatch() == null ? 1 : request.getBlockOnMatch());
        target.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        return target;
    }

    private void applyPolicyTemplates(AiOutputGovernanceRecord record, Map<String, Object> metadata) {
        List<Map<String, Object>> matches = new ArrayList<>();
        String sampledText = sampleArtifactText(record.getArtifactPath());
        String searchableText = String.join("\n",
                nullToEmpty(record.getArtifactSummary()),
                nullToEmpty(record.getMetadataJson()),
                sampledText);
        for (AiOutputGovernancePolicyTemplate policy : listEnabledPolicyTemplates()) {
            if (!policyApplies(policy, record.getArtifactType())) {
                continue;
            }
            boolean matched = matchesPolicy(policy, record, searchableText, sampledText);
            if (!matched) {
                continue;
            }
            matches.add(policyMatch(policy));
            applyPolicyRisk(record, policy);
        }
        if (matches.isEmpty()) {
            return;
        }
        Map<String, Object> enriched = new LinkedHashMap<>();
        if (metadata != null) {
            enriched.putAll(metadata);
        }
        enriched.put("policyMatches", matches);
        record.setMetadataJson(toJson(enriched));
    }

    private boolean policyApplies(AiOutputGovernancePolicyTemplate policy, String artifactType) {
        String target = policy.getTargetArtifactType();
        return "ANY".equalsIgnoreCase(target) || target == null || target.equalsIgnoreCase(artifactType);
    }

    private boolean matchesPolicy(AiOutputGovernancePolicyTemplate policy, AiOutputGovernanceRecord record,
                                  String searchableText, String sampledText) {
        String detector = defaultText(policy.getDetectorType(), "REGEX").toUpperCase();
        Map<String, Object> config = readJsonMap(policy.getConfigJson());
        return switch (detector) {
            case "KEYWORD" -> containsKeyword(searchableText, config);
            case "MISSING_TEST" -> "CODE".equals(record.getArtifactType()) && !hasTestArtifact(record.getArtifactPath(), config);
            case "HIGH_RISK" -> isHighRisk(record, config);
            default -> matchesRegex(searchableText, config);
        };
    }

    private boolean matchesRegex(String text, Map<String, Object> config) {
        Object pattern = config.get("pattern");
        if (pattern == null) {
            return false;
        }
        try {
            return Pattern.compile(String.valueOf(pattern)).matcher(text).find();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean containsKeyword(String text, Map<String, Object> config) {
        Object keywords = config.get("keywords");
        if (!(keywords instanceof List<?> list)) {
            return false;
        }
        return list.stream().map(String::valueOf).anyMatch(text::contains);
    }

    private boolean hasTestArtifact(String artifactPath, Map<String, Object> config) {
        if (!hasText(artifactPath)) {
            return false;
        }
        List<String> keywords = keywordList(config.get("testPathKeywords"));
        Path root = Path.of(artifactPath);
        if (!Files.exists(root)) {
            return false;
        }
        try (Stream<Path> stream = Files.walk(root, 8)) {
            return stream.filter(Files::isRegularFile)
                    .map(path -> path.toString().toLowerCase())
                    .anyMatch(path -> keywords.stream().anyMatch(path::contains));
        } catch (IOException e) {
            return false;
        }
    }

    private boolean isHighRisk(AiOutputGovernanceRecord record, Map<String, Object> config) {
        int threshold = toInt(config.get("riskScoreMin"), 80);
        return RISK_HIGH.equals(record.getRiskLevel()) || (record.getRiskScore() != null && record.getRiskScore() >= threshold);
    }

    private void applyPolicyRisk(AiOutputGovernanceRecord record, AiOutputGovernancePolicyTemplate policy) {
        String severity = defaultText(policy.getSeverity(), "MAJOR").toUpperCase();
        if ("BLOCKER".equals(severity) || "CRITICAL".equals(severity)) {
            record.setRiskLevel(RISK_HIGH);
            record.setRiskScore(Math.max(nullToZero(record.getRiskScore()), 90));
        } else if ("MAJOR".equals(severity)) {
            record.setRiskLevel(RISK_MEDIUM);
            record.setRiskScore(Math.max(nullToZero(record.getRiskScore()), 60));
        }
        if (policy.getBlockOnMatch() != null && policy.getBlockOnMatch() == 1) {
            record.setGovernanceStatus(STATUS_BLOCKED);
        }
    }

    private Map<String, Object> policyMatch(AiOutputGovernancePolicyTemplate policy) {
        Map<String, Object> match = new LinkedHashMap<>();
        match.put("policyCode", policy.getPolicyCode());
        match.put("policyName", policy.getPolicyName());
        match.put("category", policy.getCategory());
        match.put("severity", policy.getSeverity());
        match.put("blockOnMatch", policy.getBlockOnMatch());
        return match;
    }

    private String sampleArtifactText(String artifactPath) {
        if (!hasText(artifactPath)) {
            return "";
        }
        Path root = Path.of(artifactPath);
        if (!Files.exists(root)) {
            return "";
        }
        if (Files.isRegularFile(root)) {
            return readSmallFile(root);
        }
        StringBuilder builder = new StringBuilder();
        try (Stream<Path> stream = Files.walk(root, 6)) {
            stream.filter(Files::isRegularFile)
                    .limit(30)
                    .forEach(path -> {
                        builder.append(path).append('\n');
                        builder.append(readSmallFile(path)).append('\n');
                    });
        } catch (IOException ignored) {
            return "";
        }
        return limit(builder.toString(), 80_000);
    }

    private String readSmallFile(Path path) {
        try {
            if (Files.size(path) > 256_000) {
                return "";
            }
            return Files.readString(path);
        } catch (IOException ignored) {
            return "";
        }
    }

    private AiOutputGovernanceRecord baseRecord(AutomationPipeline pipeline, AutomationStageRun stage) {
        AiOutputGovernanceRecord record = new AiOutputGovernanceRecord();
        record.setRecordCode("AIOG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        record.setSourceType(SOURCE_AUTOMATION_PIPELINE);
        record.setPipelineId(pipeline == null ? null : pipeline.getId());
        record.setStageRunId(stage == null ? null : stage.getId());
        record.setStageKey(stage == null ? null : stage.getStageKey());
        LocalDateTime now = LocalDateTime.now();
        record.setCreateTime(now);
        record.setUpdateTime(now);
        return record;
    }

    private void applyDefaultRisk(AiOutputGovernanceRecord record, String artifactType) {
        if ("CODE".equals(artifactType)) {
            record.setRiskLevel(RISK_MEDIUM);
            record.setRiskScore(55);
            return;
        }
        record.setRiskLevel(RISK_LOW);
        record.setRiskScore(25);
    }

    private void applyQualityRisk(AiOutputGovernanceRecord record, AutomationCodeQualityRun run,
                                  int issueCount, boolean passed) {
        int score = run == null || run.getOverallScore() == null ? 0 : run.getOverallScore();
        if (!passed || score < 70) {
            record.setRiskLevel(RISK_HIGH);
            record.setRiskScore(Math.max(80, 100 - score));
        } else if (score < 85 || issueCount > 0) {
            record.setRiskLevel(RISK_MEDIUM);
            record.setRiskScore(Math.max(45, 100 - score));
        } else {
            record.setRiskLevel(RISK_LOW);
            record.setRiskScore(Math.max(10, 100 - score));
        }
    }

    private String buildPolicySnapshot(AutomationPipeline pipeline) {
        if (pipeline == null) {
            return "{}";
        }
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("codeQualityEnabled", pipeline.getCodeQualityEnabled());
        policy.put("codeQualityStandardId", pipeline.getCodeQualityStandardId());
        policy.put("qualityModelCode", pipeline.getQualityModelCode());
        policy.put("gate", pipeline.getCodeQualityGateSnapshot());
        return toJson(policy);
    }

    private String toJson(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private Map<String, Object> readJsonMap(String json) {
        try {
            return objectMapper.readValue(defaultText(json, "{}"), new TypeReference<>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String validateJson(String json) {
        try {
            objectMapper.readTree(json);
            return json;
        } catch (Exception e) {
            throw new IllegalArgumentException("configJson must be valid JSON");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String required(String value, String field) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private String defaultText(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private int toInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private List<String> keywordList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(item -> String.valueOf(item).toLowerCase()).toList();
        }
        return List.of("test", "spec", "__tests__");
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
