package com.aipal.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TenantSecurityWorkspaceMigrationTest {
    @Test
    void realPermissionAndRoleSeedStatementsMigrateAllTenantRolesIdempotently() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:roles-" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE sys_permission (id BIGINT AUTO_INCREMENT PRIMARY KEY, permission_code VARCHAR(128) UNIQUE, permission_name VARCHAR(128), resource_type VARCHAR(32))");
        jdbc.execute("CREATE TABLE sys_role (id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT, role_code VARCHAR(64), role_name VARCHAR(128), role_scope VARCHAR(32), is_deleted INT DEFAULT 0, UNIQUE (tenant_id, role_code))");
        var initializer = new TenantSecuritySchemaInitializer(source, "a".repeat(64));
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
            invoke(initializer, "createTables", statement);
            for (String role : Set.of("platform_admin", "tenant_admin", "developer", "reviewer", "readonly")) {
                jdbc.update("INSERT INTO sys_role (tenant_id, role_code, role_name, role_scope) VALUES (2, ?, ?, 'TENANT')", role, role);
            }
            jdbc.update("INSERT INTO sys_role (tenant_id, role_code, role_name, role_scope, is_deleted) VALUES (3, 'developer', 'deleted', 'TENANT', 1)");
            invoke(initializer, "seedPermissions", statement); invoke(initializer, "seedRoles", statement);
            int permissionCount = jdbc.queryForObject("SELECT COUNT(*) FROM sys_permission", Integer.class);
            int grantCount = jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_permission", Integer.class);
            invoke(initializer, "seedPermissions", statement); invoke(initializer, "seedRoles", statement);
            assertEquals(permissionCount, jdbc.queryForObject("SELECT COUNT(*) FROM sys_permission", Integer.class));
            assertEquals(grantCount, jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_permission", Integer.class));
        }
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM sys_permission WHERE permission_code LIKE 'project-workspace:%'", Integer.class));
        for (long tenant : new long[]{1, 2}) {
            assertEquals(Set.of("project-workspace:view", "project-workspace:execute", "project-workspace:manage"), grants(jdbc, tenant, "platform_admin"));
            assertEquals(Set.of("project-workspace:view", "project-workspace:execute", "project-workspace:manage"), grants(jdbc, tenant, "tenant_admin"));
            assertEquals(Set.of("project-workspace:view", "project-workspace:execute"), grants(jdbc, tenant, "developer"));
            assertEquals(Set.of("project-workspace:view"), grants(jdbc, tenant, "reviewer"));
            assertEquals(Set.of("project-workspace:view"), grants(jdbc, tenant, "readonly"));
        }
        assertTrue(grants(jdbc, 3, "developer").isEmpty());
    }

    // MySQL UPDATE JOIN menu initialization is intentionally outside this H2 seed-statement test.
    private void invoke(Object target, String name, Statement statement) throws Exception {
        Method method = TenantSecuritySchemaInitializer.class.getDeclaredMethod(name, Statement.class);
        method.setAccessible(true); method.invoke(target, statement);
    }
    private Set<String> grants(JdbcTemplate jdbc, long tenantId, String role) {
        return Set.copyOf(jdbc.queryForList("SELECT p.permission_code FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id JOIN sys_permission p ON p.id=rp.permission_id WHERE rp.tenant_id=? AND r.tenant_id=? AND r.role_code=? AND p.permission_code LIKE 'project-workspace:%'", String.class, tenantId, tenantId, role));
    }
}
