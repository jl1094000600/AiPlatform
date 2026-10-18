package com.aipal.service;

import com.aipal.common.BizException;
import com.aipal.common.ErrorCode;
import com.aipal.dto.ProjectModelGatewayDtos.SafeProjectContext;
import com.aipal.dto.ProjectWarRoomDtos;
import com.aipal.entity.AiMemoryProject;
import com.aipal.entity.ProjectAssignmentEvent;
import com.aipal.entity.ProjectDiscussionMessage;
import com.aipal.mapper.ProjectAssignmentEventMapper;
import com.aipal.mapper.ProjectDiscussionMessageMapper;
import com.aipal.service.memory.MemoryProjectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectWorkspaceContextServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

    private MemoryProjectService memoryProjectService;
    private ProjectWarRoomService projectWarRoomService;
    private ProjectDiscussionMessageMapper discussionMessageMapper;
    private ProjectAssignmentEventMapper assignmentEventMapper;
    private ProjectWorkspaceContextService service;
    private AiMemoryProject project;

    @BeforeEach
    void setUp() {
        memoryProjectService = mock(MemoryProjectService.class);
        projectWarRoomService = mock(ProjectWarRoomService.class);
        discussionMessageMapper = mock(ProjectDiscussionMessageMapper.class);
        assignmentEventMapper = mock(ProjectAssignmentEventMapper.class);
        service = new ProjectWorkspaceContextService(memoryProjectService, projectWarRoomService,
                discussionMessageMapper, assignmentEventMapper);

        project = new AiMemoryProject();
        project.setId(7L);
        project.setTenantId(1L);
        project.setProjectKey("proj-1");
        project.setProjectName("Project One");
        project.setProjectType("CUSTOMER_PROJECT");
        project.setOwnerUserId(9L);
        project.setStatus("ACTIVE");
        when(memoryProjectService.requireAccessibleWorkspaceProject("proj-1")).thenReturn(project);
        when(memoryProjectService.listWorkspaceMentionableUsers("proj-1")).thenReturn(List.of());
        when(projectWarRoomService.getWarRoom("proj-1")).thenReturn(emptyWarRoom());
        when(discussionMessageMapper.selectList(any())).thenReturn(List.of());
        when(assignmentEventMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    void toStringAndJsonDoNotLeakExecutionInternalsOrPaths() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 7, 28, 10, 0);
        project.setProjectName("/Users/alice/workspace/AiPlatform prompt");

        ProjectWarRoomDtos.WarRoomPipelineView pipeline = new ProjectWarRoomDtos.WarRoomPipelineView(
                11L, "AUTO_1", "proj-1", "inputJson={\"prompt\":\"secret\"} /Users/alice/repo",
                "RUNNING", "requirement_analysis", "需求确认", 1, 8, now,
                List.of(), List.of());
        ProjectWarRoomDtos.TeamAgentQueueRunView run = new ProjectWarRoomDtos.TeamAgentQueueRunView(
                21L, "proj-1", "AGENT_RUN", "REQ-1 resultJson apiKey", 3L, "FAILED",
                "trace-1", null, 12, "Execution failed", false, false, now, now, now);
        ProjectWarRoomDtos.RiskCardView risk = new ProjectWarRoomDtos.RiskCardView(
                "DANGER", "payload leak", "C:\\secret\\stack trace at com.aipal.Secret.run(Secret.java:1)",
                "AGENT_RUN", 21L);
        when(projectWarRoomService.getWarRoom("proj-1")).thenReturn(new ProjectWarRoomDtos.WarRoomResponse(
                new ProjectWarRoomDtos.ProjectWorkspaceView(7L, "proj-1", project.getProjectName(),
                        "CUSTOMER_PROJECT", 9L, "ACTIVE", now),
                List.of(pipeline), List.of(run), List.of(), List.of(), List.of(), List.of(risk),
                new ProjectWarRoomDtos.WarRoomSummary(1, 1, 0, 1, "RED"), false));

        ProjectDiscussionMessage coordinator = discussion(101L, null, "Coordinator", "SUMMARY",
                "inputJson={\"prompt\":\"secret\"} /Users/alice/run resultJson", now);
        ProjectDiscussionMessage member = discussion(102L, 42L, "Alice", "TEXT",
                "please check stack trace at com.aipal.Secret.run(Secret.java:1)", now);
        when(discussionMessageMapper.selectList(any())).thenReturn(List.of(coordinator, member));

        ProjectAssignmentEvent event = new ProjectAssignmentEvent();
        event.setId(201L);
        event.setTenantId(1L);
        event.setProjectKey("proj-1");
        event.setEventType("ASSIGNED");
        event.setFromAssigneeUserId(null);
        event.setToAssigneeUserId(42L);
        event.setFromStatus(null);
        event.setToStatus("ASSIGNED");
        event.setReason("definitionSnapshot /Users/alice/payload");
        event.setCreateTime(now);
        when(assignmentEventMapper.selectList(any())).thenReturn(List.of(event));
        when(memoryProjectService.listWorkspaceMentionableUsers("proj-1")).thenReturn(List.of(
                new MemoryProjectService.ProjectMemberView(42L, "alice", "Alice /Users/alice", "MEMBER")));

        SafeProjectContext context = service.build("proj-1");
        String combined = context.toString() + "\n" + JSON.writeValueAsString(context);

        for (String marker : List.of("/Users/", "C:\\", "inputJson", "resultJson",
                "definitionSnapshot", "prompt", "payload", "apiKey", "stack trace")) {
            assertFalse(combined.toLowerCase(Locale.ROOT).contains(marker.toLowerCase(Locale.ROOT)),
                    () -> "safe context leaked marker " + marker + ": " + combined);
        }
        assertEquals("proj-1", context.projectKey());
        assertEquals(1, context.pipelines().size());
        assertEquals(1, context.runs().size());
        assertEquals(1, context.risks().size());
    }

    @Test
    void discussionSenderTypeIsDerivedFromAuthorAndListsAreCapped() {
        List<ProjectDiscussionMessage> messages = new ArrayList<>();
        LocalDateTime now = LocalDateTime.of(2026, 7, 28, 10, 0);
        for (int index = 0; index < 60; index++) {
            messages.add(discussion(100L + index, index % 2 == 0 ? null : 42L,
                    index % 2 == 0 ? "Coordinator" : "Alice", "TEXT", "message-" + index, now));
        }
        when(discussionMessageMapper.selectList(any())).thenReturn(messages);

        List<ProjectAssignmentEvent> events = new ArrayList<>();
        for (int index = 0; index < 25; index++) {
            ProjectAssignmentEvent event = new ProjectAssignmentEvent();
            event.setId(200L + index);
            event.setTenantId(1L);
            event.setProjectKey("proj-1");
            event.setEventType("ASSIGNED");
            event.setToAssigneeUserId(42L);
            event.setToStatus("ASSIGNED");
            event.setCreateTime(now);
            events.add(event);
        }
        when(assignmentEventMapper.selectList(any())).thenReturn(events);

        SafeProjectContext context = service.build("proj-1");

        assertEquals(50, context.discussions().size());
        assertEquals(20, context.assignmentEvents().size());
        assertEquals("COORDINATOR", context.discussions().get(0).senderType());
        assertEquals("USER", context.discussions().get(1).senderType());
    }

    @Test
    void failsClosedWhenCurrentUserIsNotAnActiveMember() {
        when(memoryProjectService.requireAccessibleWorkspaceProject("proj-1"))
                .thenThrow(ErrorCode.PROJECT_ACTIVE_MEMBER_REQUIRED.ex());

        BizException error = assertThrows(BizException.class, () -> service.build("proj-1"));

        assertEquals(ErrorCode.PROJECT_ACTIVE_MEMBER_REQUIRED.getCode(), error.getCode());
    }

    @Test
    void rejectsBlankProjectKey() {
        BizException error = assertThrows(BizException.class, () -> service.build(" "));

        assertEquals(ErrorCode.PROJECT_KEY_REQUIRED.getCode(), error.getCode());
    }

    private ProjectWarRoomDtos.WarRoomResponse emptyWarRoom() {
        return new ProjectWarRoomDtos.WarRoomResponse(
                new ProjectWarRoomDtos.ProjectWorkspaceView(7L, "proj-1", "Project One",
                        "CUSTOMER_PROJECT", 9L, "ACTIVE", LocalDateTime.now()),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new ProjectWarRoomDtos.WarRoomSummary(0, 0, 0, 0, "GREEN"), false);
    }

    private ProjectDiscussionMessage discussion(Long id, Long senderUserId, String senderName,
                                                String messageType, String content, LocalDateTime createTime) {
        ProjectDiscussionMessage message = new ProjectDiscussionMessage();
        message.setId(id);
        message.setTenantId(1L);
        message.setProjectKey("proj-1");
        message.setSenderUserId(senderUserId);
        message.setSenderName(senderName);
        message.setMessageType(messageType);
        message.setContent(content);
        message.setCreateTime(createTime);
        return message;
    }
}
