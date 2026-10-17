package com.aipal.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.FileSystemResource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ProjectCollaborationSchemaInitializerTest {
    private static final List<String> TABLES = List.of("project_discussion_message", "project_task_draft", "project_assignment", "project_assignment_event");

    private DriverManagerDataSource database() {
        return new DriverManagerDataSource("jdbc:h2:mem:schema-" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
    }

    @Test
    void fourTenantTablesAndIndexesAreIdempotent() throws Exception {
        var dataSource = database();
        var initializer = new ProjectCollaborationSchemaInitializer(dataSource);
        initializer.initialize();
        initializer.initialize();
        try (Connection connection = dataSource.getConnection()) {
            for (String table : TABLES) {
                Map<String, String> columns = columns(connection, table);
                assertTrue(columns.containsKey("tenant_id"), table);
                assertTrue(columns.containsKey("create_time"), table);
            }
            assertEquals(List.of("tenant_id", "target_type", "target_id"),
                    indexes(connection, "project_assignment").get("uk_project_assignment_target"));
            assertTrue(indexes(connection, "project_assignment").containsKey("idx_project_assignment_mine"));
            assertTrue(columns(connection, "project_assignment_event").containsKey("project_key"));
        }
    }

    @Test
    void sameTargetCannotBeDuplicatedByChangingProjectAndTenantIsolationRemains() {
        var dataSource = database();
        new ProjectCollaborationSchemaInitializer(dataSource).initialize();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String insert = "INSERT INTO project_assignment (tenant_id, project_key, target_type, target_id, assignee_user_id, created_by) VALUES (?, ?, 'AGENT_RUN', 10, 2, 1)";
        jdbc.update(insert, 1, "project-a");
        assertThrows(org.springframework.dao.DuplicateKeyException.class, () -> jdbc.update(insert, 1, "project-a"));
        assertThrows(org.springframework.dao.DuplicateKeyException.class, () -> jdbc.update(insert, 1, "project-b"));
        jdbc.update(insert, 2, "project-a");
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM project_assignment", Integer.class));
    }

    @Test
    void manualSqlAndInitializerHaveIdenticalColumnsAndIndexes() throws Exception {
        var generated = database();
        var manual = database();
        new ProjectCollaborationSchemaInitializer(generated).initialize();
        try (Connection connection = manual.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("sql/schema-project-collaboration.sql"));
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("sql/schema-project-collaboration.sql"));
        }
        try (Connection a = generated.getConnection(); Connection b = manual.getConnection()) {
            for (String table : TABLES) {
                assertEquals(columns(a, table), columns(b, table), table + " columns");
                assertEquals(indexes(a, table), indexes(b, table), table + " indexes");
            }
        }
    }

    private Map<String, String> columns(Connection connection, String table) throws SQLException {
        Map<String, String> columns = new TreeMap<>();
        try (ResultSet rows = connection.getMetaData().getColumns(null, null, table, null)) {
            while (rows.next()) columns.put(rows.getString("COLUMN_NAME"), rows.getString("TYPE_NAME") + ":" + rows.getInt("COLUMN_SIZE") + ":" + rows.getInt("NULLABLE"));
        }
        return columns;
    }

    private Map<String, List<String>> indexes(Connection connection, String table) throws SQLException {
        Map<String, SortedMap<Integer, String>> ordered = new TreeMap<>();
        try (ResultSet rows = connection.getMetaData().getIndexInfo(null, null, table, false, false)) {
            while (rows.next()) {
                String index = rows.getString("INDEX_NAME");
                if (index == null || index.toLowerCase().startsWith("primary_key")) continue;
                // H2 names the backing index of a named UNIQUE KEY with an internal suffix.
                String normalized = index.toLowerCase().replaceFirst("_index_[a-z0-9]+$", "");
                ordered.computeIfAbsent(normalized, ignored -> new TreeMap<>())
                        .put(rows.getInt("ORDINAL_POSITION"), rows.getString("COLUMN_NAME"));
            }
        }
        Map<String, List<String>> result = new TreeMap<>();
        ordered.forEach((name, value) -> result.put(name, List.copyOf(value.values())));
        return result;
    }
}
