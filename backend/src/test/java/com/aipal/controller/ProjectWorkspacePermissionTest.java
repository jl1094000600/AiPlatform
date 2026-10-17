package com.aipal.controller;

import com.aipal.dto.ProjectCollaborationDtos;
import com.aipal.dto.ProjectWarRoomDtos;
import com.aipal.security.RequirePermission;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;

class ProjectWorkspacePermissionTest {
    @Test void readsAndManagementKeepViewGateForProjectRoleOrGlobalManagement() throws Exception {
        permission(ProjectCollaborationController.class.getMethod("list", String.class), "project-workspace:view");
        permission(ProjectCollaborationController.class.getMethod("mine"), "project-workspace:view");
        permission(ProjectCollaborationController.class.getMethod("assign", String.class, ProjectCollaborationDtos.AssignRequest.class), "project-workspace:view");
        permission(ProjectCollaborationController.class.getMethod("reassign", String.class, Long.class, ProjectCollaborationDtos.ReassignRequest.class), "project-workspace:view");
        for (Method method : ProjectWarRoomController.class.getDeclaredMethods()) {
            RequirePermission annotation = method.getAnnotation(RequirePermission.class);
            if (annotation != null) assertTrue(SetHolder.VALID.contains(annotation.value()), method.getName());
        }
    }
    @Test void acceptingCompletingAndTeamRunCreationRequireExecute() throws Exception {
        permission(ProjectCollaborationController.class.getMethod("accept", String.class, Long.class, ProjectCollaborationDtos.AssignmentActionRequest.class), "project-workspace:execute");
        permission(ProjectCollaborationController.class.getMethod("complete", String.class, Long.class, ProjectCollaborationDtos.AssignmentActionRequest.class), "project-workspace:execute");
        Method teamRun = java.util.Arrays.stream(ProjectWarRoomController.class.getDeclaredMethods()).filter(method -> java.util.Arrays.asList(method.getParameterTypes()).contains(ProjectWarRoomDtos.TeamAgentRunRequest.class)).findFirst().orElseThrow();
        permission(teamRun, "project-workspace:execute");
    }
    private void permission(Method method, String value) { assertNotNull(method.getAnnotation(RequirePermission.class)); assertEquals(value, method.getAnnotation(RequirePermission.class).value()); }
    private static class SetHolder { static final java.util.Set<String> VALID = java.util.Set.of("project-workspace:view", "project-workspace:execute"); }
}
