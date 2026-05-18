package com.aipal.service;

import com.aipal.dto.SkillFunctionDefinition;
import com.aipal.dto.SkillGenerateRequest;
import com.aipal.dto.SkillRequest;
import com.aipal.dto.SkillResponse;
import com.aipal.entity.AiSkill;
import com.aipal.mapper.AiSkillMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SkillService {
    private static final int STATUS_DISABLED = 0;
    private static final int STATUS_ENABLED = 1;

    private final AiSkillMapper skillMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = false)
    private ChatModelService chatModelService;

    public Page<SkillResponse> listSkills(int pageNum, int pageSize, Integer status) {
        LambdaQueryWrapper<AiSkill> wrapper = new LambdaQueryWrapper<AiSkill>()
                .eq(status != null, AiSkill::getStatus, status)
                .orderByDesc(AiSkill::getCreateTime);
        Page<AiSkill> page = skillMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Page<SkillResponse> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toResponse).toList());
        return result;
    }

    public List<SkillResponse> listEnabledSkills() {
        return skillMapper.selectList(new LambdaQueryWrapper<AiSkill>()
                        .eq(AiSkill::getStatus, STATUS_ENABLED)
                        .orderByDesc(AiSkill::getCreateTime))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public SkillResponse getSkill(Long id) {
        return toResponse(requireSkill(id));
    }

    public SkillResponse createSkill(SkillRequest request) {
        SkillRequest normalized = request == null ? new SkillRequest() : request;
        validate(normalized);
        LocalDateTime now = LocalDateTime.now();
        AiSkill skill = new AiSkill();
        fillSkill(skill, normalized);
        skill.setCreateTime(now);
        skill.setUpdateTime(now);
        skill.setIsDeleted(0);
        skillMapper.insert(skill);
        return toResponse(skill);
    }

    public SkillResponse updateSkill(Long id, SkillRequest request) {
        AiSkill skill = requireSkill(id);
        SkillRequest normalized = request == null ? new SkillRequest() : request;
        validate(normalized);
        fillSkill(skill, normalized);
        skill.setUpdateTime(LocalDateTime.now());
        skillMapper.updateById(skill);
        return toResponse(skill);
    }

    public boolean deleteSkill(Long id) {
        return skillMapper.deleteById(id) > 0;
    }

    public SkillRequest generateSkillDraft(SkillGenerateRequest request) {
        String requirement = normalizeText(request == null ? null : request.getRequirement(), 500);
        if (requirement == null) {
            throw new IllegalArgumentException("请输入想生成的 Skill 能力说明");
        }
        String scenario = normalizeText(request.getScenario(), 200);
        String modelCode = normalizeText(request.getModelCode(), 80);
        boolean includeFunction = request.getIncludeFunction() == null || request.getIncludeFunction();
        SkillRequest fallback = buildTemplateDraft(requirement, scenario, includeFunction);
        if (modelCode != null && chatModelService != null) {
            try {
                return mergeDraft(fallback, generateSkillDraftWithModel(modelCode, requirement, scenario, includeFunction));
            } catch (Exception e) {
                log.warn("Failed to generate skill draft with model {}, fallback to template: {}", modelCode, e.getMessage());
            }
        }
        return fallback;
    }

    private SkillRequest buildTemplateDraft(String requirement, String scenario, boolean includeFunction) {
        SkillRequest draft = new SkillRequest();
        draft.setSkillCode("AI_SKILL_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT));
        draft.setSkillName(buildDraftName(requirement));
        draft.setDescription(buildDraftDescription(requirement, scenario));
        draft.setStatus(STATUS_ENABLED);
        draft.setPromptContent(buildDraftPrompt(requirement, scenario));
        draft.setFunctionDefinitions(includeFunction ? List.of(buildDraftFunction(requirement)) : List.of());
        return draft;
    }

    private SkillRequest generateSkillDraftWithModel(String modelCode, String requirement, String scenario, boolean includeFunction) throws IOException {
        String modelPrompt = """
                你是 AI 平台的 Skill 设计助手。请根据用户需求生成一个 Skill 草案，只返回严格 JSON，不要 Markdown。

                JSON 字段：
                {
                  "skillCode": "可选，英文大写和下划线组成",
                  "skillName": "简短中文名称，以 Skill 结尾",
                  "description": "一句话描述适用场景",
                  "status": 1,
                  "promptContent": "可直接交给模型遵循的中文提示词",
                  "functionDefinitions": [
                    {
                      "name": "英文驼峰函数名",
                      "description": "函数说明",
                      "parametersJson": "合法 JSON Schema 字符串",
                      "returnSchema": "合法 JSON Schema 字符串",
                      "javaSnippet": "Java 可读函数签名或片段",
                      "enabled": true
                    }
                  ]
                }

                用户需求：%s
                使用场景：%s
                是否需要函数元数据：%s
                """.formatted(requirement, scenario == null ? "未指定" : scenario, includeFunction ? "是" : "否");
        String response = chatModelService.chat(modelCode, modelPrompt);
        JsonNode json = objectMapper.readTree(extractJsonObject(response));
        return objectMapper.treeToValue(json, SkillRequest.class);
    }

    private SkillRequest mergeDraft(SkillRequest fallback, SkillRequest generated) {
        if (generated == null) {
            return fallback;
        }
        SkillRequest merged = new SkillRequest();
        merged.setSkillCode(blankToDefault(generated.getSkillCode(), fallback.getSkillCode()));
        merged.setSkillName(blankToDefault(generated.getSkillName(), fallback.getSkillName()));
        merged.setDescription(blankToDefault(generated.getDescription(), fallback.getDescription()));
        merged.setStatus(generated.getStatus() == null ? STATUS_ENABLED : generated.getStatus());
        merged.setPromptContent(blankToDefault(generated.getPromptContent(), fallback.getPromptContent()));
        List<SkillFunctionDefinition> functions = generated.getFunctionDefinitions() == null
                ? List.of() : generated.getFunctionDefinitions();
        merged.setFunctionDefinitions(functions.isEmpty() ? fallback.getFunctionDefinitions() : functions);
        return merged;
    }

    public String requireEnabledSkillSnapshot(Long id) {
        if (id == null) {
            return null;
        }
        AiSkill skill = requireSkill(id);
        if (skill.getStatus() == null || skill.getStatus() != STATUS_ENABLED) {
            throw new IllegalArgumentException("Skill is disabled: " + id);
        }
        try {
            return objectMapper.writeValueAsString(toResponse(skill));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize skill snapshot: " + e.getMessage(), e);
        }
    }

    private void fillSkill(AiSkill skill, SkillRequest request) {
        skill.setSkillCode(resolveSkillCode(request.getSkillCode()));
        skill.setSkillName(request.getSkillName().trim());
        skill.setDescription(blankToNull(request.getDescription()));
        skill.setStatus(request.getStatus() == null ? STATUS_ENABLED : request.getStatus());
        skill.setPromptContent(blankToNull(request.getPromptContent()));
        skill.setFunctionDefinitions(writeFunctions(request.getFunctionDefinitions()));
    }

    private void validate(SkillRequest request) {
        if (request.getSkillName() == null || request.getSkillName().isBlank()) {
            throw new IllegalArgumentException("skillName is required");
        }
        if (request.getStatus() != null && request.getStatus() != STATUS_ENABLED && request.getStatus() != STATUS_DISABLED) {
            throw new IllegalArgumentException("status must be 0 or 1");
        }
        List<SkillFunctionDefinition> functions = request.getFunctionDefinitions() == null
                ? List.of() : request.getFunctionDefinitions();
        for (SkillFunctionDefinition function : functions) {
            boolean enabled = function.getEnabled() == null || function.getEnabled();
            if (enabled && (function.getName() == null || function.getName().isBlank())) {
                throw new IllegalArgumentException("Enabled function name is required");
            }
            if (function.getParametersJson() != null && !function.getParametersJson().isBlank()) {
                assertJson(function.getParametersJson(), "parametersJson");
            }
        }
    }

    private AiSkill requireSkill(Long id) {
        AiSkill skill = skillMapper.selectById(id);
        if (skill == null) {
            throw new IllegalArgumentException("Skill does not exist: " + id);
        }
        return skill;
    }

    private SkillResponse toResponse(AiSkill skill) {
        if (skill == null) {
            return null;
        }
        SkillResponse response = new SkillResponse();
        response.setId(skill.getId());
        response.setSkillCode(skill.getSkillCode());
        response.setSkillName(skill.getSkillName());
        response.setDescription(skill.getDescription());
        response.setStatus(skill.getStatus());
        response.setPromptContent(skill.getPromptContent());
        response.setFunctionDefinitions(readFunctions(skill.getFunctionDefinitions()));
        response.setCreateTime(skill.getCreateTime());
        response.setUpdateTime(skill.getUpdateTime());
        return response;
    }

    private String writeFunctions(List<SkillFunctionDefinition> functions) {
        try {
            return objectMapper.writeValueAsString(functions == null ? List.of() : functions);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid function definitions: " + e.getMessage(), e);
        }
    }

    private List<SkillFunctionDefinition> readFunctions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<SkillFunctionDefinition>>() {});
        } catch (IOException e) {
            return List.of();
        }
    }

    private void assertJson(String value, String fieldName) {
        try {
            objectMapper.readTree(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(fieldName + " must be valid JSON");
        }
    }

    private String resolveSkillCode(String value) {
        if (value == null || value.isBlank()) {
            return "SKILL_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String extractJsonObject(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Model returned empty response");
        }
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("Model response does not contain a JSON object");
        }
        return value.substring(start, end + 1);
    }

    private String normalizeText(String value, int maxCodePoints) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return trimCodePoints(normalized, maxCodePoints);
    }

    private String buildDraftName(String requirement) {
        String title = requirement
                .replaceAll("[，。,.；;：:！!？?]+.*$", "")
                .replaceAll("[^\\p{IsHan}a-zA-Z0-9 _-]", "")
                .trim();
        if (title.isBlank()) {
            title = "AI 生成";
        }
        return trimCodePoints(title, 18) + " Skill";
    }

    private String buildDraftDescription(String requirement, String scenario) {
        String description = "用于" + requirement + "，帮助模型按统一约束完成任务。";
        if (scenario != null) {
            description += " 适用场景：" + scenario + "。";
        }
        return trimCodePoints(description, 180);
    }

    private String buildDraftPrompt(String requirement, String scenario) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个可复用的业务 Skill，目标是：").append(requirement).append("\n\n");
        if (scenario != null) {
            prompt.append("适用场景：").append(scenario).append("\n\n");
        }
        prompt.append("""
                执行要求：
                1. 先识别用户真实目标、输入信息和约束条件。
                2. 如果关键信息不足，先用简短问题补齐，不要臆测业务事实。
                3. 输出内容要结构清晰，可直接被 Agent 或人工继续使用。
                4. 遇到高风险、权限、隐私或数据不确定场景时，需要明确提示风险和假设。
                5. 保持语言简洁，优先给出可执行结果，再补充必要说明。

                输出格式：
                - 结论或生成结果
                - 关键依据
                - 下一步建议
                """);
        return prompt.toString();
    }

    private SkillFunctionDefinition buildDraftFunction(String requirement) {
        SkillFunctionDefinition function = new SkillFunctionDefinition();
        function.setName(resolveDraftFunctionName(requirement));
        function.setDescription("接收用户输入、上下文和约束，执行该 Skill 的核心任务。");
        function.setParametersJson("""
                {
                  "type": "object",
                  "properties": {
                    "input": {
                      "type": "string",
                      "description": "用户原始需求或待处理内容"
                    },
                    "context": {
                      "type": "object",
                      "description": "业务上下文、已知数据或上游 Agent 输出"
                    },
                    "constraints": {
                      "type": "array",
                      "items": {
                        "type": "string"
                      },
                      "description": "执行时必须遵守的限制条件"
                    }
                  },
                  "required": [
                    "input"
                  ]
                }
                """);
        function.setReturnSchema("""
                {
                  "type": "object",
                  "properties": {
                    "result": {
                      "type": "string",
                      "description": "Skill 执行结果"
                    },
                    "confidence": {
                      "type": "number",
                      "description": "结果可信度，范围 0-1"
                    },
                    "nextActions": {
                      "type": "array",
                      "items": {
                        "type": "string"
                      },
                      "description": "建议的下一步动作"
                    }
                  }
                }
                """);
        function.setJavaSnippet("""
                public SkillResult executeSkillTask(SkillInput input) {
                    // TODO: 接入具体业务服务或数据源
                    return SkillResult.success();
                }
                """);
        function.setEnabled(true);
        return function;
    }

    private String resolveDraftFunctionName(String requirement) {
        if (requirement.contains("查询") || requirement.contains("检索") || requirement.contains("读取")) {
            return "querySkillContext";
        }
        if (requirement.contains("生成") || requirement.contains("编写") || requirement.contains("撰写")) {
            return "generateSkillContent";
        }
        if (requirement.contains("审核") || requirement.contains("检查") || requirement.contains("评估")) {
            return "reviewSkillOutput";
        }
        return "executeSkillTask";
    }

    private String trimCodePoints(String value, int maxCodePoints) {
        if (value == null || value.codePointCount(0, value.length()) <= maxCodePoints) {
            return value;
        }
        int end = value.offsetByCodePoints(0, maxCodePoints);
        return value.substring(0, end);
    }
}
