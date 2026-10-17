package com.aipal.service;

import com.aipal.common.BizException;
import com.aipal.dto.ProjectWarRoomDtos;
import com.aipal.entity.AgentRun;
import com.aipal.mapper.AutomationApprovalMapper;
import com.aipal.mapper.AutomationPipelineMapper;
import com.aipal.security.TenantContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProjectTeamRunTransactionTest {
    private ProjectCollaborationDatabaseFixture db;
    private AgentRunService runtime;
    private ProjectWarRoomService service;

    @BeforeEach void setup() throws Exception {
        db = new ProjectCollaborationDatabaseFixture();
        runtime = mock(AgentRunService.class);
        // The external runtime is replaced with a durable database insert, joined to the production
        // War Room transaction. Assignment, event and audit mappers remain the real production mappers.
        when(runtime.createOrReuse(any())).thenAnswer(invocation -> {
            AgentRunService.CreateRunCommand command = invocation.getArgument(0);
            if (db.jdbc.queryForObject("SELECT COUNT(*) FROM agent_run WHERE id=11", Integer.class) == 0) {
                db.seedRun(11, TenantContext.tenantId(), command.projectKey());
            }
            AgentRun run = new AgentRun(); run.setId(11L); run.setProjectKey(command.projectKey()); return run;
        });
        when(runtime.businessView(any())).thenReturn(new AgentRunService.RunView(11L, "project-a",
                "DELIVERY_REQUEST", "REQ-1", 3L, 1L, "QUEUED", "trace", null, 0,
                null, true, false, LocalDateTime.now(), null, null));
        service = db.transactional(new ProjectWarRoomService(db.memory, db.mapper(AutomationPipelineMapper.class),
                null, db.mapper(AutomationApprovalMapper.class), runtime, null, null, null, db.assignments));
    }
    @AfterEach void cleanup() { TenantContext.clear(); }
    private ProjectWarRoomDtos.TeamAgentRunRequest request(Long assignee) {
        return new ProjectWarRoomDtos.TeamAgentRunRequest(3L, "DELIVERY_REQUEST", "REQ-1",
                Map.of("projectKey", "untrusted-project", "instruction", "release"), assignee, "release owner");
    }
    private void error(int code, Runnable action) { assertEquals(code, assertThrows(BizException.class, action::run).getCode()); }

    @Test void structuredAssigneePersistsWithTrustedPathProjectAndRepeatIsIdempotent() {
        service.createTeamAgentRun("project-a", request(2L));
        service.createTeamAgentRun("project-a", request(2L));
        assertEquals(1, db.count("agent_run")); assertEquals(1, db.count("project_assignment"));
        assertEquals(1, db.count("project_assignment_event")); assertEquals(1, db.count("sys_audit_log"));
        assertEquals("project-a", db.jdbc.queryForObject("SELECT project_key FROM agent_run", String.class));
        assertEquals(2L, db.jdbc.queryForObject("SELECT assignee_user_id FROM project_assignment", Long.class));
        var captor = org.mockito.ArgumentCaptor.forClass(AgentRunService.CreateRunCommand.class);
        verify(runtime, times(2)).createOrReuse(captor.capture());
        assertTrue(captor.getAllValues().stream().allMatch(command -> command.projectKey().equals("project-a")));
    }

    @Test void unassignedRunNeedsExecutionButDoesNotNeedAssignmentManagement() {
        ProjectCollaborationDatabaseFixture.context(2, 1, false, "project-workspace:view", "project-workspace:execute");
        service.createTeamAgentRun("project-a", request(null));
        assertEquals(1, db.count("agent_run")); assertEquals(0, db.count("project_assignment"));
    }

    @ParameterizedTest @ValueSource(strings={"noExecute", "noManagement", "invalidAssignee", "removedCaller", "longReason"})
    void preflightFailureCreatesNoExecutionState(String failure) {
        ProjectWarRoomDtos.TeamAgentRunRequest request = request(2L);
        int code = 403;
        switch (failure) {
            case "noExecute" -> ProjectCollaborationDatabaseFixture.context(1, 1, false, "project-workspace:view");
            case "noManagement" -> ProjectCollaborationDatabaseFixture.context(2, 1, false, "project-workspace:view", "project-workspace:execute");
            case "invalidAssignee" -> { db.jdbc.update("UPDATE sys_user SET status=0 WHERE id=2"); code=400; }
            case "removedCaller" -> db.jdbc.update("UPDATE ai_memory_project_member SET status='INACTIVE' WHERE user_id=1");
            case "longReason" -> { request = new ProjectWarRoomDtos.TeamAgentRunRequest(3L, "DELIVERY_REQUEST", "REQ-1", Map.of(), 2L, "x".repeat(501)); code=400; }
        }
        var finalRequest = request;
        error(code, () -> service.createTeamAgentRun("project-a", finalRequest));
        verify(runtime, never()).createOrReuse(any()); assertEquals(0, db.count("agent_run"));
        assertEquals(0, db.count("project_assignment")); assertEquals(0, db.count("project_assignment_event"));
    }

    @ParameterizedTest @ValueSource(strings={"event", "audit"})
    void assignmentPersistenceFailureRollsBackTheNewRun(String persistence) {
        if (persistence.equals("event")) db.jdbc.execute("ALTER TABLE project_assignment_event ADD CONSTRAINT reject_event CHECK (event_type <> 'ASSIGNED')");
        else db.jdbc.execute("ALTER TABLE sys_audit_log ADD CONSTRAINT reject_audit CHECK (result <> 1)");
        assertThrows(RuntimeException.class, () -> service.createTeamAgentRun("project-a", request(2L)));
        assertEquals(0, db.count("agent_run")); assertEquals(0, db.count("project_assignment"));
        assertEquals(0, db.count("project_assignment_event")); assertEquals(0, db.count("sys_audit_log"));
    }

    @Test void retriedRunCannotSilentlyChangeExistingAssignee() {
        service.createTeamAgentRun("project-a", request(2L));
        error(409, () -> service.createTeamAgentRun("project-a", request(3L)));
        assertEquals(1, db.count("agent_run")); assertEquals(1, db.count("project_assignment_event"));
        assertEquals(2L, db.jdbc.queryForObject("SELECT assignee_user_id FROM project_assignment", Long.class));
    }
}
