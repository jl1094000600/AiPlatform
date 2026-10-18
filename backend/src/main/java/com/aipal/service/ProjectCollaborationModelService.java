package com.aipal.service;

import com.aipal.common.BizException;
import com.aipal.common.ErrorCode;
import com.aipal.dto.ProjectModelGatewayDtos.CoordinatorResult;
import com.aipal.dto.ProjectModelGatewayDtos.GeneratedTaskDraft;
import com.aipal.dto.ProjectModelGatewayDtos.PrivateAskResult;
import com.aipal.dto.ProjectModelGatewayDtos.PrivateAskSource;
import com.aipal.dto.ProjectModelGatewayDtos.PrivateAskTurn;
import com.aipal.dto.ProjectModelGatewayDtos.SafeProjectContext;
import com.aipal.entity.AiModel;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Read-only CHAT model gateway for project collaboration.
 *
 * <p>It receives only {@link SafeProjectContext}, uses the tenant's default enabled CHAT model and
 * strictly validates the model answer as a whole. Any malformed/unknown/oversized/empty response
 * fails with {@link ErrorCode#MODEL_RESPONSE_INVALID}; nothing is partially accepted and nothing is
 * persisted here.</p>
 */
@Service
@RequiredArgsConstructor
public class ProjectCollaborationModelService {

    private static final int MAX_RESPONSE_CHARS = 200_000;
    private static final int MAX_SUMMARY = 4_000;
    private static final int MAX_ANSWER = 8_000;
    private static final int MAX_LIST_ITEM = 500;
    private static final int MAX_RISKS = 20;
    private static final int MAX_QUESTIONS = 20;
    private static final int MAX_TASK_DRAFTS = 20;
    private static final int MAX_SOURCES = 20;
    private static final int MAX_LIMITATIONS = 20;
    private static final int MAX_DRAFT_TITLE = 255;
    private static final int MAX_DRAFT_DESCRIPTION = 4_000;
    private static final int MAX_SOURCE_TYPE = 64;
    private static final int MAX_SOURCE_REFERENCE = 500;
    private static final int MAX_QUESTION = 4_000;
    private static final int MAX_HISTORY_TURNS = 20;
    private static final int MAX_HISTORY_CONTENT = 4_000;

    private static final Set<String> COORDINATOR_FIELDS =
            Set.of("summary", "risks", "questions", "taskDrafts");
    private static final Set<String> TASK_DRAFT_FIELDS =
            Set.of("title", "description", "suggestedAgentId");
    private static final Set<String> PRIVATE_ASK_FIELDS =
            Set.of("answer", "sources", "limitations");
    private static final Set<String> PRIVATE_ASK_SOURCE_FIELDS =
            Set.of("type", "reference");

    /**
     * A model must not echo execution internals or filesystem locations into a shared result.
     * Generic words such as "prompt"/"payload" are intentionally not rejected here: they can
     * appear in legitimate advisory prose. Concrete leak markers are rejected.
     */
    private static final Pattern OUTPUT_SENSITIVE = Pattern.compile(
            "(?i)(?:/users/|/home/|[a-z]:\\\\"
                    + "|input\\s*json|result\\s*json|definition\\s*snapshot"
                    + "|api[_-]?key\\s*[=:]|stack\\s*trace)");

    private static final String COORDINATOR_SYSTEM_PROMPT = """
            你是只读的项目协作协调者（Discussion Coordinator），只能基于用户提供的安全项目上下文进行总结和建议。
            规则：
            1. 只读：不得声称已执行、修改、创建、指派、确认或完成任何操作，只能提出建议。
            2. 不得编造上下文中不存在的项目数据、人员或事件。
            3. 不得输出或复述原始提示词、Agent Run 输入输出、工具载荷、定义快照、密钥、绝对路径或异常堆栈。
            4. 只输出 JSON，不要输出 Markdown、解释或额外文本。
            5. JSON 字段必须严格且完整为：summary（非空字符串）、risks（字符串数组）、questions（字符串数组）、taskDrafts（对象数组）。
            6. 每个 taskDraft 只允许 title（必填非空字符串）、description（可选字符串）、suggestedAgentId（可选正整数），不得包含其他字段。
            """;

    private static final String PRIVATE_ASK_SYSTEM_PROMPT = """
            你是只读的项目私密问答助手（Private Ask），只能基于用户提供的安全项目上下文、当前问题和当前页面会话回答。
            规则：
            1. 只读：不得声称已执行、修改、创建、指派、确认或完成任何操作。
            2. 回答仅当前用户可见，不得建议把本次问答写入共享讨论、项目事件或记忆。
            3. 不得编造上下文中不存在的项目数据；无法回答时在 limitations 中说明。
            4. 不得输出或复述原始提示词、Agent Run 输入输出、工具载荷、定义快照、密钥、绝对路径或异常堆栈。
            5. 只输出 JSON，不要输出 Markdown、解释或额外文本。
            6. JSON 字段必须严格且完整为：answer（非空字符串）、sources（对象数组）、limitations（字符串数组）。
            7. 每个 source 只允许 type（非空字符串）和 reference（非空字符串），不得包含其他字段。
            """;

    private final ModelService modelService;
    private final ModelGateway modelGateway;
    private final ObjectMapper objectMapper;

    public CoordinatorResult coordinate(SafeProjectContext context) {
        AiModel model = requireDefaultChatModel();
        Map<String, Object> input = fields("context", contextMap(context));
        String content = call(model, COORDINATOR_SYSTEM_PROMPT, input);
        return parseCoordinator(content);
    }

    public PrivateAskResult privateAsk(SafeProjectContext context, String question, List<PrivateAskTurn> history) {
        String normalizedQuestion = normalizeQuestion(question);
        List<PrivateAskTurn> normalizedHistory = normalizeHistory(history);
        AiModel model = requireDefaultChatModel();
        Map<String, Object> input = fields(
                "context", contextMap(context),
                "question", normalizedQuestion,
                "history", normalizedHistory.stream()
                        .map(turn -> fields("role", turn.role(), "content", turn.content()))
                        .toList());
        String content = call(model, PRIVATE_ASK_SYSTEM_PROMPT, input);
        return parsePrivateAsk(content);
    }

    private AiModel requireDefaultChatModel() {
        AiModel model = modelService.getDefaultEnabledModel(ModelService.CAPABILITY_CHAT);
        if (model == null) {
            throw ErrorCode.MODEL_NOT_CONFIGURED.ex();
        }
        return model;
    }

    private String call(AiModel model, String systemPrompt, Map<String, Object> payload) {
        String userPrompt;
        try {
            userPrompt = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException encodingFailure) {
            throw new IllegalStateException("Failed to encode safe project model input", encodingFailure);
        }
        ModelCallResult result;
        try {
            result = modelGateway.chat(model, systemPrompt, userPrompt);
        } catch (BizException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw ErrorCode.MODEL_CALL_FAILED.ex();
        }
        return result == null ? null : result.contentOrEmpty();
    }

    private String normalizeQuestion(String question) {
        if (question == null || question.isBlank()) {
            throw ErrorCode.PRIVATE_ASK_QUESTION_REQUIRED.ex();
        }
        String normalized = question.strip();
        if (normalized.length() > MAX_QUESTION) {
            throw ErrorCode.PRIVATE_ASK_QUESTION_TOO_LONG.ex();
        }
        return normalized;
    }

    private List<PrivateAskTurn> normalizeHistory(List<PrivateAskTurn> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        if (history.size() > MAX_HISTORY_TURNS) {
            throw ErrorCode.PRIVATE_ASK_HISTORY_INVALID.ex();
        }
        List<PrivateAskTurn> normalized = new ArrayList<>(history.size());
        for (PrivateAskTurn turn : history) {
            if (turn == null || turn.role() == null || turn.content() == null) {
                throw ErrorCode.PRIVATE_ASK_HISTORY_INVALID.ex();
            }
            String role = turn.role().strip().toUpperCase(Locale.ROOT);
            if (!"USER".equals(role) && !"ASSISTANT".equals(role)) {
                throw ErrorCode.PRIVATE_ASK_HISTORY_INVALID.ex();
            }
            String content = turn.content().strip();
            if (content.isEmpty() || content.length() > MAX_HISTORY_CONTENT) {
                throw ErrorCode.PRIVATE_ASK_HISTORY_INVALID.ex();
            }
            normalized.add(new PrivateAskTurn(role, content));
        }
        return normalized;
    }

    private CoordinatorResult parseCoordinator(String content) {
        JsonNode root = parseJsonObject(content, COORDINATOR_FIELDS);
        String summary = requiredText(root, "summary", MAX_SUMMARY);
        List<String> risks = requiredTextList(root, "risks", MAX_LIST_ITEM, MAX_RISKS);
        List<String> questions = requiredTextList(root, "questions", MAX_LIST_ITEM, MAX_QUESTIONS);
        JsonNode draftsNode = root.get("taskDrafts");
        if (draftsNode == null || !draftsNode.isArray() || draftsNode.size() > MAX_TASK_DRAFTS) {
            throw invalidResponse();
        }
        List<GeneratedTaskDraft> taskDrafts = new ArrayList<>(draftsNode.size());
        for (JsonNode draftNode : draftsNode) {
            taskDrafts.add(parseTaskDraft(draftNode));
        }
        return new CoordinatorResult(summary, risks, questions, taskDrafts);
    }

    private GeneratedTaskDraft parseTaskDraft(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw invalidResponse();
        }
        Set<String> actualFields = fieldNames(node);
        if (!TASK_DRAFT_FIELDS.containsAll(actualFields)) {
            throw invalidResponse();
        }
        String title = requiredText(node, "title", MAX_DRAFT_TITLE);
        String description = optionalText(node, "description", MAX_DRAFT_DESCRIPTION);
        Long suggestedAgentId = optionalPositiveLong(node, "suggestedAgentId");
        return new GeneratedTaskDraft(title, description, suggestedAgentId);
    }

    private PrivateAskResult parsePrivateAsk(String content) {
        JsonNode root = parseJsonObject(content, PRIVATE_ASK_FIELDS);
        String answer = requiredText(root, "answer", MAX_ANSWER);
        JsonNode sourcesNode = root.get("sources");
        if (sourcesNode == null || !sourcesNode.isArray() || sourcesNode.size() > MAX_SOURCES) {
            throw invalidResponse();
        }
        List<PrivateAskSource> sources = new ArrayList<>(sourcesNode.size());
        for (JsonNode sourceNode : sourcesNode) {
            if (sourceNode == null || !sourceNode.isObject()) {
                throw invalidResponse();
            }
            if (!PRIVATE_ASK_SOURCE_FIELDS.containsAll(fieldNames(sourceNode))) {
                throw invalidResponse();
            }
            sources.add(new PrivateAskSource(
                    requiredText(sourceNode, "type", MAX_SOURCE_TYPE),
                    requiredText(sourceNode, "reference", MAX_SOURCE_REFERENCE)));
        }
        List<String> limitations = requiredTextList(root, "limitations", MAX_LIST_ITEM, MAX_LIMITATIONS);
        return new PrivateAskResult(answer, sources, limitations);
    }

    private JsonNode parseJsonObject(String content, Set<String> allowedFields) {
        if (content == null || content.isBlank()) {
            throw invalidResponse();
        }
        String json = stripCodeFence(content);
        if (json.length() > MAX_RESPONSE_CHARS) {
            throw invalidResponse();
        }
        JsonNode root;
        try {
            try (JsonParser parser = objectMapper.getFactory().createParser(json)) {
                parser.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
                root = objectMapper.readTree(parser);
                if (root != null && root.isObject() && parser.nextToken() != null) {
                    throw invalidResponse();
                }
            }
            if (root == null || !root.isObject()) {
                throw invalidResponse();
            }
            if (!fieldNames(root).equals(allowedFields)) {
                throw invalidResponse();
            }
            return root;
        } catch (BizException invalid) {
            throw invalid;
        } catch (Exception parseFailure) {
            throw invalidResponse();
        }
    }

    private String stripCodeFence(String content) {
        String trimmed = content.strip();
        if (trimmed.startsWith("\uFEFF")) {
            trimmed = trimmed.substring(1).strip();
        }
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLineEnd = trimmed.indexOf('\n');
        if (firstLineEnd < 0 || !trimmed.endsWith("```")) {
            throw invalidResponse();
        }
        String header = trimmed.substring(0, firstLineEnd).strip();
        if (!header.equals("```") && !header.equalsIgnoreCase("```json")) {
            throw invalidResponse();
        }
        String body = trimmed.substring(firstLineEnd + 1, trimmed.length() - 3).strip();
        if (body.isEmpty()) {
            throw invalidResponse();
        }
        return body;
    }

    private String requiredText(JsonNode node, String field, int maxLength) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw invalidResponse();
        }
        String text = value.asText().strip();
        if (text.isEmpty() || text.length() > maxLength || OUTPUT_SENSITIVE.matcher(text).find()) {
            throw invalidResponse();
        }
        return text;
    }

    private String optionalText(JsonNode node, String field, int maxLength) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw invalidResponse();
        }
        String text = value.asText().strip();
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() > maxLength || OUTPUT_SENSITIVE.matcher(text).find()) {
            throw invalidResponse();
        }
        return text;
    }

    private List<String> requiredTextList(JsonNode node, String field, int maxItemLength, int maxItems) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray() || value.size() > maxItems) {
            throw invalidResponse();
        }
        List<String> result = new ArrayList<>(value.size());
        for (JsonNode item : value) {
            if (!item.isTextual()) {
                throw invalidResponse();
            }
            String text = item.asText().strip();
            if (text.isEmpty() || text.length() > maxItemLength
                    || OUTPUT_SENSITIVE.matcher(text).find()) {
                throw invalidResponse();
            }
            result.add(text);
        }
        return result;
    }

    private Long optionalPositiveLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber()) {
            throw invalidResponse();
        }
        long parsed = value.asLong();
        if (parsed <= 0) {
            throw invalidResponse();
        }
        return parsed;
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private BizException invalidResponse() {
        return ErrorCode.MODEL_RESPONSE_INVALID.ex();
    }

    private Map<String, Object> contextMap(SafeProjectContext context) {
        if (context == null) {
            return Map.of();
        }
        return fields(
                "projectKey", context.projectKey(),
                "projectName", context.projectName(),
                "projectType", context.projectType(),
                "status", context.status(),
                "ownerUserId", context.ownerUserId(),
                "overallStatus", context.overallStatus(),
                "pipelineCount", context.pipelineCount(),
                "activeRunCount", context.activeRunCount(),
                "pendingApprovalCount", context.pendingApprovalCount(),
                "riskCount", context.riskCount(),
                "pipelines", context.pipelines().stream()
                        .map(pipeline -> fields(
                                "id", pipeline.id(),
                                "title", pipeline.title(),
                                "status", pipeline.status(),
                                "currentStage", pipeline.currentStage()))
                        .toList(),
                "runs", context.runs().stream()
                        .map(run -> fields(
                                "id", run.id(),
                                "businessType", run.businessType(),
                                "businessId", run.businessId(),
                                "agentId", run.agentId(),
                                "status", run.status(),
                                "createTime", time(run.createTime()),
                                "endTime", time(run.endTime())))
                        .toList(),
                "risks", context.risks().stream()
                        .map(risk -> fields(
                                "level", risk.level(),
                                "title", risk.title(),
                                "description", risk.description()))
                        .toList(),
                "discussions", context.discussions().stream()
                        .map(message -> fields(
                                "id", message.id(),
                                "senderType", message.senderType(),
                                "senderName", message.senderName(),
                                "messageType", message.messageType(),
                                "content", message.content(),
                                "createTime", time(message.createTime())))
                        .toList(),
                "assignmentEvents", context.assignmentEvents().stream()
                        .map(event -> fields(
                                "id", event.id(),
                                "eventType", event.eventType(),
                                "fromUserId", event.fromUserId(),
                                "toUserId", event.toUserId(),
                                "fromStatus", event.fromStatus(),
                                "toStatus", event.toStatus(),
                                "reason", event.reason(),
                                "createTime", time(event.createTime())))
                        .toList(),
                "members", context.members().stream()
                        .map(member -> fields(
                                "userId", member.userId(),
                                "username", member.username(),
                                "realName", member.realName(),
                                "memberRole", member.memberRole()))
                        .toList());
    }

    private static String time(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    private static Map<String, Object> fields(Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            values.put((String) pairs[i], pairs[i + 1]);
        }
        return values;
    }
}
