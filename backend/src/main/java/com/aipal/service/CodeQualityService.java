package com.aipal.service;

import com.aipal.dto.CodeQualityRuleRequest;
import com.aipal.dto.CodeQualityRuleResponse;
import com.aipal.dto.CodeQualityStandardRequest;
import com.aipal.dto.CodeQualityStandardResponse;
import com.aipal.entity.AiModel;
import com.aipal.entity.AutomationCodeQualityIssue;
import com.aipal.entity.AutomationCodeQualityRun;
import com.aipal.entity.AutomationPipeline;
import com.aipal.entity.AutomationStageRun;
import com.aipal.entity.CodeQualityRule;
import com.aipal.entity.CodeQualityStandard;
import com.aipal.mapper.AutomationCodeQualityIssueMapper;
import com.aipal.mapper.AutomationCodeQualityRunMapper;
import com.aipal.mapper.CodeQualityRuleMapper;
import com.aipal.mapper.CodeQualityStandardMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CodeQualityService {
    private static final int STATUS_DISABLED = 0;
    private static final int STATUS_ENABLED = 1;
    private static final int MAX_REVIEW_FILE_COUNT = 30;
    private static final int MAX_REVIEW_CHARS = 120_000;
    private static final int DEFAULT_MAX_TOKENS = 4096;

    private final CodeQualityStandardMapper standardMapper;
    private final CodeQualityRuleMapper ruleMapper;
    private final AutomationCodeQualityRunMapper runMapper;
    private final AutomationCodeQualityIssueMapper issueMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Page<CodeQualityStandardResponse> listStandards(int pageNum, int pageSize, Integer status) {
        Page<CodeQualityStandard> page = standardMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<CodeQualityStandard>()
                        .eq(status != null, CodeQualityStandard::getStatus, status)
                        .orderByDesc(CodeQualityStandard::getCreateTime));
        Page<CodeQualityStandardResponse> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toResponse).toList());
        return result;
    }

    public List<CodeQualityStandardResponse> listEnabledStandards() {
        return standardMapper.selectList(new LambdaQueryWrapper<CodeQualityStandard>()
                        .eq(CodeQualityStandard::getStatus, STATUS_ENABLED)
                        .orderByDesc(CodeQualityStandard::getCreateTime))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public CodeQualityStandardResponse getStandard(Long id) {
        return toResponse(requireStandard(id));
    }

    @Transactional
    public CodeQualityStandardResponse createStandard(CodeQualityStandardRequest request) {
        CodeQualityStandardRequest normalized = request == null ? new CodeQualityStandardRequest() : request;
        validateStandard(normalized);
        LocalDateTime now = LocalDateTime.now();
        CodeQualityStandard standard = new CodeQualityStandard();
        fillStandard(standard, normalized);
        standard.setCreateTime(now);
        standard.setUpdateTime(now);
        standard.setIsDeleted(0);
        standardMapper.insert(standard);
        replaceRules(standard.getId(), normalized.getRules());
        return toResponse(requireStandard(standard.getId()));
    }

    @Transactional
    public CodeQualityStandardResponse updateStandard(Long id, CodeQualityStandardRequest request) {
        CodeQualityStandard standard = requireStandard(id);
        CodeQualityStandardRequest normalized = request == null ? new CodeQualityStandardRequest() : request;
        validateStandard(normalized);
        fillStandard(standard, normalized);
        standard.setUpdateTime(LocalDateTime.now());
        standardMapper.updateById(standard);
        replaceRules(standard.getId(), normalized.getRules());
        return toResponse(requireStandard(id));
    }

    public boolean deleteStandard(Long id) {
        return standardMapper.deleteById(id) > 0;
    }

    public StandardSnapshot requireEnabledStandardSnapshot(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("codeQualityStandardId is required when code quality is enabled");
        }
        CodeQualityStandard standard = requireStandard(id);
        if (standard.getStatus() == null || standard.getStatus() != STATUS_ENABLED) {
            throw new IllegalArgumentException("Code quality standard is disabled: " + id);
        }
        CodeQualityStandardResponse response = toResponse(standard);
        try {
            String standardSnapshot = objectMapper.writeValueAsString(response);
            String gateSnapshot = normalizeGateConfig(response.getGateConfig());
            return new StandardSnapshot(standard.getId(), standardSnapshot, gateSnapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize code quality standard: " + e.getMessage(), e);
        }
    }

    public List<AutomationCodeQualityRun> listRuns(Long pipelineId) {
        return runMapper.selectList(new LambdaQueryWrapper<AutomationCodeQualityRun>()
                .eq(AutomationCodeQualityRun::getPipelineId, pipelineId)
                .orderByDesc(AutomationCodeQualityRun::getCreateTime));
    }

    public List<AutomationCodeQualityIssue> listIssues(Long runId) {
        return issueMapper.selectList(new LambdaQueryWrapper<AutomationCodeQualityIssue>()
                .eq(AutomationCodeQualityIssue::getRunId, runId)
                .orderByAsc(AutomationCodeQualityIssue::getId));
    }

    @Transactional
    public EvaluationOutcome evaluate(AutomationPipeline pipeline, AutomationStageRun qualityStage,
                                      AutomationStageRun codeStage, AiModel model) {
        LocalDateTime start = LocalDateTime.now();
        AutomationCodeQualityRun run = new AutomationCodeQualityRun();
        run.setPipelineId(pipeline.getId());
        run.setStageRunId(qualityStage.getId());
        run.setCodeStageRunId(codeStage.getId());
        run.setStandardId(pipeline.getCodeQualityStandardId());
        run.setStandardSnapshot(pipeline.getCodeQualityStandardSnapshot());
        run.setGateSnapshot(pipeline.getCodeQualityGateSnapshot());
        run.setModelCode(model == null ? null : model.getModelCode());
        run.setStatus("RUNNING");
        run.setPassed(0);
        run.setStartTime(start);
        run.setCreateTime(start);
        run.setUpdateTime(start);
        runMapper.insert(run);

        try {
            if (model == null || isBlank(model.getEndpoint()) || isBlank(model.getApiKey())) {
                throw new IllegalStateException("Code quality model is not configured");
            }
            String codeContext = readGeneratedCodeContext(codeStage);
            if (codeContext.isBlank()) {
                throw new IllegalStateException("Generated code is empty");
            }
            String systemPrompt = "You are a strict senior code reviewer. Return pure JSON only.";
            String userPrompt = buildEvaluationPrompt(pipeline, run, codeContext);
            ModelCallResult modelResult = callModel(model, systemPrompt, userPrompt);
            JsonNode result = parseModelJson(modelResult.content());
            applyEvaluationResult(run, result, modelResult, start);
            List<AutomationCodeQualityIssue> issues = saveIssues(run, result.path("issues"));
            GateDecision gateDecision = applyGate(run, issues);
            run.setPassed(gateDecision.passed() ? 1 : 0);
            run.setStatus(gateDecision.passed() ? "SUCCESS" : "BLOCKED");
            run.setErrorMessage(gateDecision.passed() ? null : gateDecision.message());
            run.setUpdateTime(LocalDateTime.now());
            runMapper.updateById(run);
            return new EvaluationOutcome(run, issues, gateDecision.passed(), gateDecision.message());
        } catch (Exception e) {
            LocalDateTime now = LocalDateTime.now();
            run.setStatus("FAILED");
            run.setPassed(0);
            run.setErrorMessage(e.getMessage());
            run.setEndTime(now);
            run.setDurationMs((int) Duration.between(start, now).toMillis());
            run.setUpdateTime(now);
            runMapper.updateById(run);
            return new EvaluationOutcome(run, List.of(), false, e.getMessage());
        }
    }

    private void fillStandard(CodeQualityStandard standard, CodeQualityStandardRequest request) {
        standard.setStandardCode(resolveStandardCode(request.getStandardCode()));
        standard.setStandardName(request.getStandardName().trim());
        standard.setDescription(blankToNull(request.getDescription()));
        standard.setLanguage(isBlank(request.getLanguage()) ? "GENERAL" : request.getLanguage().trim().toUpperCase(Locale.ROOT));
        standard.setFramework(blankToNull(request.getFramework()));
        standard.setStatus(request.getStatus() == null ? STATUS_ENABLED : request.getStatus());
        standard.setGateConfig(normalizeGateConfig(request.getGateConfig()));
    }

    private void validateStandard(CodeQualityStandardRequest request) {
        if (isBlank(request.getStandardName())) {
            throw new IllegalArgumentException("standardName is required");
        }
        if (request.getStatus() != null && request.getStatus() != STATUS_ENABLED && request.getStatus() != STATUS_DISABLED) {
            throw new IllegalArgumentException("status must be 0 or 1");
        }
        normalizeGateConfig(request.getGateConfig());
        List<CodeQualityRuleRequest> rules = request.getRules() == null ? List.of() : request.getRules();
        for (CodeQualityRuleRequest rule : rules) {
            boolean enabled = rule.getEnabled() == null || rule.getEnabled();
            if (enabled && isBlank(rule.getTitle())) {
                throw new IllegalArgumentException("Enabled rule title is required");
            }
        }
    }

    private void replaceRules(Long standardId, List<CodeQualityRuleRequest> requests) {
        ruleMapper.delete(new LambdaUpdateWrapper<CodeQualityRule>().eq(CodeQualityRule::getStandardId, standardId));
        LocalDateTime now = LocalDateTime.now();
        List<CodeQualityRuleRequest> rules = requests == null ? List.of() : requests;
        int index = 1;
        for (CodeQualityRuleRequest request : rules) {
            CodeQualityRule rule = new CodeQualityRule();
            rule.setStandardId(standardId);
            rule.setRuleCode(isBlank(request.getRuleCode()) ? "RULE_" + index : request.getRuleCode().trim());
            rule.setCategory(isBlank(request.getCategory()) ? "maintainability" : request.getCategory().trim());
            rule.setSeverity(isBlank(request.getSeverity()) ? "MAJOR" : request.getSeverity().trim().toUpperCase(Locale.ROOT));
            rule.setTitle(request.getTitle().trim());
            rule.setDescription(blankToNull(request.getDescription()));
            rule.setCheckPrompt(blankToNull(request.getCheckPrompt()));
            rule.setEnabled(Boolean.FALSE.equals(request.getEnabled()) ? 0 : 1);
            rule.setCreateTime(now);
            rule.setUpdateTime(now);
            rule.setIsDeleted(0);
            ruleMapper.insert(rule);
            index++;
        }
    }

    private CodeQualityStandard requireStandard(Long id) {
        CodeQualityStandard standard = standardMapper.selectById(id);
        if (standard == null) {
            throw new IllegalArgumentException("Code quality standard does not exist: " + id);
        }
        return standard;
    }

    private CodeQualityStandardResponse toResponse(CodeQualityStandard standard) {
        CodeQualityStandardResponse response = new CodeQualityStandardResponse();
        response.setId(standard.getId());
        response.setStandardCode(standard.getStandardCode());
        response.setStandardName(standard.getStandardName());
        response.setDescription(standard.getDescription());
        response.setLanguage(standard.getLanguage());
        response.setFramework(standard.getFramework());
        response.setStatus(standard.getStatus());
        response.setGateConfig(standard.getGateConfig());
        response.setCreateTime(standard.getCreateTime());
        response.setUpdateTime(standard.getUpdateTime());
        response.setRules(ruleMapper.selectList(new LambdaQueryWrapper<CodeQualityRule>()
                        .eq(CodeQualityRule::getStandardId, standard.getId())
                        .orderByAsc(CodeQualityRule::getId))
                .stream()
                .map(this::toRuleResponse)
                .toList());
        return response;
    }

    private CodeQualityRuleResponse toRuleResponse(CodeQualityRule rule) {
        CodeQualityRuleResponse response = new CodeQualityRuleResponse();
        response.setId(rule.getId());
        response.setStandardId(rule.getStandardId());
        response.setRuleCode(rule.getRuleCode());
        response.setCategory(rule.getCategory());
        response.setSeverity(rule.getSeverity());
        response.setTitle(rule.getTitle());
        response.setDescription(rule.getDescription());
        response.setCheckPrompt(rule.getCheckPrompt());
        response.setEnabled(rule.getEnabled() == null || rule.getEnabled() == 1);
        response.setCreateTime(rule.getCreateTime());
        response.setUpdateTime(rule.getUpdateTime());
        return response;
    }

    private String buildEvaluationPrompt(AutomationPipeline pipeline, AutomationCodeQualityRun run, String codeContext) {
        return """
                Evaluate the generated code according to the code quality standard and gate.
                Project: %s
                Requirement: %s
                Pipeline ID: %s

                Code quality standard snapshot:
                %s

                Gate config:
                %s

                Generated code:
                %s

                Return JSON only with this shape:
                {
                  "overallScore": 0-100,
                  "passed": true,
                  "summary": "short review summary",
                  "metrics": {
                    "security": 0-100,
                    "maintainability": 0-100,
                    "readability": 0-100,
                    "architecture": 0-100,
                    "prdAlignment": 0-100,
                    "testability": 0-100
                  },
                  "issues": [
                    {
                      "ruleCode": "RULE-001",
                      "severity": "BLOCKER|CRITICAL|MAJOR|MINOR|INFO",
                      "category": "security|maintainability|readability|architecture|prdAlignment|testability",
                      "filePath": "relative/path",
                      "lineStart": 1,
                      "lineEnd": 1,
                      "title": "short title",
                      "description": "what is wrong",
                      "suggestion": "how to fix"
                    }
                  ]
                }
                """.formatted(
                nullToEmpty(pipeline.getProjectName()),
                nullToEmpty(pipeline.getRequirementTitle()),
                pipeline.getId(),
                nullToEmpty(run.getStandardSnapshot()),
                nullToEmpty(run.getGateSnapshot()),
                codeContext
        );
    }

    private String readGeneratedCodeContext(AutomationStageRun codeStage) throws IOException {
        if (isBlank(codeStage.getArtifactPath()) || isBlank(codeStage.getArtifactContent())) {
            return "";
        }
        Path root = Paths.get(codeStage.getArtifactPath()).toAbsolutePath().normalize();
        JsonNode filesNode = objectMapper.readTree(codeStage.getArtifactContent()).path("files");
        if (!filesNode.isArray()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int fileCount = 0;
        int charCount = 0;
        for (JsonNode fileNode : filesNode) {
            if (fileCount >= MAX_REVIEW_FILE_COUNT || charCount >= MAX_REVIEW_CHARS) {
                break;
            }
            String pathValue = fileNode.path("path").asText("");
            if (pathValue.isBlank()) {
                continue;
            }
            Path target = root.resolve(pathValue).normalize();
            if (!target.startsWith(root) || !Files.isRegularFile(target)) {
                continue;
            }
            String content = Files.readString(target);
            int remain = MAX_REVIEW_CHARS - charCount;
            if (content.length() > remain) {
                content = content.substring(0, Math.max(0, remain));
            }
            builder.append("\n\n### FILE: ").append(pathValue).append("\n```").append(languageHint(pathValue)).append("\n")
                    .append(content).append("\n```");
            charCount += content.length();
            fileCount++;
        }
        return builder.toString();
    }

    private ModelCallResult callModel(AiModel model, String systemPrompt, String userPrompt) throws IOException {
        String endpoint = model.getEndpoint().endsWith("/")
                ? model.getEndpoint() + "chat/completions"
                : model.getEndpoint() + "/chat/completions";
        Map<String, Object> body = Map.of(
                "model", model.getModelCode(),
                "temperature", model.getDefaultTemperature() == null ? BigDecimal.valueOf(0.2) : model.getDefaultTemperature(),
                "max_tokens", model.getMaxTokens() == null ? DEFAULT_MAX_TOKENS : model.getMaxTokens(),
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

    private ModelCallResult extractModelCallResult(String response, String systemPrompt, String userPrompt) throws IOException {
        JsonNode root = objectMapper.readTree(response);
        String content = root.path("choices").isArray() && root.path("choices").size() > 0
                ? root.path("choices").get(0).path("message").path("content").asText("")
                : "";
        JsonNode usage = root.path("usage");
        int inputTokens = firstInt(usage, "prompt_tokens", "input_tokens");
        int outputTokens = firstInt(usage, "completion_tokens", "output_tokens");
        int totalTokens = firstInt(usage, "total_tokens");
        if (totalTokens == 0) {
            totalTokens = inputTokens + outputTokens;
        }
        if (totalTokens == 0) {
            totalTokens = estimateTokens(systemPrompt) + estimateTokens(userPrompt) + estimateTokens(content);
            inputTokens = estimateTokens(systemPrompt) + estimateTokens(userPrompt);
            outputTokens = estimateTokens(content);
        }
        return new ModelCallResult(content, inputTokens, outputTokens, totalTokens);
    }

    private JsonNode parseModelJson(String content) throws IOException {
        if (isBlank(content)) {
            throw new IllegalStateException("Model returned empty evaluation result");
        }
        String cleaned = content.trim();
        if (cleaned.startsWith("```")) {
            int firstLine = cleaned.indexOf('\n');
            int lastFence = cleaned.lastIndexOf("```");
            if (firstLine >= 0 && lastFence > firstLine) {
                cleaned = cleaned.substring(firstLine + 1, lastFence).trim();
            }
        }
        int objectStart = cleaned.indexOf('{');
        int objectEnd = cleaned.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            cleaned = cleaned.substring(objectStart, objectEnd + 1);
        }
        return objectMapper.readTree(cleaned);
    }

    private void applyEvaluationResult(AutomationCodeQualityRun run, JsonNode result,
                                       ModelCallResult modelResult, LocalDateTime start) throws JsonProcessingException {
        LocalDateTime now = LocalDateTime.now();
        run.setOverallScore(clampScore(result.path("overallScore").asInt(0)));
        run.setSummary(result.path("summary").asText(""));
        run.setMetricsJson(result.path("metrics").isMissingNode() ? "{}" : objectMapper.writeValueAsString(result.path("metrics")));
        run.setRawResult(objectMapper.writeValueAsString(result));
        run.setInputTokens(modelResult.inputTokens());
        run.setOutputTokens(modelResult.outputTokens());
        run.setTotalTokens(modelResult.totalTokens());
        run.setEndTime(now);
        run.setDurationMs((int) Duration.between(start, now).toMillis());
    }

    private List<AutomationCodeQualityIssue> saveIssues(AutomationCodeQualityRun run, JsonNode issuesNode) {
        issueMapper.delete(new LambdaUpdateWrapper<AutomationCodeQualityIssue>()
                .eq(AutomationCodeQualityIssue::getRunId, run.getId()));
        if (!issuesNode.isArray()) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        List<AutomationCodeQualityIssue> issues = new ArrayList<>();
        for (JsonNode node : issuesNode) {
            AutomationCodeQualityIssue issue = new AutomationCodeQualityIssue();
            issue.setRunId(run.getId());
            issue.setPipelineId(run.getPipelineId());
            issue.setStageRunId(run.getStageRunId());
            issue.setRuleCode(blankToNull(node.path("ruleCode").asText("")));
            issue.setSeverity(normalizeSeverity(node.path("severity").asText("MAJOR")));
            issue.setCategory(blankToNull(node.path("category").asText("")));
            issue.setFilePath(blankToNull(node.path("filePath").asText("")));
            issue.setLineStart(node.path("lineStart").isNumber() ? node.path("lineStart").asInt() : null);
            issue.setLineEnd(node.path("lineEnd").isNumber() ? node.path("lineEnd").asInt() : issue.getLineStart());
            issue.setTitle(node.path("title").asText("Code quality issue"));
            issue.setDescription(blankToNull(node.path("description").asText("")));
            issue.setSuggestion(blankToNull(node.path("suggestion").asText("")));
            issue.setCreateTime(now);
            issueMapper.insert(issue);
            issues.add(issue);
        }
        return issues;
    }

    private GateDecision applyGate(AutomationCodeQualityRun run, List<AutomationCodeQualityIssue> issues) {
        GateConfig gate = readGate(run.getGateSnapshot());
        int blocker = countSeverity(issues, "BLOCKER");
        int critical = countSeverity(issues, "CRITICAL");
        int major = countSeverity(issues, "MAJOR");
        int security = metricValue(run.getMetricsJson(), "security");
        List<String> failures = new ArrayList<>();
        if (run.getOverallScore() == null || run.getOverallScore() < gate.overallScoreMin()) {
            failures.add("overall score is lower than " + gate.overallScoreMin());
        }
        if (blocker > gate.blockerMax()) {
            failures.add("BLOCKER issues exceed " + gate.blockerMax());
        }
        if (critical > gate.criticalMax()) {
            failures.add("CRITICAL issues exceed " + gate.criticalMax());
        }
        if (major > gate.majorMax()) {
            failures.add("MAJOR issues exceed " + gate.majorMax());
        }
        if (security < gate.securityScoreMin()) {
            failures.add("security score is lower than " + gate.securityScoreMin());
        }
        if (failures.isEmpty()) {
            return new GateDecision(true, null);
        }
        return new GateDecision(false, String.join("; ", failures));
    }

    private GateConfig readGate(String gateJson) {
        try {
            Map<String, Object> map = objectMapper.readValue(normalizeGateConfig(gateJson),
                    new TypeReference<Map<String, Object>>() {});
            return new GateConfig(
                    toInt(map.get("overallScoreMin"), 80),
                    toInt(map.get("blockerMax"), 0),
                    toInt(map.get("criticalMax"), 0),
                    toInt(map.get("majorMax"), 5),
                    toInt(map.get("securityScoreMin"), 0)
            );
        } catch (Exception e) {
            return new GateConfig(80, 0, 0, 5, 0);
        }
    }

    private int metricValue(String metricsJson, String key) {
        try {
            return objectMapper.readTree(metricsJson == null ? "{}" : metricsJson).path(key).asInt(0);
        } catch (Exception e) {
            return 0;
        }
    }

    private String normalizeGateConfig(String value) {
        if (isBlank(value)) {
            return """
                    {"overallScoreMin":80,"blockerMax":0,"criticalMax":0,"majorMax":5,"securityScoreMin":0}
                    """.trim();
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("gateConfig must be valid JSON");
        }
    }

    private String resolveStandardCode(String value) {
        if (isBlank(value)) {
            return "CQS_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        }
        return value.trim();
    }

    private String languageHint(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".java")) return "java";
        if (lower.endsWith(".vue")) return "vue";
        if (lower.endsWith(".js")) return "javascript";
        if (lower.endsWith(".ts")) return "typescript";
        if (lower.endsWith(".xml")) return "xml";
        if (lower.endsWith(".sql")) return "sql";
        return "";
    }

    private String normalizeSeverity(String severity) {
        String value = isBlank(severity) ? "MAJOR" : severity.trim().toUpperCase(Locale.ROOT);
        return List.of("BLOCKER", "CRITICAL", "MAJOR", "MINOR", "INFO").contains(value) ? value : "MAJOR";
    }

    private int countSeverity(List<AutomationCodeQualityIssue> issues, String severity) {
        return (int) issues.stream().filter(issue -> severity.equalsIgnoreCase(issue.getSeverity())).count();
    }

    private int clampScore(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private int firstInt(JsonNode node, String... keys) {
        for (String key : keys) {
            if (node.has(key) && node.get(key).isNumber()) {
                return node.get(key).asInt();
            }
        }
        return 0;
    }

    private int estimateTokens(String value) {
        return value == null ? 0 : Math.max(1, value.length() / 4);
    }

    private int toInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record StandardSnapshot(Long standardId, String standardSnapshot, String gateSnapshot) {
    }

    public record EvaluationOutcome(AutomationCodeQualityRun run, List<AutomationCodeQualityIssue> issues,
                                    boolean passed, String message) {
    }

    private record GateDecision(boolean passed, String message) {
    }

    private record GateConfig(int overallScoreMin, int blockerMax, int criticalMax, int majorMax, int securityScoreMin) {
    }

    private record ModelCallResult(String content, int inputTokens, int outputTokens, int totalTokens) {
    }
}
