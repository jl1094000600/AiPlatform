package com.aipal.service;

import com.aipal.common.BizException;
import com.aipal.dto.ProjectCollaborationDtos.*;
import com.aipal.entity.ProjectAssignment;
import com.aipal.mapper.ProjectAssignmentMapper;
import com.aipal.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class ProjectAssignmentDatabaseTest {
    private ProjectCollaborationDatabaseFixture db;

    @BeforeEach void setup() throws Exception { db = new ProjectCollaborationDatabaseFixture(); db.seedRun(10, 1, "project-a"); }
    @AfterEach void cleanup() { TenantContext.clear(); }

    private AssignmentView assign(long user) { return db.assignments.assign("project-a", new AssignRequest("AGENT_RUN", 10L, user, "  release owner  ")); }
    private void error(int code, Runnable action) { assertEquals(code, assertThrows(BizException.class, action::run).getCode()); }
    private void as(long user, String... permissions) { ProjectCollaborationDatabaseFixture.context(user, 1, false, permissions); }

    @ParameterizedTest
    @ValueSource(strings = {"AGENT_RUN", "TASK_DRAFT", "AUTOMATION_APPROVAL"})
    void allTargetTypesPersistSafeAssignmentEventAndAudit(String type) {
        if (type.equals("TASK_DRAFT")) db.jdbc.update("INSERT INTO project_task_draft (id, tenant_id, project_key, source_type, title, created_by) VALUES (10, 1, 'project-a', 'TEST', 'Draft', 1)");
        if (type.equals("AUTOMATION_APPROVAL")) {
            db.jdbc.update("INSERT INTO automation_pipeline (id, tenant_id, project_key) VALUES (20, 1, 'project-a')");
            db.jdbc.update("INSERT INTO automation_approval (id, tenant_id, pipeline_id) VALUES (10, 1, 20)");
        }
        AssignmentView view = db.assignments.assign("project-a", new AssignRequest(type, 10L, 2L, "  release owner  "));
        assertEquals("ASSIGNED", view.status()); assertEquals(0, view.version());
        assertTrue(view.canReassign()); assertFalse(view.canAccept());
        assertEquals(1, db.count("project_assignment_event")); assertEquals(1, db.count("sys_audit_log"));
        var event = db.jdbc.queryForMap("SELECT * FROM project_assignment_event");
        assertEquals(1L, ((Number) event.get("tenant_id")).longValue());
        assertEquals("project-a", event.get("project_key")); assertEquals("ASSIGNED", event.get("event_type"));
        assertEquals(1L, ((Number) event.get("actor_user_id")).longValue());
        assertEquals("release owner", event.get("reason"));
        assertNull(event.get("from_assignee_user_id")); assertEquals(2L, ((Number) event.get("to_assignee_user_id")).longValue());
        var audit = db.jdbc.queryForMap("SELECT * FROM sys_audit_log");
        assertEquals(type + ":10", audit.get("resource_code")); assertEquals(1, ((Number) audit.get("result")).intValue());
        assertTrue(audit.get("after_value").toString().contains("\"projectKey\":\"project-a\""));
        for (String secret : List.of("private prompt", "private output", "/Users/", "stack trace", "inputJson", "resultJson")) assertFalse(view.toString().contains(secret));
    }

    @Test void retrySameAssigneeIsIdempotentDifferentAssigneeConflicts() {
        var first = assign(2); var retry = assign(2);
        assertEquals(first.id(), retry.id()); error(409, () -> assign(3));
        assertEquals(1, db.count("project_assignment")); assertEquals(1, db.count("project_assignment_event")); assertEquals(1, db.count("sys_audit_log"));
    }

    @Test void concurrentSameTargetCreatesExactlyOneAssignmentEventAndAudit() throws Exception {
        var context = TenantContext.get(); var barrier = new CyclicBarrier(2); var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Long> request = () -> { TenantContext.set(context); try { barrier.await(); return assign(2).id(); } finally { TenantContext.clear(); } };
            Future<Long> a = executor.submit(request), b = executor.submit(request);
            assertEquals(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
            assertEquals(1, db.count("project_assignment")); assertEquals(1, db.count("project_assignment_event")); assertEquals(1, db.count("sys_audit_log"));
        } finally { executor.shutdownNow(); }
    }

    @Test void acceptCompleteStateMachineIsVersionedAndDoesNotCompleteUnderlyingRun() {
        var assigned = assign(2); as(2, "project-workspace:view", "project-workspace:execute");
        error(409, () -> db.assignments.complete("project-a", assigned.id(), new AssignmentActionRequest(0, null)));
        var accepted = db.assignments.accept("project-a", assigned.id(), new AssignmentActionRequest(0, "take over"));
        assertEquals("ACCEPTED", accepted.status()); assertEquals(1, accepted.version()); assertTrue(accepted.canComplete()); assertFalse(accepted.canAccept());
        var completed = db.assignments.complete("project-a", assigned.id(), new AssignmentActionRequest(1, "done"));
        assertEquals("COMPLETED", completed.status()); assertEquals(2, completed.version());
        assertFalse(completed.canReassign()); assertFalse(completed.canComplete());
        error(409, () -> db.assignments.accept("project-a", completed.id(), new AssignmentActionRequest(2, null)));
        error(409, () -> db.assignments.complete("project-a", completed.id(), new AssignmentActionRequest(2, null)));
        assertEquals("QUEUED", db.jdbc.queryForObject("SELECT status FROM agent_run WHERE id=10", String.class));
        assertEquals(3, db.count("project_assignment_event")); assertEquals(3, db.count("sys_audit_log"));
    }

    @Test void managerCannotActForAnotherAssigneeAndExecutePermissionIsMandatory() {
        var view = assign(2);
        error(403, () -> db.assignments.accept("project-a", view.id(), new AssignmentActionRequest(0, null)));
        as(2, "project-workspace:view");
        error(403, () -> db.assignments.accept("project-a", view.id(), new AssignmentActionRequest(0, null)));
        assertEquals(1, db.count("project_assignment_event"));
    }

    @Test void reassignResetsAcceptanceAndSameAssigneeDoesNotCreateFakeEvents() {
        var view = assign(2); as(2, "project-workspace:view", "project-workspace:execute");
        db.assignments.accept("project-a", view.id(), new AssignmentActionRequest(0, null));
        as(1, "project-workspace:view", "project-workspace:execute");
        var unchanged = db.assignments.reassign("project-a", view.id(), new ReassignRequest(2L, 1, "same"));
        assertEquals(1, unchanged.version()); assertEquals(2, db.count("project_assignment_event"));
        var reassigned = db.assignments.reassign("project-a", view.id(), new ReassignRequest(3L, 1, "handover"));
        assertEquals("ASSIGNED", reassigned.status()); assertEquals(2, reassigned.version());
        var event = db.jdbc.queryForMap("SELECT * FROM project_assignment_event WHERE event_type='REASSIGNED'");
        assertEquals("ACCEPTED", event.get("from_status")); assertEquals("ASSIGNED", event.get("to_status"));
        assertEquals(2L, ((Number) event.get("from_assignee_user_id")).longValue()); assertEquals(3L, ((Number) event.get("to_assignee_user_id")).longValue());
        as(2, "project-workspace:view", "project-workspace:execute");
        error(403, () -> db.assignments.complete("project-a", view.id(), new AssignmentActionRequest(2, null)));
    }

    @ParameterizedTest @ValueSource(strings={"COMPLETED", "CANCELLED"})
    void terminalAssignmentsCannotBeReopened(String status) {
        var view = assign(2); db.jdbc.update("UPDATE project_assignment SET status=? WHERE id=?", status, view.id());
        error(409, () -> db.assignments.reassign("project-a", view.id(), new ReassignRequest(3L, 0, "restart")));
        assertEquals(status, db.jdbc.queryForObject("SELECT status FROM project_assignment WHERE id=?", String.class, view.id()));
        assertEquals(1, db.count("project_assignment_event"));
    }

    @Test void staleVersionDoesNotMutateOrAppendSuccessfulAudit() {
        var view = assign(2); db.assignments.reassign("project-a", view.id(), new ReassignRequest(3L, 0, "first"));
        error(409, () -> db.assignments.reassign("project-a", view.id(), new ReassignRequest(2L, 0, "stale")));
        assertEquals(3L, db.jdbc.queryForObject("SELECT assignee_user_id FROM project_assignment", Long.class));
        assertEquals(2, db.count("project_assignment_event")); assertEquals(2, db.count("sys_audit_log"));
    }

    @Test void mapperCompareAndSetRejectsSecondUpdateAndWrongTenantProjectOrDeletedRow() {
        var view = assign(2); var mapper = db.mapper(ProjectAssignmentMapper.class);
        var update = new LambdaUpdateWrapper<ProjectAssignment>().eq(ProjectAssignment::getTenantId, 1L)
                .eq(ProjectAssignment::getProjectKey, "project-a").eq(ProjectAssignment::getId, view.id())
                .eq(ProjectAssignment::getVersion, 0).eq(ProjectAssignment::getStatus, "ASSIGNED")
                .set(ProjectAssignment::getAssigneeUserId, 3L).setSql("version = version + 1");
        assertEquals(1, mapper.update(null, update)); assertEquals(0, mapper.update(null, update));
        assertEquals(0, mapper.update(null, new LambdaUpdateWrapper<ProjectAssignment>().eq(ProjectAssignment::getTenantId, 2L).eq(ProjectAssignment::getId, view.id()).set(ProjectAssignment::getStatus, "ACCEPTED")));
        assertEquals(0, mapper.update(null, new LambdaUpdateWrapper<ProjectAssignment>().eq(ProjectAssignment::getProjectKey, "project-b").eq(ProjectAssignment::getId, view.id()).set(ProjectAssignment::getStatus, "ACCEPTED")));
        db.jdbc.update("UPDATE project_assignment SET is_deleted=1");
        assertEquals(0, mapper.update(null, new LambdaUpdateWrapper<ProjectAssignment>().eq(ProjectAssignment::getId, view.id()).set(ProjectAssignment::getStatus, "ACCEPTED")));
    }

    @Test void concurrentReassignHasOneWinnerAndOneConflict() throws Exception {
        var view = assign(2); var context = TenantContext.get(); var barrier = new CyclicBarrier(2); var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> request = () -> { TenantContext.set(context); try { barrier.await(); db.assignments.reassign("project-a", view.id(), new ReassignRequest(3L, 0, "race")); return 200; } catch (BizException conflict) { return conflict.getCode(); } finally { TenantContext.clear(); } };
            Future<Integer> a = executor.submit(request), b = executor.submit(request);
            assertEquals(Set.of(200, 409), Set.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)));
            assertEquals(1, db.jdbc.queryForObject("SELECT version FROM project_assignment", Integer.class));
            assertEquals(2, db.count("project_assignment_event")); assertEquals(2, db.count("sys_audit_log"));
        } finally { executor.shutdownNow(); }
    }

    @Test void failedEventRollsBackAssignmentAndFailedAuditRollsBackEventToo() {
        db.jdbc.execute("ALTER TABLE project_assignment_event ADD CONSTRAINT reject_events CHECK (event_type <> 'ASSIGNED')");
        assertThrows(RuntimeException.class, () -> assign(2));
        assertEquals(0, db.count("project_assignment")); assertEquals(0, db.count("project_assignment_event")); assertEquals(0, db.count("sys_audit_log"));
        db.jdbc.execute("ALTER TABLE project_assignment_event DROP CONSTRAINT reject_events");
        db.jdbc.execute("ALTER TABLE sys_audit_log ADD CONSTRAINT reject_audits CHECK (result <> 1)");
        assertThrows(RuntimeException.class, () -> assign(2));
        assertEquals(0, db.count("project_assignment")); assertEquals(0, db.count("project_assignment_event")); assertEquals(0, db.count("sys_audit_log"));
    }

    @Test void failedMutationEventRollsBackCompareAndSet() {
        var view = assign(2);
        db.jdbc.execute("ALTER TABLE project_assignment_event ADD CONSTRAINT reject_reassign CHECK (event_type <> 'REASSIGNED')");
        assertThrows(RuntimeException.class, () -> db.assignments.reassign("project-a", view.id(), new ReassignRequest(3L, 0, "fail")));
        assertEquals(0, db.jdbc.queryForObject("SELECT version FROM project_assignment", Integer.class));
        assertEquals(2L, db.jdbc.queryForObject("SELECT assignee_user_id FROM project_assignment", Long.class));
        assertEquals(1, db.count("project_assignment_event")); assertEquals(1, db.count("sys_audit_log"));
    }

    @ParameterizedTest @ValueSource(strings={"project", "tenant", "deleted"})
    void foreignAndDeletedRunsAreRejected(String mutation) {
        if (mutation.equals("project")) db.jdbc.update("UPDATE agent_run SET project_key='project-b'");
        if (mutation.equals("tenant")) db.jdbc.update("UPDATE agent_run SET tenant_id=2");
        if (mutation.equals("deleted")) db.jdbc.update("UPDATE agent_run SET is_deleted=1");
        error(400, () -> assign(2)); assertEquals(0, db.count("project_assignment"));
    }

    @ParameterizedTest @ValueSource(strings={"approvalTenant", "pipelineTenant", "pipelineProject", "pipelineDeleted"})
    void approvalMustBelongToCurrentTenantPipelineAndProject(String mutation) {
        db.jdbc.update("INSERT INTO automation_pipeline (id, tenant_id, project_key, is_deleted) VALUES (20, 1, 'project-a', 0)");
        db.jdbc.update("INSERT INTO automation_approval (id, tenant_id, pipeline_id) VALUES (10, 1, 20)");
        switch (mutation) {
            case "approvalTenant" -> db.jdbc.update("UPDATE automation_approval SET tenant_id=2");
            case "pipelineTenant" -> db.jdbc.update("UPDATE automation_pipeline SET tenant_id=2");
            case "pipelineProject" -> db.jdbc.update("UPDATE automation_pipeline SET project_key='project-b'");
            case "pipelineDeleted" -> db.jdbc.update("UPDATE automation_pipeline SET is_deleted=1");
        }
        error(400, () -> db.assignments.assign("project-a", new AssignRequest("AUTOMATION_APPROVAL", 10L, 2L, null)));
        assertEquals(0, db.count("project_assignment"));
    }

    @Test void ordinaryMemberCannotAssignButProjectManagerCanWithoutGlobalManage() {
        as(2, "project-workspace:view"); error(403, () -> assign(3));
        db.jdbc.update("UPDATE ai_memory_project_member SET member_role='MANAGER' WHERE user_id=2");
        assertEquals(3L, assign(3).assigneeUserId());
    }

    @Test void myAssignmentsFilterCurrentUserTenantAndRevokedProjectMembership() {
        var view = assign(2);
        db.jdbc.update("INSERT INTO project_assignment (tenant_id, project_key, target_type, target_id, assignee_user_id, created_by) VALUES (2, 'project-a', 'AGENT_RUN', 10, 2, 1)");
        as(2, "project-workspace:view", "project-workspace:execute");
        assertEquals(List.of(view.id()), db.assignments.listMine().stream().map(AssignmentView::id).toList());
        db.jdbc.update("UPDATE ai_memory_project_member SET status='INACTIVE' WHERE user_id=2 AND tenant_id=1");
        assertTrue(db.assignments.listMine().isEmpty());
        db.jdbc.update("UPDATE ai_memory_project_member SET status='ACTIVE' WHERE user_id=2");
        db.jdbc.update("UPDATE ai_memory_project SET status='ARCHIVED'");
        assertTrue(db.assignments.listMine().isEmpty());
    }

    @Test void deletedAssignmentsAndDisabledAssigneesHaveNoActionCapabilities() {
        var view = assign(2); as(2, "project-workspace:view", "project-workspace:execute");
        db.jdbc.update("UPDATE sys_user SET status=0 WHERE id=2");
        assertTrue(db.assignments.listMine().isEmpty());
        as(1, "project-workspace:view", "project-workspace:execute");
        var safe = db.assignments.listProject("project-a").getFirst();
        assertFalse(safe.canAccept()); assertFalse(safe.canComplete());
        db.jdbc.update("UPDATE project_assignment SET is_deleted=1 WHERE id=?", view.id());
        assertTrue(db.assignments.listProject("project-a").isEmpty());
        error(404, () -> db.assignments.reassign("project-a", view.id(), new ReassignRequest(3L, 0, null)));
    }
}
