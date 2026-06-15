package com.aipal.controller;

import com.aipal.security.RequirePermission;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AlertControllerPermissionTest {

    @Test
    void queryEndpointsRemainReadOnlyAndMutationsRequireManagePermission() throws Exception {
        Map<String, String> expectedPermissions = Map.of(
                "rules", "alert:view",
                "events", "alert:view",
                "createRule", "alert:manage",
                "updateRule", "alert:manage",
                "deleteRule", "alert:manage",
                "acknowledge", "alert:manage",
                "evaluate", "alert:manage"
        );

        for (Map.Entry<String, String> entry : expectedPermissions.entrySet()) {
            Method method = findMethod(entry.getKey());
            assertEquals(entry.getValue(), method.getAnnotation(RequirePermission.class).value(), entry.getKey());
        }
    }

    private Method findMethod(String methodName) {
        for (Method method : AlertController.class.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        throw new AssertionError("Missing controller method: " + methodName);
    }
}
