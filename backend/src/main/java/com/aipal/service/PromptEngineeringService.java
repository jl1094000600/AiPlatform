package com.aipal.service;

import com.aipal.dto.PromptEvaluateRequest;
import com.aipal.dto.PromptOptimizeRequest;
import com.aipal.entity.AiAgent;
import com.aipal.entity.AiModel;
import com.aipal.entity.PromptEngineeringEvalResult;
import com.aipal.entity.PromptEngineeringEvalRun;
import com.aipal.entity.PromptEngineeringOptimizeRun;
import com.aipal.entity.PromptEngineeringPrompt;
import com.aipal.entity.PromptEngineeringTestCase;
import com.aipal.entity.PromptEngineeringVersion;
import com.aipal.mapper.AiAgentMapper;
import com.aipal.mapper.AiModelMapper;
import com.aipal.mapper.PromptEngineeringEvalResultMapper;
import com.aipal.mapper.PromptEngineeringEvalRunMapper;
import com.aipal.mapper.PromptEngineeringOptimizeRunMapper;
import com.aipal.mapper.PromptEngineeringPromptMapper;
import com.aipal.mapper.PromptEngineeringTestCaseMapper;
import com.aipal.mapper.PromptEngineeringVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PromptEngineeringService {
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_ARCHIVED = "ARCHIVED";
    private static final String RUN_SUCCESS = "SUCCESS";
    private static final String RUN_FAILED = "FAILED";

    private final PromptEngineeringPromptMapper promptMapper;
    private final PromptEngineeringVersionMapper versionMapper;
    private final PromptEngineeringTestCaseMapper testCaseMapper;
    private final PromptEngineeringEvalRunMapper evalRunMapper;
    private final PromptEngineeringEvalResultMapper evalResultMapper;
    private final PromptEngineeringOptimizeRunMapper optimizeRunMapper;
    private final AiAgentMapper agentMapper;
    private final AiModelMapper modelMapper;
    private final AgentRuntimeConfigService runtimeConfigService;
    private final ChatModelService chatModelService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Page<PromptEngineeringPrompt> listPrompts(int pageNum, int pageSize, Long agentId,
                                                     String projectKey, Integer status, String keyword) {
        LambdaQueryWrapper<PromptEngineeringPrompt> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(agentId != null, PromptEngineeringPrompt::getAgentId, agentId);
        wrapper.eq(hasText(projectKey), PromptEngineeringPrompt::getProjectKey, projectKey);
        wrapper.eq(status != null, PromptEngineeringPrompt::getStatus, status);
        if (hasText(keyword)) {
            wrapper.and(w -> w.like(PromptEngineeringPrompt::getPromptName, keyword)
                    .or().like(PromptEngineeringPrompt::getPromptCode, keyword));
        }
        wrapper.orderByDesc(PromptEngineeringPrompt::getUpdateTime);
        Page<PromptEngineeringPrompt> page = promptMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        page.getRecords().forEach(this::enrichLatestScore);
        return page;
    }

    public PromptEngineeringPrompt getPrompt(Long id) {
        PromptEngineeringPrompt prompt = requirePrompt(id);
        enrichLatestScore(prompt);
        return prompt;
    }

    @Transactional
    public PromptEngineeringPrompt createPrompt(PromptEngineeringPrompt request) {
        if (request == null || request.getAgentId() == null) {
            throw new IllegalArgumentException("agentId is required");
        }
        AiAgent agent = requireAgent(request.getAgentId());
        LocalDateTime now = LocalDateTime.now();
        PromptEngineeringPrompt prompt = new PromptEngineeringPrompt();
        prompt.setPromptCode(hasText(request.getPromptCode()) ? request.getPromptCode().trim() : "PROMPT_" + shortId());
        prompt.setPromptName(required(request.getPromptName(), "promptName"));
        prompt.setDescription(request.getDescription());
        prompt.setAgentId(agent.getId());
        prompt.setAgentCode(agent.getAgentCode());
        prompt.setAgentName(agent.getAgentName());
        prompt.setProjectName(request.getProjectName());
        prompt.setProjectKey(request.getProjectKey());
        prompt.setPipelineId(request.getPipelineId());
        prompt.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        prompt.setCreateTime(now);
        prompt.setUpdateTime(now);
        promptMapper.insert(prompt);
        return prompt;
    }

    @Transactional
    public PromptEngineeringPrompt updatePrompt(Long id, PromptEngineeringPrompt request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        PromptEngineeringPrompt prompt = requirePrompt(id);
        if (request.getAgentId() != null && !request.getAgentId().equals(prompt.getAgentId())) {
            AiAgent agent = requireAgent(request.getAgentId());
            prompt.setAgentId(agent.getId());
            prompt.setAgentCode(agent.getAgentCode());
            prompt.setAgentName(agent.getAgentName());
        }
        if (hasText(request.getPromptName())) prompt.setPromptName(request.getPromptName().trim());
        prompt.setDescription(request.getDescription());
        prompt.setProjectName(request.getProjectName());
        prompt.setProjectKey(request.getProjectKey());
        prompt.setPipelineId(request.getPipelineId());
        if (request.getStatus() != null) prompt.setStatus(request.getStatus());
        prompt.setUpdateTime(LocalDateTime.now());
        promptMapper.updateById(prompt);
        return prompt;
    }

    @Transactional
    public boolean deletePrompt(Long id) {
        PromptEngineeringPrompt prompt = requirePrompt(id);
        if (prompt.getPublishedVersionId() != null && prompt.getStatus() != null && prompt.getStatus() == 1) {
            throw new IllegalStateException("已发布且启用中的提示词不能删除，请先停用或发布其他版本");
        }
        return promptMapper.deleteById(id) > 0;
    }

    public List<PromptEngineeringVersion> listVersions(Long promptId) {
        return versionMapper.selectList(new LambdaQueryWrapper<PromptEngineeringVersion>()
                .eq(PromptEngineeringVersion::getPromptId, promptId)
                .orderByDesc(PromptEngineeringVersion::getVersionNo));
    }

    @Transactional
    public PromptEngineeringVersion createVersion(Long promptId, PromptEngineeringVersion request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        PromptEngineeringPrompt prompt = requirePrompt(promptId);
        Integer maxVersion = versionMapper.selectList(new LambdaQueryWrapper<PromptEngineeringVersion>()
                        .eq(PromptEngineeringVersion::getPromptId, promptId))
                .stream()
                .map(PromptEngineeringVersion::getVersionNo)
                .filter(value -> value != null)
                .max(Integer::compareTo)
                .orElse(0);
        LocalDateTime now = LocalDateTime.now();
        PromptEngineeringVersion version = new PromptEngineeringVersion();
        version.setPromptId(promptId);
        version.setVersionNo(maxVersion + 1);
        version.setVersionName(hasText(request.getVersionName()) ? request.getVersionName() : "v" + (maxVersion + 1));
        version.setSystemPrompt(request.getSystemPrompt());
        version.setUserPromptTemplate(request.getUserPromptTemplate());
        version.setVariableDefinitions(request.getVariableDefinitions());
        version.setChangelog(request.getChangelog());
        version.setStatus(STATUS_DRAFT);
        version.setCreateTime(now);
        version.setUpdateTime(now);
        versionMapper.insert(version);
        prompt.setLatestVersionId(version.getId());
        prompt.setUpdateTime(now);
        promptMapper.updateById(prompt);
        return version;
    }

    public List<PromptEngineeringTestCase> listTestCases(Long promptId) {
        return testCaseMapper.selectList(new LambdaQueryWrapper<PromptEngineeringTestCase>()
                .eq(PromptEngineeringTestCase::getPromptId, promptId)
                .orderByDesc(PromptEngineeringTestCase::getCreateTime));
    }

    @Transactional
    public PromptEngineeringTestCase createTestCase(Long promptId, PromptEngineeringTestCase request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        requirePrompt(promptId);
        validateJson(request.getInputJson(), "inputJson");
        LocalDateTime now = LocalDateTime.now();
        PromptEngineeringTestCase testCase = new PromptEngineeringTestCase();
        testCase.setPromptId(promptId);
        testCase.setCaseName(required(request.getCaseName(), "caseName"));
        testCase.setInputJson(defaultText(request.getInputJson(), "{}"));
        testCase.setExpectedOutput(request.getExpectedOutput());
        testCase.setScoringRule(request.getScoringRule());
        testCase.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
        testCase.setCreateTime(now);
        testCase.setUpdateTime(now);
        testCaseMapper.insert(testCase);
        return testCase;
    }

    @Transactional
    public PromptEngineeringTestCase updateTestCase(Long promptId, Long caseId, PromptEngineeringTestCase request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        requirePrompt(promptId);
        PromptEngineeringTestCase testCase = requireTestCase(caseId);
        validateJson(request.getInputJson(), "inputJson");
        testCase.setCaseName(required(request.getCaseName(), "caseName"));
        testCase.setInputJson(defaultText(request.getInputJson(), "{}"));
        testCase.setExpectedOutput(request.getExpectedOutput());
        testCase.setScoringRule(request.getScoringRule());
        testCase.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
        testCase.setUpdateTime(LocalDateTime.now());
        testCaseMapper.updateById(testCase);
        return testCase;
    }

    public boolean deleteTestCase(Long promptId, Long caseId) {
        requirePrompt(promptId);
        return testCaseMapper.deleteById(caseId) > 0;
    }

    @Transactional
    public PromptEngineeringEvalRun evaluate(Long versionId, PromptEvaluateRequest request) {
        PromptEngineeringVersion version = requireVersion(versionId);
        PromptEngineeringPrompt prompt = requirePrompt(version.getPromptId());
        List<PromptEngineeringTestCase> cases = testCaseMapper.selectList(new LambdaQueryWrapper<PromptEngineeringTestCase>()
                .eq(PromptEngineeringTestCase::getPromptId, prompt.getId())
                .eq(PromptEngineeringTestCase::getEnabled, 1)
                .orderByAsc(PromptEngineeringTestCase::getId));
        if (cases.isEmpty()) {
            throw new IllegalArgumentException("请先维护至少一个启用的测试样例");
        }
        AiModel model = resolveModel(request == null ? null : request.getModelId(), prompt.getAgentId());
        LocalDateTime start = LocalDateTime.now();
        PromptEngineeringEvalRun run = new PromptEngineeringEvalRun();
        run.setRunCode("PER_" + shortId());
        run.setPromptId(prompt.getId());
        run.setVersionId(version.getId());
        run.setModelId(model == null ? null : model.getId());
        run.setModelCode(model == null ? null : model.getModelCode());
        run.setStatus("RUNNING");
        run.setStartTime(start);
        run.setCreateTime(start);
        run.setUpdateTime(start);
        evalRunMapper.insert(run);

        int totalScore = 0;
        int totalInputTokens = 0;
        int totalOutputTokens = 0;
        for (PromptEngineeringTestCase testCase : cases) {
            LocalDateTime caseStart = LocalDateTime.now();
            PromptEngineeringEvalResult result = evaluateCase(run, version, testCase, model, caseStart);
            totalScore += result.getScore() == null ? 0 : result.getScore();
            totalInputTokens += estimateTokens(version.getSystemPrompt()) + estimateTokens(testCase.getInputJson());
            totalOutputTokens += estimateTokens(result.getPredictedOutput());
            evalResultMapper.insert(result);
        }
        int overallScore = Math.round((float) totalScore / cases.size());
        Map<String, Object> metrics = metrics(overallScore);
        LocalDateTime end = LocalDateTime.now();
        run.setOverallScore(overallScore);
        run.setMetricsJson(toJson(metrics));
        run.setSummary("本次评测完成，综合得分 " + overallScore + "，共评测 " + cases.size() + " 个样例。");
        run.setRawResult(toJson(Map.of("caseCount", cases.size(), "metrics", metrics)));
        run.setInputTokens(totalInputTokens);
        run.setOutputTokens(totalOutputTokens);
        run.setTotalTokens(totalInputTokens + totalOutputTokens);
        run.setStatus(RUN_SUCCESS);
        run.setEndTime(end);
        run.setDurationMs((int) Duration.between(start, end).toMillis());
        run.setUpdateTime(end);
        evalRunMapper.updateById(run);

        version.setLatestScore(overallScore);
        version.setUpdateTime(end);
        versionMapper.updateById(version);
        prompt.setLatestVersionId(version.getId());
        prompt.setUpdateTime(end);
        promptMapper.updateById(prompt);
        return run;
    }

    public List<PromptEngineeringEvalResult> listEvalResults(Long runId) {
        return evalResultMapper.selectList(new LambdaQueryWrapper<PromptEngineeringEvalResult>()
                .eq(PromptEngineeringEvalResult::getRunId, runId)
                .orderByAsc(PromptEngineeringEvalResult::getId));
    }

    public List<PromptEngineeringEvalRun> listEvalRuns(Long versionId) {
        return evalRunMapper.selectList(new LambdaQueryWrapper<PromptEngineeringEvalRun>()
                .eq(PromptEngineeringEvalRun::getVersionId, versionId)
                .orderByDesc(PromptEngineeringEvalRun::getCreateTime));
    }

    @Transactional
    public PromptEngineeringOptimizeRun optimize(Long versionId, PromptOptimizeRequest request) {
        PromptEngineeringVersion source = requireVersion(versionId);
        PromptEngineeringPrompt prompt = requirePrompt(source.getPromptId());
        AiModel model = resolveModel(request == null ? null : request.getModelId(), prompt.getAgentId());
        String goal = defaultText(request == null ? null : request.getOptimizeGoal(), "提升任务符合度、稳定性和输出结构清晰度");
        LocalDateTime start = LocalDateTime.now();
        PromptEngineeringOptimizeRun run = new PromptEngineeringOptimizeRun();
        run.setPromptId(prompt.getId());
        run.setSourceVersionId(source.getId());
        run.setModelId(model == null ? null : model.getId());
        run.setModelCode(model == null ? null : model.getModelCode());
        run.setOptimizeGoal(goal);
        run.setStatus("RUNNING");
        run.setStartTime(start);
        run.setCreateTime(start);
        run.setUpdateTime(start);
        optimizeRunMapper.insert(run);

        String raw = optimizeWithModel(source, prompt, model, goal);
        PromptEngineeringVersion target = createVersion(prompt.getId(), optimizedVersionRequest(source, raw, goal));
        LocalDateTime end = LocalDateTime.now();
        run.setTargetVersionId(target.getId());
        run.setOptimizationSummary(extractOptimizationSummary(raw));
        run.setRawResult(raw);
        run.setStatus(RUN_SUCCESS);
        run.setInputTokens(estimateTokens(source.getSystemPrompt()) + estimateTokens(source.getUserPromptTemplate()) + estimateTokens(goal));
        run.setOutputTokens(estimateTokens(raw));
        run.setTotalTokens((run.getInputTokens() == null ? 0 : run.getInputTokens()) + (run.getOutputTokens() == null ? 0 : run.getOutputTokens()));
        run.setEndTime(end);
        run.setDurationMs((int) Duration.between(start, end).toMillis());
        run.setUpdateTime(end);
        optimizeRunMapper.updateById(run);
        return run;
    }

    public List<PromptEngineeringOptimizeRun> listOptimizeRuns(Long versionId) {
        return optimizeRunMapper.selectList(new LambdaQueryWrapper<PromptEngineeringOptimizeRun>()
                .eq(PromptEngineeringOptimizeRun::getSourceVersionId, versionId)
                .orderByDesc(PromptEngineeringOptimizeRun::getCreateTime));
    }

    @Transactional
    public PromptEngineeringVersion publish(Long versionId) {
        PromptEngineeringVersion version = requireVersion(versionId);
        PromptEngineeringPrompt prompt = requirePrompt(version.getPromptId());
        versionMapper.selectList(new LambdaQueryWrapper<PromptEngineeringVersion>()
                .eq(PromptEngineeringVersion::getPromptId, prompt.getId())
                .eq(PromptEngineeringVersion::getStatus, STATUS_PUBLISHED))
                .forEach(item -> {
                    item.setStatus(STATUS_ARCHIVED);
                    item.setUpdateTime(LocalDateTime.now());
                    versionMapper.updateById(item);
                });
        LocalDateTime now = LocalDateTime.now();
        version.setStatus(STATUS_PUBLISHED);
        version.setPublishTime(now);
        version.setUpdateTime(now);
        versionMapper.updateById(version);
        prompt.setPublishedVersionId(version.getId());
        prompt.setLatestVersionId(version.getId());
        prompt.setUpdateTime(now);
        promptMapper.updateById(prompt);
        runtimeConfigService.attachPrompt(prompt.getAgentId(), prompt.getId(), version.getId(),
                version.getSystemPrompt(), version.getUserPromptTemplate());
        return version;
    }

    private PromptEngineeringEvalResult evaluateCase(PromptEngineeringEvalRun run, PromptEngineeringVersion version,
                                                     PromptEngineeringTestCase testCase, AiModel model,
                                                     LocalDateTime start) {
        PromptEngineeringEvalResult result = new PromptEngineeringEvalResult();
        result.setRunId(run.getId());
        result.setPromptId(run.getPromptId());
        result.setVersionId(version.getId());
        result.setTestCaseId(testCase.getId());
        result.setCaseName(testCase.getCaseName());
        result.setInputJson(testCase.getInputJson());
        result.setExpectedOutput(testCase.getExpectedOutput());
        result.setCreateTime(start);
        try {
            String userPrompt = renderTemplate(version.getUserPromptTemplate(), parseMap(testCase.getInputJson()));
            String predicted = callPromptModel(model, version.getSystemPrompt(), userPrompt);
            int score = scorePrediction(predicted, testCase.getExpectedOutput());
            result.setPredictedOutput(predicted);
            result.setScore(score);
            result.setPassed(score >= 70 ? 1 : 0);
            result.setDimensionScores(toJson(metrics(score)));
            result.setFeedback(score >= 70 ? "输出基本符合期望。" : "输出与期望差异较大，建议增强约束、示例或输出结构要求。");
            result.setRawResult(toJson(Map.of("modelCode", model == null ? "" : model.getModelCode(), "score", score)));
        } catch (Exception e) {
            result.setPredictedOutput("");
            result.setScore(0);
            result.setPassed(0);
            result.setErrorMessage(e.getMessage());
            result.setFeedback("评测失败：" + e.getMessage());
        }
        result.setDurationMs((int) Duration.between(start, LocalDateTime.now()).toMillis());
        return result;
    }

    private String callPromptModel(AiModel model, String systemPrompt, String userPrompt) {
        if (model == null || !hasText(model.getModelCode())) {
            return "未选择可用模型，无法生成预测输出。";
        }
        try {
            return chatModelService.chat(model.getModelCode(), userPrompt, Map.of("systemPrompt", defaultText(systemPrompt, "")));
        } catch (Exception e) {
            return "模型调用失败：" + e.getMessage();
        }
    }

    private String optimizeWithModel(PromptEngineeringVersion source, PromptEngineeringPrompt prompt,
                                     AiModel model, String goal) {
        String request = """
                请优化下面的提示词，要求只返回 JSON：
                {
                  "systemPrompt": "优化后的系统提示词",
                  "userPromptTemplate": "优化后的用户提示词模板",
                  "variableDefinitions": "变量定义 JSON 或说明",
                  "summary": "中文优化说明"
                }

                Agent: %s / %s
                项目: %s
                优化目标: %s
                当前 systemPrompt:
                %s

                当前 userPromptTemplate:
                %s
                """.formatted(prompt.getAgentName(), prompt.getAgentCode(),
                defaultText(prompt.getProjectName(), "-"), goal,
                defaultText(source.getSystemPrompt(), ""), defaultText(source.getUserPromptTemplate(), ""));
        if (model == null) {
            return fallbackOptimization(source, goal);
        }
        try {
            return chatModelService.chat(model.getModelCode(), request);
        } catch (Exception e) {
            return fallbackOptimization(source, goal + "；模型调用失败：" + e.getMessage());
        }
    }

    private PromptEngineeringVersion optimizedVersionRequest(PromptEngineeringVersion source, String raw, String goal) {
        Map<String, Object> parsed = readJson(raw);
        PromptEngineeringVersion request = new PromptEngineeringVersion();
        request.setVersionName("AI优化-" + LocalDateTime.now().toLocalDate());
        request.setSystemPrompt(defaultText(stringValue(parsed.get("systemPrompt")), defaultText(source.getSystemPrompt(), "") + "\n\n请严格遵循业务目标：" + goal));
        request.setUserPromptTemplate(defaultText(stringValue(parsed.get("userPromptTemplate")), source.getUserPromptTemplate()));
        request.setVariableDefinitions(defaultText(stringValue(parsed.get("variableDefinitions")), source.getVariableDefinitions()));
        request.setChangelog(defaultText(stringValue(parsed.get("summary")), "基于优化目标生成的新版本：" + goal));
        return request;
    }

    private String fallbackOptimization(PromptEngineeringVersion source, String goal) {
        return toJson(Map.of(
                "systemPrompt", defaultText(source.getSystemPrompt(), "") + "\n\n请优先满足以下优化目标：" + goal + "\n输出前检查事实一致性、结构完整性和风险边界。",
                "userPromptTemplate", defaultText(source.getUserPromptTemplate(), "") + "\n\n请按以下结构输出：结论、依据、风险、下一步建议。",
                "variableDefinitions", defaultText(source.getVariableDefinitions(), "{}"),
                "summary", "使用本地兜底规则生成优化版本。"
        ));
    }

    private String extractOptimizationSummary(String raw) {
        Map<String, Object> parsed = readJson(raw);
        return defaultText(stringValue(parsed.get("summary")), "已生成优化版本。");
    }

    private AiModel resolveModel(Long modelId, Long agentId) {
        if (modelId != null) {
            return modelMapper.selectById(modelId);
        }
        AiAgent agent = agentMapper.selectById(agentId);
        if (agent != null && agent.getModelId() != null) {
            return modelMapper.selectById(agent.getModelId());
        }
        return null;
    }

    private AiAgent requireAgent(Long agentId) {
        AiAgent agent = agentMapper.selectById(agentId);
        if (agent == null) throw new IllegalArgumentException("Agent does not exist: " + agentId);
        return agent;
    }

    private PromptEngineeringPrompt requirePrompt(Long id) {
        PromptEngineeringPrompt prompt = promptMapper.selectById(id);
        if (prompt == null) throw new IllegalArgumentException("Prompt does not exist: " + id);
        return prompt;
    }

    private PromptEngineeringVersion requireVersion(Long id) {
        PromptEngineeringVersion version = versionMapper.selectById(id);
        if (version == null) throw new IllegalArgumentException("Prompt version does not exist: " + id);
        return version;
    }

    private PromptEngineeringTestCase requireTestCase(Long id) {
        PromptEngineeringTestCase testCase = testCaseMapper.selectById(id);
        if (testCase == null) throw new IllegalArgumentException("Test case does not exist: " + id);
        return testCase;
    }

    private void enrichLatestScore(PromptEngineeringPrompt prompt) {
        if (prompt.getLatestVersionId() == null) return;
        PromptEngineeringVersion version = versionMapper.selectById(prompt.getLatestVersionId());
        if (version != null) prompt.setLatestScore(version.getLatestScore());
    }

    private String renderTemplate(String template, Map<String, Object> values) {
        String result = defaultText(template, "");
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", stringValue(entry.getValue()));
        }
        return result;
    }

    private int scorePrediction(String predicted, String expected) {
        String normalizedPredicted = normalize(predicted);
        String normalizedExpected = normalize(expected);
        if (!hasText(normalizedExpected)) return hasText(normalizedPredicted) ? 75 : 0;
        if (normalizedPredicted.equals(normalizedExpected)) return 100;
        if (normalizedPredicted.contains(normalizedExpected) || normalizedExpected.contains(normalizedPredicted)) return 85;
        long hit = normalizedExpected.chars().filter(ch -> normalizedPredicted.indexOf(ch) >= 0).count();
        return Math.max(20, Math.min(80, (int) Math.round(hit * 100.0 / Math.max(1, normalizedExpected.length()))));
    }

    private Map<String, Object> metrics(int score) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("taskAlignment", score);
        metrics.put("stability", Math.max(0, score - 5));
        metrics.put("constraintFollowing", score);
        metrics.put("outputStructure", Math.min(100, score + 5));
        metrics.put("businessAccuracy", score);
        metrics.put("riskControl", Math.max(0, score - 3));
        return metrics;
    }

    private Map<String, Object> parseMap(String json) {
        try {
            return objectMapper.readValue(defaultText(json, "{}"), new TypeReference<>() {
            });
        } catch (Exception e) {
            return Map.of("input", defaultText(json, ""));
        }
    }

    private Map<String, Object> readJson(String json) {
        try {
            return objectMapper.readValue(defaultText(json, "{}"), new TypeReference<>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private void validateJson(String json, String field) {
        try {
            objectMapper.readTree(defaultText(json, "{}"));
        } catch (Exception e) {
            throw new IllegalArgumentException(field + " must be valid JSON");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private int estimateTokens(String text) {
        return hasText(text) ? Math.max(1, (int) Math.ceil(text.length() / 4.0)) : 0;
    }

    private String normalize(String text) {
        return defaultText(text, "").toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String required(String value, String field) {
        if (!hasText(value)) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    private String defaultText(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
