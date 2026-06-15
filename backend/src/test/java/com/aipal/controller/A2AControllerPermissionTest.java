package com.aipal.controller;

import com.aipal.security.RequirePermission;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.assertEquals;

class A2AControllerPermissionTest {

    @Test
    void mutatingA2AEndpointsRequireExplicitPermissions() throws Exception {
        assertPermission(A2AController.class.getMethod("sendMessage", com.aipal.dto.A2AMessage.class), "agent:invoke");
        assertPermission(A2AController.class.getMethod("refreshAgent", String.class), "agent:update");
        assertPermission(A2AGraphController.class.getMethod("exportGraph", String.class), "graph:manage");
    }

    private void assertPermission(Method method, String expected) {
        assertEquals(expected, method.getAnnotation(RequirePermission.class).value());
    }
}
