package com.aipal.service;

import com.aipal.common.BizException;
import com.aipal.security.TenantContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class MemoryWorkspaceAuthorizationDatabaseTest {
    private ProjectCollaborationDatabaseFixture db;
    @BeforeEach void setup() throws Exception { db = new ProjectCollaborationDatabaseFixture(); }
    @AfterEach void cleanup() { TenantContext.clear(); }
    private void denied(int code, Runnable request) { assertEquals(code, assertThrows(BizException.class, request::run).getCode()); }

    @Test void memoryPolicyAndAutomationListDoNotGrantWorkspaceAccess() {
        ProjectCollaborationDatabaseFixture.context(1, 1, false, "memory:policy", "automation:list");
        denied(403, () -> db.memory.listAccessibleWorkspaces());
        denied(403, () -> db.memory.requireAccessibleWorkspaceProject("project-a"));
    }

    @ParameterizedTest @ValueSource(strings={"platformAdmin", "globalManage", "projectOwner"})
    void administrativeCapabilitiesCannotReviveRemovedMembers(String capability) {
        ProjectCollaborationDatabaseFixture.context(1, 1, capability.equals("platformAdmin"),
                "project-workspace:view", capability.equals("globalManage") ? "project-workspace:manage" : "memory:policy");
        db.jdbc.update("UPDATE ai_memory_project_member SET status='INACTIVE' WHERE user_id=1");
        assertTrue(db.memory.listAccessibleWorkspaces().isEmpty());
        denied(403, () -> db.memory.requireAccessibleWorkspaceProject("project-a"));
        denied(403, () -> db.memory.canManageWorkspaceProject("project-a"));
    }

    @ParameterizedTest @ValueSource(strings={"userDisabled", "userDeleted", "userMissing", "tenantInactive", "tenantMissing", "memberInactive", "memberDeleted", "memberWrongTenant"})
    void selectorAndAssignmentRejectEveryInvalidMemberCondition(String condition) {
        switch (condition) {
            case "userDisabled" -> db.jdbc.update("UPDATE sys_user SET status=0 WHERE id=2");
            case "userDeleted" -> db.jdbc.update("UPDATE sys_user SET is_deleted=1 WHERE id=2");
            case "userMissing" -> db.jdbc.update("DELETE FROM sys_user WHERE id=2");
            case "tenantInactive" -> db.jdbc.update("UPDATE sys_user_tenant SET status=0 WHERE user_id=2");
            case "tenantMissing" -> db.jdbc.update("DELETE FROM sys_user_tenant WHERE user_id=2");
            case "memberInactive" -> db.jdbc.update("UPDATE ai_memory_project_member SET status='INACTIVE' WHERE user_id=2");
            case "memberDeleted" -> db.jdbc.update("UPDATE ai_memory_project_member SET is_deleted=1 WHERE user_id=2");
            case "memberWrongTenant" -> db.jdbc.update("UPDATE ai_memory_project_member SET tenant_id=2 WHERE user_id=2");
        }
        assertFalse(db.memory.listWorkspaceMentionableUsers("project-a").stream().anyMatch(member -> member.userId()==2L));
        denied(400, () -> db.memory.requireActiveMember("project-a", 2L));
        assertFalse(db.memory.isActiveWorkspaceMember("project-a", 2L));
    }

    @Test void currentTenantMembershipWorksRegardlessOfDefaultTenantId() {
        assertEquals(99L, db.jdbc.queryForObject("SELECT default_tenant_id FROM sys_user WHERE id=2", Long.class));
        assertEquals(2L, db.memory.requireActiveMember("project-a", 2L).userId());
        assertTrue(db.memory.listWorkspaceMentionableUsers("project-a").stream().anyMatch(member -> member.userId()==2L));
    }

    @ParameterizedTest @ValueSource(strings={"OWNER", "MANAGER"})
    void projectRolesCanManageWithoutGlobalPermission(String role) {
        ProjectCollaborationDatabaseFixture.context(2, 1, false, "project-workspace:view");
        db.jdbc.update("UPDATE ai_memory_project_member SET member_role=? WHERE user_id=2", role);
        assertTrue(db.memory.canManageWorkspaceProject("project-a"));
    }

    @Test void ordinaryMemberWithGlobalManageCanManageButOtherProjectsRoleDoesNotLeak() {
        ProjectCollaborationDatabaseFixture.context(2, 1, false, "project-workspace:view");
        db.seedProject(2, "project-b", 1, 2); db.seedMember(1, 2, 2, "OWNER");
        assertFalse(db.memory.canManageWorkspaceProject("project-a"));
        ProjectCollaborationDatabaseFixture.context(2, 1, false, "project-workspace:view", "project-workspace:manage");
        assertTrue(db.memory.canManageWorkspaceProject("project-a"));
    }

    @Test void sameProjectKeyInAnotherTenantDoesNotLeak() {
        db.seedProject(2, "project-a", 2, 1); db.seedMember(2, 2, 2, "OWNER");
        ProjectCollaborationDatabaseFixture.context(2, 1, false, "project-workspace:view");
        assertEquals(1L, db.memory.requireAccessibleWorkspaceProject("project-a").getId());
        assertFalse(db.memory.canManageWorkspaceProject("project-a"));
        assertEquals(1, db.memory.listAccessibleWorkspaces().size());
    }

    @ParameterizedTest @ValueSource(strings={"ARCHIVED", "DELETED"})
    void archivedAndDeletedProjectsDisallowWorkspaceAndPersonalAccess(String mutation) {
        if (mutation.equals("ARCHIVED")) db.jdbc.update("UPDATE ai_memory_project SET status='ARCHIVED'");
        else db.jdbc.update("UPDATE ai_memory_project SET is_deleted=1");
        assertTrue(db.memory.listAccessibleWorkspaces().isEmpty());
        assertFalse(db.memory.isActiveWorkspaceMember("project-a", 1L));
        denied(404, () -> db.memory.requireAccessibleWorkspaceProject("project-a"));
    }
}
