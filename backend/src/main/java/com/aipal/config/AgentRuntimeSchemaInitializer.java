package com.aipal.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Creates the durable on-demand agent runtime schema in each environment, idempotently. */
@Component
@ConditionalOnProperty(name = "aipal.schema-initialization.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class AgentRuntimeSchemaInitializer {

    private final DataSource dataSource;

    @PostConstruct
    public void initialize() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            createRun(statement);
            createStep(statement);
            createTask(statement);
            createMemorySnapshot(statement);
            createArtifact(statement);
            createExecutionSnapshot(statement);
            createRunEvent(statement);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize agent runtime schema: " + e.getMessage(), e);
        }
    }

    private void createRun(Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS agent_run (
                    id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL, project_id BIGINT DEFAULT NULL,
                    project_key VARCHAR(128) DEFAULT NULL, owner_user_id BIGINT DEFAULT NULL,
                    business_type VARCHAR(64) NOT NULL, business_id VARCHAR(128) NOT NULL,
                    agent_id BIGINT NOT NULL, agent_version_id BIGINT NOT NULL, definition_snapshot LONGTEXT NOT NULL,
                    definition_hash VARCHAR(64) NOT NULL, idempotency_key VARCHAR(128) NOT NULL, status VARCHAR(32) NOT NULL,
                    input_json LONGTEXT DEFAULT NULL, result_json LONGTEXT DEFAULT NULL, trace_id VARCHAR(96) DEFAULT NULL,
                    memory_trace_id VARCHAR(96) DEFAULT NULL, max_steps INT NOT NULL DEFAULT 8,
                    max_child_tasks INT NOT NULL DEFAULT 5, max_total_tokens INT NOT NULL DEFAULT 8000,
                    total_tokens INT NOT NULL DEFAULT 0, error_message VARCHAR(2048) DEFAULT NULL,
                    start_time DATETIME DEFAULT NULL, end_time DATETIME DEFAULT NULL, version INT NOT NULL DEFAULT 1,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP, update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    is_deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (id),
                    UNIQUE KEY uk_agent_run_idempotency (tenant_id, idempotency_key),
                    KEY idx_agent_run_project_status (tenant_id, project_key, status, create_time),
                    KEY idx_agent_run_owner_status (tenant_id, owner_user_id, status, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void createStep(Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS agent_step (
                    id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL, run_id BIGINT NOT NULL,
                    parent_step_id BIGINT DEFAULT NULL, step_no INT NOT NULL, step_type VARCHAR(32) NOT NULL,
                    status VARCHAR(32) NOT NULL, tool_name VARCHAR(128) DEFAULT NULL,
                    tool_idempotency_key VARCHAR(128) DEFAULT NULL, input_json LONGTEXT DEFAULT NULL,
                    output_json LONGTEXT DEFAULT NULL, trace_id VARCHAR(96) DEFAULT NULL,
                    input_tokens INT NOT NULL DEFAULT 0, output_tokens INT NOT NULL DEFAULT 0,
                    error_message VARCHAR(2048) DEFAULT NULL, start_time DATETIME DEFAULT NULL, end_time DATETIME DEFAULT NULL,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP, update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    is_deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (id), UNIQUE KEY uk_agent_step_no (run_id, step_no),
                    KEY idx_agent_step_run (tenant_id, run_id, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void createTask(Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS agent_task (
                    id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL, run_id BIGINT NOT NULL,
                    parent_task_id BIGINT DEFAULT NULL, task_type VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL,
                    payload_json LONGTEXT DEFAULT NULL, attempt_count INT NOT NULL DEFAULT 0,
                    max_attempts INT NOT NULL DEFAULT 3, lease_owner VARCHAR(128) DEFAULT NULL,
                    lease_until DATETIME DEFAULT NULL, available_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    start_time DATETIME DEFAULT NULL, end_time DATETIME DEFAULT NULL, error_message VARCHAR(2048) DEFAULT NULL,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP, update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    is_deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (id),
                    KEY idx_agent_task_claim (status, available_at, lease_until),
                    KEY idx_agent_task_claim_tenant (tenant_id, status, task_type, parent_task_id, available_at, id),
                    KEY idx_agent_task_run (tenant_id, run_id, status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void createMemorySnapshot(Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS agent_memory_snapshot (
                    id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL, run_id BIGINT NOT NULL,
                    snapshot_version INT NOT NULL DEFAULT 1, memory_id BIGINT DEFAULT NULL, memory_version INT DEFAULT NULL,
                    memory_code VARCHAR(64) NOT NULL, source_type VARCHAR(32) DEFAULT NULL, scope_type VARCHAR(32) DEFAULT NULL,
                    token_count INT NOT NULL DEFAULT 0, policy_version INT DEFAULT NULL, trace_id VARCHAR(96) DEFAULT NULL,
                    content_summary TEXT DEFAULT NULL, create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    is_deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (id),
                    KEY idx_agent_snapshot_run (tenant_id, run_id, snapshot_version, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void createArtifact(Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS agent_artifact (
                    id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL, run_id BIGINT NOT NULL,
                    step_id BIGINT DEFAULT NULL, artifact_type VARCHAR(64) NOT NULL, title VARCHAR(256) NOT NULL,
                    storage_path VARCHAR(1024) DEFAULT NULL, content_json LONGTEXT DEFAULT NULL,
                    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT', create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    is_deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (id),
                    KEY idx_agent_artifact_run (tenant_id, run_id, artifact_type, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void createExecutionSnapshot(Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS agent_run_execution_snapshot (
                    id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL, run_id BIGINT NOT NULL,
                    snapshot_format VARCHAR(32) NOT NULL, key_id VARCHAR(64) NOT NULL, iv_b64 VARCHAR(64) NOT NULL,
                    ciphertext_b64 LONGTEXT NOT NULL, plaintext_hash VARCHAR(64) NOT NULL,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP, is_deleted TINYINT NOT NULL DEFAULT 0,
                    PRIMARY KEY (id), UNIQUE KEY uk_agent_run_execution_snapshot (run_id),
                    KEY idx_agent_execution_snapshot_tenant (tenant_id, run_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void createRunEvent(Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS agent_run_event (
                    id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL, run_id BIGINT NOT NULL,
                    from_status VARCHAR(32) DEFAULT NULL, to_status VARCHAR(32) NOT NULL,
                    actor_user_id BIGINT DEFAULT NULL, actor_name VARCHAR(128) DEFAULT NULL,
                    reason VARCHAR(512) DEFAULT NULL, trace_id VARCHAR(96) DEFAULT NULL,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP, is_deleted TINYINT NOT NULL DEFAULT 0,
                    PRIMARY KEY (id), KEY idx_agent_run_event_run (tenant_id, run_id, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }
}
