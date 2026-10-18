package com.aipal.service;

import com.aipal.common.ErrorCode;
import com.aipal.dto.ProjectModelGatewayDtos.PrivateAskResult;
import com.aipal.dto.ProjectModelGatewayDtos.PrivateAskTurn;
import com.aipal.dto.ProjectModelGatewayDtos.SafeProjectContext;
import com.aipal.dto.ProjectPrivateAskDtos.ConvertPrivateAskRequest;
import com.aipal.dto.ProjectPrivateAskDtos.PrivateAskRequest;
import com.aipal.dto.ProjectPrivateAskDtos.PrivateAskResponse;
import com.aipal.dto.ProjectTaskDraftDtos.CreateTaskDraftRequest;
import com.aipal.dto.ProjectTaskDraftDtos.TaskDraftView;
import com.aipal.entity.AiMemoryProject;
import com.aipal.service.memory.MemoryProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * M3-C Private Ask: a read-only, zero-persistence project Q&amp;A plus the explicit conversion of a
 * user-selected summary into a task draft.
 *
 * <p>The question and the page-session history are handed straight to
 * {@link ProjectCollaborationModelService#privateAsk} together with the frozen
 * {@link SafeProjectContext}. Nothing is persisted: no discussion, task draft, memory or audit
 * content is written. The only write in this service is the user's explicit
 * {@link #convertToDraft} call, which persists just the submitted title/description summary with
 * source type {@code PRIVATE_ASK}.</p>
 */
@Service
@RequiredArgsConstructor
public class ProjectPrivateAskService {

    /** The frozen draft source type for an explicitly submitted Private Ask summary. */
    public static final String SOURCE_TYPE_PRIVATE_ASK = "PRIVATE_ASK";

    private static final String PERMISSION_VIEW = "project-workspace:view";
    private static final String PERMISSION_EXECUTE = "project-workspace:execute";

    private static final int MAX_QUESTION_LENGTH = 4_000;
    private static final int MAX_HISTORY_TURNS = 20;
    private static final int MAX_HISTORY_CONTENT_LENGTH = 4_000;
    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_DESCRIPTION_LENGTH = 4_000;

    private final ProjectWorkspaceContextService workspaceContextService;
    private final ProjectCollaborationModelService collaborationModelService;
    private final ProjectTaskDraftService projectTaskDraftService;
    private final MemoryProjectService memoryProjectService;

    /**
     * Answers a private question against the frozen safe project context.
     *
     * <p>Deliberately not transactional: this method performs no writes. Building the context is
     * read-only and also enforces the workspace view permission plus a live project membership.</p>
     */
    public PrivateAskResponse ask(String projectKey, PrivateAskRequest request) {
        memoryProjectService.requireWorkspacePermission(PERMISSION_VIEW);
        SafeProjectContext context = workspaceContextService.build(projectKey);
        AskInput input = validateAsk(request);
        PrivateAskResult result = collaborationModelService.privateAsk(context, input.question(), input.history());
        if (result == null) {
            throw ErrorCode.MODEL_RESPONSE_INVALID.ex();
        }
        return new PrivateAskResponse(result.answer(), result.sources(), result.limitations());
    }

    /**
     * Creates a task draft from the summary the user explicitly selected in the Private Ask panel.
     *
     * <p>Only the submitted title/description leaves the private session; the conversation itself is
     * never read here, so it cannot be copied into the draft, a run input, shared discussions,
     * project events, memories or audit bodies.</p>
     */
    public TaskDraftView convertToDraft(String projectKey, ConvertPrivateAskRequest request) {
        memoryProjectService.requireWorkspacePermission(PERMISSION_EXECUTE);
        AiMemoryProject project = memoryProjectService.requireAccessibleWorkspaceProject(projectKey);
        DraftInput input = validateConversion(request);
        return projectTaskDraftService.create(project.getProjectKey(),
                new CreateTaskDraftRequest(input.title(), input.description(),
                        request.suggestedAgentId(), request.suggestedAssigneeUserId(),
                        SOURCE_TYPE_PRIVATE_ASK, null, request.idempotencyKey()));
    }

    private AskInput validateAsk(PrivateAskRequest request) {
        String question = request == null ? null : trimmedToNull(request.question());
        if (question == null) {
            throw ErrorCode.PRIVATE_ASK_QUESTION_REQUIRED.ex();
        }
        if (question.length() > MAX_QUESTION_LENGTH) {
            throw ErrorCode.PRIVATE_ASK_QUESTION_TOO_LONG.ex();
        }
        List<PrivateAskTurn> history = request.history();
        if (history.size() > MAX_HISTORY_TURNS) {
            throw ErrorCode.PRIVATE_ASK_HISTORY_INVALID.ex();
        }
        List<PrivateAskTurn> normalized = new ArrayList<>(history.size());
        for (PrivateAskTurn turn : history) {
            normalized.add(normalizeTurn(turn));
        }
        return new AskInput(question, List.copyOf(normalized));
    }

    private PrivateAskTurn normalizeTurn(PrivateAskTurn turn) {
        if (turn == null || turn.role() == null || turn.content() == null) {
            throw ErrorCode.PRIVATE_ASK_HISTORY_INVALID.ex();
        }
        String role = turn.role().strip().toUpperCase(Locale.ROOT);
        if (!"USER".equals(role) && !"ASSISTANT".equals(role)) {
            throw ErrorCode.PRIVATE_ASK_HISTORY_INVALID.ex();
        }
        String content = turn.content().strip();
        if (content.isEmpty() || content.length() > MAX_HISTORY_CONTENT_LENGTH) {
            throw ErrorCode.PRIVATE_ASK_HISTORY_INVALID.ex();
        }
        return new PrivateAskTurn(role, content);
    }

    private DraftInput validateConversion(ConvertPrivateAskRequest request) {
        if (request == null) {
            throw ErrorCode.TASK_DRAFT_TITLE_REQUIRED.ex();
        }
        String title = trimmedToNull(request.title());
        if (title == null) {
            throw ErrorCode.TASK_DRAFT_TITLE_REQUIRED.ex();
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw ErrorCode.TASK_DRAFT_TITLE_TOO_LONG.ex();
        }
        String description = trimmedToNull(request.description());
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            throw ErrorCode.TASK_DRAFT_DESCRIPTION_TOO_LONG.ex();
        }
        return new DraftInput(title, description);
    }

    private String trimmedToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record AskInput(String question, List<PrivateAskTurn> history) {
    }

    private record DraftInput(String title, String description) {
    }
}
