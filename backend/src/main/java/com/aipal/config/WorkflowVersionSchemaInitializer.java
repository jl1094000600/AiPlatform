package com.aipal.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Ensures ai_workflow_version exists and run rows can bind to immutable snapshots.
 */
@Component
@ConditionalOnProperty(name = "aipal.schema-initialization.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class WorkflowVersionSchemaInitializer {
    private final DataSource dataSource;

    @PostConstruct
    public void initialize() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ai_workflow_version (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        workflow_id BIGINT NOT NULL,
                        version_no INT NOT NULL,
                        definition_json JSON NOT NULL,
                        layout_json JSON DEFAULT NULL,
                        definition_hash VARCHAR(64) NOT NULL,
                        status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
                        published_by BIGINT DEFAULT NULL,
                        publish_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                        create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                        update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        is_deleted TINYINT NOT NULL DEFAULT 0,
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_workflow_version (tenant_id, workflow_id, version_no),
                        KEY idx_workflow_version_status (tenant_id, workflow_id, status, version_no)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
            addColumnIfMissing(connection, statement, "ai_workflow_execution", "version_id",
                    "ALTER TABLE ai_workflow_execution ADD COLUMN version_id BIGINT DEFAULT NULL");
            addColumnIfMissing(connection, statement, "ai_workflow_execution", "definition_hash",
                    "ALTER TABLE ai_workflow_execution ADD COLUMN definition_hash VARCHAR(64) DEFAULT NULL");
            addColumnIfMissing(connection, statement, "ai_workflow", "project_key",
                    "ALTER TABLE ai_workflow ADD COLUMN project_key VARCHAR(128) DEFAULT NULL");
            addColumnIfMissing(connection, statement, "ai_workflow", "layout_json",
                    "ALTER TABLE ai_workflow ADD COLUMN layout_json JSON DEFAULT NULL");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ai_workflow_node_run (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        execution_id VARCHAR(64) NOT NULL,
                        node_id VARCHAR(64) NOT NULL,
                        node_type VARCHAR(32) NOT NULL,
                        status VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
                        attempt INT NOT NULL DEFAULT 1,
                        input_digest VARCHAR(64) DEFAULT NULL,
                        output_digest VARCHAR(64) DEFAULT NULL,
                        duration_ms BIGINT DEFAULT NULL,
                        error_message VARCHAR(2000) DEFAULT NULL,
                        create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                        update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        is_deleted TINYINT NOT NULL DEFAULT 0,
                        PRIMARY KEY (id),
                        KEY idx_workflow_node_run_exec (tenant_id, execution_id, id),
                        KEY idx_workflow_node_run_node (tenant_id, execution_id, node_id, attempt)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize workflow version schema: " + e.getMessage(), e);
        }
    }

    private void addColumnIfMissing(Connection connection, Statement statement, String table,
                                    String column, String alterSql) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet columns = metadata.getColumns(connection.getCatalog(), null, table, column)) {
            if (!columns.next()) {
                statement.executeUpdate(alterSql);
            }
        }
    }
}
