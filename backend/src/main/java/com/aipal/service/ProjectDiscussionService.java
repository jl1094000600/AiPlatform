package com.aipal.service;

import com.aipal.common.ErrorCode;
import com.aipal.dto.ProjectDiscussionDtos.CoordinatorRequest;
import com.aipal.dto.ProjectDiscussionDtos.CoordinatorResponse;
import com.aipal.dto.ProjectDiscussionDtos.DiscussionMessageView;
import com.aipal.dto.ProjectDiscussionDtos.PostDiscussionRequest;
import com.aipal.dto.ProjectModelGatewayDtos.CoordinatorResult;
import com.aipal.dto.ProjectModelGatewayDtos.GeneratedTaskDraft;
import com.aipal.dto.ProjectModelGatewayDtos.SafeProjectContext;
import com.aipal.dto.ProjectTaskDraftDtos.CreateTaskDraftRequest;
import com.aipal.dto.ProjectTaskDraftDtos.TaskDraftView;
import com.aipal.entity.AiMemoryProject;
import com.aipal.entity.ProjectDiscussionMessage;
import com.aipal.mapper.ProjectDiscussionMessageMapper;
import com.aipal.security.TenantContext;
import com.aipal.service.memory.MemoryProjectService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * M3-B shared discussion and the read-only Coordinator.
 *
 * <p>Shared discussion messages are persisted per tenant/project. The Coordinator is strictly
 * read-only: it calls the CHAT model outside the write transaction, then persists a validated
 * {@link CoordinatorResult} as one {@code SUMMARY} message plus one message per risk and task
 * suggestion, and one DRAFT task draft per suggestion. A model failure writes nothing.</p>
 *
 * <p>{@code sender_type} is not a database column: it is derived from {@code sender_user_id}
 * (null means {@code COORDINATOR}, otherwise {@code USER}) exactly as the M3-A context gateway
 * does.</p>
 */
@Service
@RequiredArgsConstructor
public class ProjectDiscussionService {

    static final String MESSAGE_TYPE_TEXT = "TEXT";
    static final String MESSAGE_TYPE_SUMMARY = "SUMMARY";
    static final String MESSAGE_TYPE_RISK = "RISK";
    static final String MESSAGE_TYPE_TASK_SUGGESTION = "TASK_SUGGESTION";

    static final String SENDER_TYPE_USER = "USER";
    static final String SENDER_TYPE_COORDINATOR = "COORDINATOR";

    static final String COORDINATOR_NAME = "Coordinator";
    static final String SOURCE_TYPE_DISCUSSION = "DISCUSSION";

    static final int MIN_LIMIT = 1;
    static final int MAX_LIMIT = 50;
    static final int MAX_CONTENT_LENGTH = 4_000;
    static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;
    /** Longest derived suffix is ":suggestion:" + two digits, so the base key must leave room. */
    private static final int MAX_DERIVED_KEY_SUFFIX_LENGTH = 14;
    /** The SUMMARY row uses a MySQL TEXT column (65,535 bytes); leave protocol headroom. */
    private static final int MAX_SUMMARY_JSON_BYTES = 60_000;

    private static final String PERMISSION_VIEW = "project-workspace:view";
    private static final String PERMISSION_EXECUTE = "project-workspace:execute";

    private final MemoryProjectService memoryProjectService;
    private final ProjectDiscussionMessageMapper discussionMessageMapper;
    private final ProjectWorkspaceContextService workspaceContextService;
    private final ProjectCollaborationModelService collaborationModelService;
    private final ProjectTaskDraftService projectTaskDraftService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    /** Returns the most recent {@code limit} messages, oldest first for timeline display. */
    public List<DiscussionMessageView> list(String projectKey, int limit) {
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw ErrorCode.DISCUSSION_LIMIT_INVALID.ex();
        }
        AiMemoryProject project = requireViewProject(projectKey);
        Long tenantId = tenantId(project);
        List<ProjectDiscussionMessage> messages = discussionMessageMapper.selectList(
                new LambdaQueryWrapper<ProjectDiscussionMessage>()
                        .eq(ProjectDiscussionMessage::getTenantId, tenantId)
                        .eq(ProjectDiscussionMessage::getProjectKey, project.getProjectKey())
                        .orderByDesc(ProjectDiscussionMessage::getCreateTime)
                        .orderByDesc(ProjectDiscussionMessage::getId)
                        .last("LIMIT " + limit));
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<ProjectDiscussionMessage> ascending = new ArrayList<>(messages);
        ascending.sort(Comparator
                .comparing(ProjectDiscussionMessage::getCreateTime,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ProjectDiscussionMessage::getId,
                        Comparator.nullsLast(Comparator.naturalOrder())));
        if (ascending.size() > limit) {
            // Keep the newest messages while preserving ascending timeline order.
            ascending = new ArrayList<>(ascending.subList(ascending.size() - limit, ascending.size()));
        }
        return ascending.stream().map(this::view).toList();
    }

    /** Persists one user message, replaying an identical idempotent request. */
    public DiscussionMessageView post(String projectKey, PostDiscussionRequest request) {
        AiMemoryProject project = requireViewProject(projectKey);
        String content = checkedContent(request == null ? null : request.content());
        String idempotencyKey = checkedIdempotencyKey(request == null ? null : request.idempotencyKey());

        if (idempotencyKey != null) {
            ProjectDiscussionMessage existing = findByKey(project, idempotencyKey);
            if (existing != null) {
                return replayMessage(existing, content);
            }
        }

        ProjectDiscussionMessage message = new ProjectDiscussionMessage();
        message.setTenantId(tenantId(project));
        message.setProjectKey(project.getProjectKey());
        message.setSenderUserId(TenantContext.userId());
        message.setSenderName(TenantContext.username());
        message.setMessageType(MESSAGE_TYPE_TEXT);
        message.setContent(content);
        message.setIdempotencyKey(idempotencyKey);
        message.setCreateTime(LocalDateTime.now());
        message.setUpdateTime(message.getCreateTime());
        message.setIsDeleted(0);
        try {
            discussionMessageMapper.insert(message);
        } catch (DuplicateKeyException duplicate) {
            // A concurrent request may have won the idempotency race; replay only identical content.
            ProjectDiscussionMessage winner = idempotencyKey == null ? null : findByKey(project, idempotencyKey);
            if (winner == null) {
                throw duplicate;
            }
            return replayMessage(winner, content);
        }
        return view(message);
    }

    /**
     * Runs the read-only Coordinator. The model call happens before the write transaction; only a
     * fully validated answer is persisted, and SUMMARY + RISK/TASK_SUGGESTION messages + task drafts
     * commit or roll back together.
     */
    public CoordinatorResponse coordinate(String projectKey, CoordinatorRequest request) {
        memoryProjectService.requireWorkspacePermission(PERMISSION_EXECUTE);
        AiMemoryProject project = requireViewProject(projectKey);
        String idempotencyKey = checkedIdempotencyKey(request == null ? null : request.idempotencyKey());
        if (idempotencyKey != null
                && idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH - MAX_DERIVED_KEY_SUFFIX_LENGTH) {
            throw ErrorCode.IDEMPOTENCY_KEY_TOO_LONG.ex();
        }

        if (idempotencyKey != null) {
            ProjectDiscussionMessage existing = findByKey(project, idempotencyKey);
            if (existing != null) {
                return replayCoordinator(project, existing);
            }
        }

        // The CHAT call is deliberately outside the write transaction; focus is not part of the
        // frozen M3-A gateway input and is therefore not forwarded.
        SafeProjectContext context = workspaceContextService.build(project.getProjectKey());
        CoordinatorResult result = collaborationModelService.coordinate(context);
        if (result == null) {
            throw ErrorCode.MODEL_RESPONSE_INVALID.ex();
        }
        String summaryJson = encodeResult(result);
        Long tenantId = tenantId(project);
        String trustedProjectKey = project.getProjectKey();
        LocalDateTime now = LocalDateTime.now();

        try {
            CoordinatorResponse response = transactionTemplate.execute(status -> persistCoordinator(
                    tenantId, trustedProjectKey, idempotencyKey, result, summaryJson, now));
            if (response == null) {
                throw new IllegalStateException("Coordinator transaction returned no result");
            }
            return response;
        } catch (DuplicateKeyException duplicate) {
            // A concurrent identical request won the SUMMARY unique key; replay its committed result.
            ProjectDiscussionMessage winner = idempotencyKey == null ? null : findByKey(project, idempotencyKey);
            if (winner != null) {
                return replayCoordinator(project, winner);
            }
            throw duplicate;
        }
    }

    private CoordinatorResponse persistCoordinator(Long tenantId, String projectKey, String idempotencyKey,
                                                   CoordinatorResult result, String summaryJson,
                                                   LocalDateTime now) {
        ProjectDiscussionMessage summary = buildMessage(tenantId, projectKey, null, COORDINATOR_NAME,
                MESSAGE_TYPE_SUMMARY, summaryJson, idempotencyKey, now);
        discussionMessageMapper.insert(summary);

        List<String> risks = safeList(result.risks());
        for (int i = 0; i < risks.size(); i++) {
            discussionMessageMapper.insert(buildMessage(tenantId, projectKey, null, COORDINATOR_NAME,
                    MESSAGE_TYPE_RISK, risks.get(i), derivedKey(idempotencyKey, ":risk:", i), now));
        }

        List<GeneratedTaskDraft> generated = safeList(result.taskDrafts());
        List<TaskDraftView> views = new ArrayList<>(generated.size());
        for (int i = 0; i < generated.size(); i++) {
            GeneratedTaskDraft draft = generated.get(i);
            discussionMessageMapper.insert(buildMessage(tenantId, projectKey, null, COORDINATOR_NAME,
                    MESSAGE_TYPE_TASK_SUGGESTION, draft.title(),
                    derivedKey(idempotencyKey, ":suggestion:", i), now));
            views.add(projectTaskDraftService.create(projectKey, new CreateTaskDraftRequest(
                    draft.title(),
                    draft.description(),
                    draft.suggestedAgentId(),
                    null,
                    SOURCE_TYPE_DISCUSSION,
                    summary.getId(),
                    derivedKey(idempotencyKey, ":draft:", i))));
        }
        return new CoordinatorResponse(result.summary(), risks, result.questions(), views, false);
    }

    /**
     * Replays a previously committed Coordinator run without calling the model again. Task drafts
     * are reloaded by their DISCUSSION source message so ids/status reflect the committed rows.
     */
    private CoordinatorResponse replayCoordinator(AiMemoryProject project, ProjectDiscussionMessage summary) {
        if (!MESSAGE_TYPE_SUMMARY.equals(summary.getMessageType())) {
            throw ErrorCode.DISCUSSION_IDEMPOTENCY_CONFLICT.ex();
        }
        CoordinatorResult result;
        try {
            result = objectMapper.readValue(summary.getContent(), CoordinatorResult.class);
        } catch (Exception invalid) {
            throw ErrorCode.MODEL_RESPONSE_INVALID.ex();
        }
        if (result == null) {
            throw ErrorCode.MODEL_RESPONSE_INVALID.ex();
        }
        List<TaskDraftView> drafts = result.taskDrafts().isEmpty()
                ? List.of()
                : linkedDrafts(project.getProjectKey(), summary.getId());
        return new CoordinatorResponse(result.summary(), result.risks(), result.questions(), drafts, true);
    }

    private List<TaskDraftView> linkedDrafts(String projectKey, Long summaryId) {
        if (summaryId == null) {
            return List.of();
        }
        List<TaskDraftView> drafts = projectTaskDraftService.list(projectKey);
        if (drafts == null || drafts.isEmpty()) {
            return List.of();
        }
        return drafts.stream()
                .filter(draft -> SOURCE_TYPE_DISCUSSION.equals(draft.sourceType()))
                .filter(draft -> Objects.equals(summaryId, draft.sourceId()))
                .toList();
    }

    private DiscussionMessageView replayMessage(ProjectDiscussionMessage existing, String content) {
        if (!Objects.equals(existing.getContent(), content)) {
            throw ErrorCode.DISCUSSION_IDEMPOTENCY_CONFLICT.ex();
        }
        return view(existing);
    }

    private ProjectDiscussionMessage findByKey(AiMemoryProject project, String idempotencyKey) {
        return discussionMessageMapper.selectOne(new LambdaQueryWrapper<ProjectDiscussionMessage>()
                .eq(ProjectDiscussionMessage::getTenantId, tenantId(project))
                .eq(ProjectDiscussionMessage::getProjectKey, project.getProjectKey())
                .eq(ProjectDiscussionMessage::getIdempotencyKey, idempotencyKey)
                .last("LIMIT 1"));
    }

    private ProjectDiscussionMessage buildMessage(Long tenantId, String projectKey, Long senderUserId,
                                                  String senderName, String messageType, String content,
                                                  String idempotencyKey, LocalDateTime now) {
        ProjectDiscussionMessage message = new ProjectDiscussionMessage();
        message.setTenantId(tenantId);
        message.setProjectKey(projectKey);
        message.setSenderUserId(senderUserId);
        message.setSenderName(senderName);
        message.setMessageType(messageType);
        message.setContent(content);
        message.setIdempotencyKey(idempotencyKey);
        message.setCreateTime(now);
        message.setUpdateTime(now);
        message.setIsDeleted(0);
        return message;
    }

    private DiscussionMessageView view(ProjectDiscussionMessage message) {
        String senderType = message.getSenderUserId() == null ? SENDER_TYPE_COORDINATOR : SENDER_TYPE_USER;
        String content = MESSAGE_TYPE_SUMMARY.equals(message.getMessageType())
                ? summaryText(message.getContent())
                : message.getContent();
        return new DiscussionMessageView(message.getId(), message.getProjectKey(), message.getSenderUserId(),
                senderType, message.getSenderName(), message.getMessageType(), content,
                message.getCreateTime());
    }

    /** SUMMARY rows store the validated CoordinatorResult JSON; display only its summary text. */
    private String summaryText(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        try {
            JsonNode root = objectMapper.readTree(content);
            if (root != null && root.isObject() && root.hasNonNull("summary")
                    && root.get("summary").isTextual()) {
                return root.get("summary").asText();
            }
        } catch (Exception notJson) {
            // Legacy/plain text summaries are returned verbatim.
        }
        return content;
    }

    private String encodeResult(CoordinatorResult result) {
        try {
            String json = objectMapper.writeValueAsString(result);
            if (json.getBytes(StandardCharsets.UTF_8).length > MAX_SUMMARY_JSON_BYTES) {
                // Never let a valid-but-huge model answer overflow the TEXT summary column after
                // the model call has already consumed budget.
                throw ErrorCode.MODEL_RESPONSE_INVALID.ex();
            }
            return json;
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Failed to encode coordinator result", failure);
        }
    }

    private AiMemoryProject requireViewProject(String projectKey) {
        memoryProjectService.requireWorkspacePermission(PERMISSION_VIEW);
        return memoryProjectService.requireAccessibleWorkspaceProject(projectKey);
    }

    private Long tenantId(AiMemoryProject project) {
        return project.getTenantId() != null ? project.getTenantId() : TenantContext.tenantId();
    }

    private String checkedContent(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ErrorCode.DISCUSSION_CONTENT_REQUIRED.ex();
        }
        String content = raw.strip();
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw ErrorCode.DISCUSSION_CONTENT_TOO_LONG.ex();
        }
        return content;
    }

    private String checkedIdempotencyKey(String raw) {
        if (raw == null) {
            return null;
        }
        String key = raw.strip();
        if (key.isEmpty()) {
            return null;
        }
        if (key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw ErrorCode.IDEMPOTENCY_KEY_TOO_LONG.ex();
        }
        return key;
    }

    private static String derivedKey(String baseKey, String suffix, int index) {
        return baseKey == null ? null : baseKey + suffix + index;
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
