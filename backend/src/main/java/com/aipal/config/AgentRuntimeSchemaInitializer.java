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
            createToolDefinition(statement);
            createToolPolicy(statement);
            createToolCall(statement);
            createContextSnapshot(statement);
            createRunStreamEvent(statement);
            createRunSpan(statement);
            ensureRuntimeV2Columns(connection, statement);
            seedBuiltinTools(statement);
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
                    memory_trace_id VARCHAR(96) DEFAULT NULL, loop_version VARCHAR(16) NOT NULL DEFAULT 'v1',
                    context_window_tokens INT NOT NULL DEFAULT 0, context_used_tokens INT NOT NULL DEFAULT 0,
                    compact_count INT NOT NULL DEFAULT 0, current_turn INT NOT NULL DEFAULT 0,
                    stop_reason VARCHAR(64) DEFAULT NULL, max_steps INT NOT NULL DEFAULT 8,
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
                    status VARCHAR(32) NOT NULL, tool_call_id BIGINT DEFAULT NULL, tool_name VARCHAR(128) DEFAULT NULL,
                    tool_idempotency_key VARCHAR(128) DEFAULT NULL, input_json LONGTEXT DEFAULT NULL,
                    output_json LONGTEXT DEFAULT NULL, trace_id VARCHAR(96) DEFAULT NULL,
                    permission_decision VARCHAR(32) DEFAULT NULL, risk_level VARCHAR(32) DEFAULT NULL,
                    compact_status VARCHAR(32) DEFAULT NULL, stream_event_count INT NOT NULL DEFAULT 0,
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

    private void createToolDefinition(Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS agent_tool_definition (
                    id BIGINT NOT NULL AUTO_INCREMENT, tool_name VARCHAR(128) NOT NULL,
                    description VARCHAR(512) DEFAULT NULL, input_schema LONGTEXT DEFAULT NULL,
                    output_schema LONGTEXT DEFAULT NULL, risk_level VARCHAR(32) NOT NULL,
                    concurrency_safe TINYINT NOT NULL DEFAULT 0, idempotent TINYINT NOT NULL DEFAULT 0,
                    timeout_seconds INT NOT NULL DEFAULT 30, requires_approval TINYINT NOT NULL DEFAULT 0,
                    tenant_scopes LONGTEXT DEFAULT NULL, status TINYINT NOT NULL DEFAULT 1,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    is_deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (id),
                    UNIQUE KEY uk_agent_tool_name (tool_name),
                    KEY idx_agent_tool_status_risk (status, risk_level, tool_name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void createToolPolicy(Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS agent_tool_policy (
                    id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL,
                    agent_id BIGINT NOT NULL DEFAULT 0, agent_version_id BIGINT NOT NULL DEFAULT 0,
                    tool_name VARCHAR(128) NOT NULL, decision VARCHAR(32) NOT NULL DEFAULT 'DENY',
                    risk_level VARCHAR(32) DEFAULT NULL, requires_approval TINYINT NOT NULL DEFAULT 0,
                    policy_config LONGTEXT DEFAULT NULL, status TINYINT NOT NULL DEFAULT 1,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    is_deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (id),
                    UNIQUE KEY uk_agent_tool_policy_scope (tenant_id, agent_id, agent_version_id, tool_name, status),
                    KEY idx_agent_tool_policy_lookup (tenant_id, agent_id, agent_version_id, tool_name, status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void createToolCall(Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS agent_tool_call (
                    id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL, run_id BIGINT NOT NULL,
                    step_id BIGINT DEFAULT NULL, call_id VARCHAR(96) NOT NULL, tool_name VARCHAR(128) NOT NULL,
                    risk_level VARCHAR(32) NOT NULL, permission_decision VARCHAR(32) DEFAULT NULL,
                    status VARCHAR(32) NOT NULL, idempotency_key VARCHAR(128) DEFAULT NULL,
                    input_json LONGTEXT DEFAULT NULL, output_json LONGTEXT DEFAULT NULL,
                    output_summary TEXT DEFAULT NULL, error_message VARCHAR(2048) DEFAULT NULL,
                    input_tokens INT NOT NULL DEFAULT 0, output_tokens INT NOT NULL DEFAULT 0,
                    start_time DATETIME DEFAULT NULL, end_time DATETIME DEFAULT NULL,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    is_deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (id),
                    UNIQUE KEY uk_agent_tool_call_id (tenant_id, call_id),
                    UNIQUE KEY uk_agent_tool_call_idempotency (tenant_id, idempotency_key),
                    KEY idx_agent_tool_call_run (tenant_id, run_id, create_time),
                    KEY idx_agent_tool_call_tool (tenant_id, tool_name, status, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void createContextSnapshot(Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS agent_context_snapshot (
                    id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL, run_id BIGINT NOT NULL,
                    snapshot_version INT NOT NULL, snapshot_type VARCHAR(32) NOT NULL,
                    context_summary LONGTEXT DEFAULT NULL, raw_context_hash VARCHAR(64) DEFAULT NULL,
                    input_tokens INT NOT NULL DEFAULT 0, output_tokens INT NOT NULL DEFAULT 0,
                    compact_reason VARCHAR(256) DEFAULT NULL, create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    is_deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (id),
                    UNIQUE KEY uk_agent_context_snapshot_version (run_id, snapshot_version),
                    KEY idx_agent_context_snapshot_run (tenant_id, run_id, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void createRunStreamEvent(Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS agent_run_stream_event (
                    id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL, run_id BIGINT NOT NULL,
                    event_type VARCHAR(64) NOT NULL, sequence_no BIGINT NOT NULL,
                    payload_json LONGTEXT DEFAULT NULL, visible_to_business TINYINT NOT NULL DEFAULT 1,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP, is_deleted TINYINT NOT NULL DEFAULT 0,
                    PRIMARY KEY (id), UNIQUE KEY uk_agent_run_stream_sequence (run_id, sequence_no),
                    KEY idx_agent_run_stream_run (tenant_id, run_id, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void createRunSpan(Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS agent_run_span (
                    id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL, run_id BIGINT NOT NULL,
                    parent_span_id BIGINT DEFAULT NULL, span_id VARCHAR(96) NOT NULL, trace_id VARCHAR(96) DEFAULT NULL,
                    span_type VARCHAR(32) NOT NULL, span_name VARCHAR(128) DEFAULT NULL,
                    step_id BIGINT DEFAULT NULL, tool_call_id BIGINT DEFAULT NULL, call_id VARCHAR(96) DEFAULT NULL,
                    status VARCHAR(32) NOT NULL, input_tokens INT NOT NULL DEFAULT 0, output_tokens INT NOT NULL DEFAULT 0,
                    summary VARCHAR(256) DEFAULT NULL, start_time DATETIME DEFAULT NULL, end_time DATETIME DEFAULT NULL,
                    duration_ms BIGINT DEFAULT NULL, create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    is_deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (id),
                    UNIQUE KEY uk_agent_run_span_id (span_id),
                    KEY idx_agent_run_span_run (tenant_id, run_id, start_time, id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void ensureRuntimeV2Columns(Connection connection, Statement statement) throws SQLException {
        addColumnIfMissing(connection, statement, "agent_run", "loop_version",
                "ALTER TABLE agent_run ADD COLUMN loop_version VARCHAR(16) NOT NULL DEFAULT 'v1' AFTER memory_trace_id");
        addColumnIfMissing(connection, statement, "agent_run", "context_window_tokens",
                "ALTER TABLE agent_run ADD COLUMN context_window_tokens INT NOT NULL DEFAULT 0 AFTER loop_version");
        addColumnIfMissing(connection, statement, "agent_run", "context_used_tokens",
                "ALTER TABLE agent_run ADD COLUMN context_used_tokens INT NOT NULL DEFAULT 0 AFTER context_window_tokens");
        addColumnIfMissing(connection, statement, "agent_run", "compact_count",
                "ALTER TABLE agent_run ADD COLUMN compact_count INT NOT NULL DEFAULT 0 AFTER context_used_tokens");
        addColumnIfMissing(connection, statement, "agent_run", "current_turn",
                "ALTER TABLE agent_run ADD COLUMN current_turn INT NOT NULL DEFAULT 0 AFTER compact_count");
        addColumnIfMissing(connection, statement, "agent_run", "stop_reason",
                "ALTER TABLE agent_run ADD COLUMN stop_reason VARCHAR(64) DEFAULT NULL AFTER current_turn");
        addColumnIfMissing(connection, statement, "agent_step", "tool_call_id",
                "ALTER TABLE agent_step ADD COLUMN tool_call_id BIGINT DEFAULT NULL AFTER status");
        addColumnIfMissing(connection, statement, "agent_step", "permission_decision",
                "ALTER TABLE agent_step ADD COLUMN permission_decision VARCHAR(32) DEFAULT NULL AFTER trace_id");
        addColumnIfMissing(connection, statement, "agent_step", "risk_level",
                "ALTER TABLE agent_step ADD COLUMN risk_level VARCHAR(32) DEFAULT NULL AFTER permission_decision");
        addColumnIfMissing(connection, statement, "agent_step", "compact_status",
                "ALTER TABLE agent_step ADD COLUMN compact_status VARCHAR(32) DEFAULT NULL AFTER risk_level");
        addColumnIfMissing(connection, statement, "agent_step", "stream_event_count",
                "ALTER TABLE agent_step ADD COLUMN stream_event_count INT NOT NULL DEFAULT 0 AFTER compact_status");
        addColumnIfMissing(connection, statement, "agent_run", "max_duration_seconds",
                "ALTER TABLE agent_run ADD COLUMN max_duration_seconds INT NOT NULL DEFAULT 600 AFTER max_total_tokens");
    }

    private void seedBuiltinTools(Statement statement) throws SQLException {
        statement.executeUpdate("""
                INSERT IGNORE INTO agent_tool_definition
                (tool_name, description, input_schema, output_schema, risk_level, concurrency_safe, idempotent, timeout_seconds, requires_approval, tenant_scopes, status)
                VALUES
                ('memory.search', 'Search authorized project memory and knowledge context.',
                 '{"type":"object","properties":{"query":{"type":"string"},"topK":{"type":"integer","minimum":1,"maximum":20}},"required":["query"]}',
                 '{"type":"object","properties":{"items":{"type":"array"}}}', 'READ_ONLY', 1, 1, 10, 0, '{"scope":"project"}', 1),
                ('model.ask', 'Ask a configured model for bounded sub-reasoning without external side effects.',
                 '{"type":"object","properties":{"prompt":{"type":"string"}},"required":["prompt"]}',
                 '{"type":"object","properties":{"answer":{"type":"string"}}}', 'READ_ONLY', 1, 1, 30, 0, '{"scope":"tenant"}', 1),
                ('agent.invoke', 'Invoke an already registered platform agent.',
                 '{"type":"object","properties":{"agentCode":{"type":"string"},"payload":{"type":"object"}},"required":["agentCode"]}',
                 '{"type":"object","properties":{"result":{"type":"object"}}}', 'MUTATING', 0, 0, 60, 1, '{"scope":"project"}', 1),
                ('workflow.trigger', 'Trigger a deployed workflow from an Agent Run.',
                 '{"type":"object","properties":{"workflowId":{"type":"integer"},"params":{"type":"object"}},"required":["workflowId"]}',
                 '{"type":"object","properties":{"executionId":{"type":"string"}}}', 'MUTATING', 0, 0, 60, 1, '{"scope":"project"}', 1),
                ('artifact.create', 'Create a structured artifact that must enter approval before final delivery.',
                 '{"type":"object","properties":{"title":{"type":"string"},"artifactType":{"type":"string"},"content":{"type":"object"}},"required":["title","artifactType"]}',
                 '{"type":"object","properties":{"artifactId":{"type":"integer"},"status":{"type":"string"}}}', 'MUTATING', 0, 1, 20, 1, '{"scope":"project"}', 1),
                ('ask_user', 'Pause execution and ask the user a clarifying question; the run waits for an answer or times out.',
                 '{"type":"object","properties":{"question":{"type":"string"},"timeoutSeconds":{"type":"integer","minimum":1,"maximum":3600}},"required":["question"]}',
                 '{"type":"object","properties":{"answer":{"type":"string"},"status":{"type":"string"}}}', 'READ_ONLY', 0, 0, 600, 0, '{"scope":"project"}', 1),
                ('task.create', 'Create a child task tracked for this agent run.',
                 '{"type":"object","properties":{"taskType":{"type":"string"},"payload":{"type":"object"}},"required":["taskType"]}',
                 '{"type":"object","properties":{"taskId":{"type":"integer"},"status":{"type":"string"}}}', 'MUTATING', 0, 0, 10, 0, '{"scope":"project"}', 1),
                ('task.list', 'List tasks belonging to this agent run.',
                 '{"type":"object","properties":{"status":{"type":"string"}}}',
                 '{"type":"object","properties":{"tasks":{"type":"array"}}}', 'READ_ONLY', 1, 1, 10, 0, '{"scope":"project"}', 1),
                ('task.get', 'Get one task of this agent run by id.',
                 '{"type":"object","properties":{"taskId":{"type":"integer"}},"required":["taskId"]}',
                 '{"type":"object","properties":{"task":{"type":"object"}}}', 'READ_ONLY', 1, 1, 10, 0, '{"scope":"project"}', 1),
                ('task.update', 'Update the status of one task of this agent run.',
                 '{"type":"object","properties":{"taskId":{"type":"integer"},"status":{"type":"string"},"note":{"type":"string"}},"required":["taskId","status"]}',
                 '{"type":"object","properties":{"taskId":{"type":"integer"},"status":{"type":"string"}}}', 'MUTATING', 0, 0, 10, 0, '{"scope":"project"}', 1)
                """);
        statement.executeUpdate("""
                UPDATE agent_tool_definition
                SET input_schema = '{"type":"object","properties":{"prompt":{"type":"string"}},"required":["prompt"]}'
                WHERE tool_name = 'model.ask' AND input_schema LIKE '%modelCode%'
                """);
    }

    private void addColumnIfMissing(Connection connection, Statement statement, String table, String column,
                                    String ddl) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet rs = metadata.getColumns(connection.getCatalog(), null, table, column)) {
            if (!rs.next()) statement.executeUpdate(ddl);
        }
    }
}
