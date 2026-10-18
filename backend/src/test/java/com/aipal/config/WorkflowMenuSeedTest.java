package com.aipal.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * /workflows must be reachable from server-driven menus, not only the frontend fallback nav.
 */
class WorkflowMenuSeedTest {

    @Test
    void seedsWorkflowMenuAndDeveloperPermission() throws Exception {
        var source = new DriverManagerDataSource(
                "jdbc:h2:mem:wf-menu-" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE sys_permission (id BIGINT AUTO_INCREMENT PRIMARY KEY, permission_code VARCHAR(128) UNIQUE, permission_name VARCHAR(128), resource_type VARCHAR(32))");
        jdbc.execute("CREATE TABLE sys_role (id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT, role_code VARCHAR(64), role_name VARCHAR(128), role_scope VARCHAR(32), is_deleted INT DEFAULT 0, UNIQUE (tenant_id, role_code))");
        jdbc.execute("CREATE TABLE sys_role_permission (id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT, role_id BIGINT, permission_id BIGINT, UNIQUE (tenant_id, role_id, permission_id))");
        jdbc.execute("CREATE TABLE sys_menu (id BIGINT AUTO_INCREMENT PRIMARY KEY, menu_code VARCHAR(128) UNIQUE, menu_name VARCHAR(128), path VARCHAR(255), icon VARCHAR(64), permission_code VARCHAR(128), parent_id BIGINT, sort_order INT)");

        var initializer = new TenantSecuritySchemaInitializer(source, "a".repeat(64));
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
            for (String role : Set.of("platform_admin", "tenant_admin", "developer", "reviewer", "readonly")) {
                jdbc.update("INSERT INTO sys_role (tenant_id, role_code, role_name, role_scope) VALUES (1, ?, ?, 'TENANT')", role, role);
                jdbc.update("INSERT INTO sys_role (tenant_id, role_code, role_name, role_scope) VALUES (2, ?, ?, 'TENANT')", role, role);
            }
            invoke(initializer, "seedPermissions", statement);
            invoke(initializer, "seedMenus", statement);
            invoke(initializer, "seedRoles", statement);
        }

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_menu WHERE menu_code='workflows' AND path='/workflows' AND permission_code='workflow:manage'",
                Integer.class));
        for (long tenant : new long[]{1, 2}) {
            assertTrue(developerHasWorkflowManage(jdbc, tenant), "tenant " + tenant + " developer missing workflow:manage");
        }
    }

    private boolean developerHasWorkflowManage(JdbcTemplate jdbc, long tenantId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id JOIN sys_permission p ON p.id=rp.permission_id "
                        + "WHERE rp.tenant_id=? AND r.tenant_id=? AND r.role_code='developer' AND p.permission_code='workflow:manage'",
                Integer.class, tenantId, tenantId);
        return count != null && count > 0;
    }

    private void invoke(Object target, String name, Statement statement) throws Exception {
        Method method = TenantSecuritySchemaInitializer.class.getDeclaredMethod(name, Statement.class);
        method.setAccessible(true);
        method.invoke(target, statement);
    }
}
