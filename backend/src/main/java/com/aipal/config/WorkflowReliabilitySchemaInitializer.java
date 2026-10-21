package com.aipal.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * W4 reliability columns and append-only execution event table.
 */
@Component
@Slf4j
public class WorkflowReliabilitySchemaInitializer implements ApplicationRunner {

    private final DataSource dataSource;

    public WorkflowReliabilitySchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            addColumnIfMissing(connection, statement, "ai_workflow_execution", "owner",
                    "ALTER TABLE ai_workflow_execution ADD COLUMN owner VARCHAR(128) DEFAULT NULL");
            addColumnIfMissing(connection, statement, "ai_workflow_execution", "lease_until",
                    "ALTER TABLE ai_workflow_execution ADD COLUMN lease_until DATETIME DEFAULT NULL");
            addColumnIfMissing(connection, statement, "ai_workflow_execution", "cancel_requested",
                    "ALTER TABLE ai_workflow_execution ADD COLUMN cancel_requested INT DEFAULT 0");
            addColumnIfMissing(connection, statement, "ai_workflow_execution", "recover_count",
                    "ALTER TABLE ai_workflow_execution ADD COLUMN recover_count INT DEFAULT 0");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ai_workflow_execution_event (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT DEFAULT NULL,
                        execution_id VARCHAR(64) NOT NULL,
                        event_type VARCHAR(32) NOT NULL,
                        node_id VARCHAR(64) DEFAULT NULL,
                        message VARCHAR(1024) DEFAULT NULL,
                        is_deleted INT DEFAULT 0,
                        create_time DATETIME DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_wf_exec_event ON ai_workflow_execution_event (execution_id, id)");
        } catch (Exception e) {
            log.warn("Workflow reliability schema init skipped: {}", e.getMessage());
        }
    }

    private void addColumnIfMissing(Connection connection, Statement statement,
                                    String table, String column, String ddl) throws Exception {
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet rs = meta.getColumns(null, null, table, column)) {
            if (rs.next()) {
                return;
            }
        }
        try {
            statement.executeUpdate(ddl);
        } catch (Exception ignored) {
            // concurrent init
        }
    }
}
