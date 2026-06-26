package com.aipal.integration;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AgentRuntimeMySqlConcurrencyTest {

    @Test
    void skipLockedLetsTwoWorkersClaimDifferentRootTasks() throws Exception {
        Map<String, String> env = loadEnv();
        Assumptions.assumeTrue(env.containsKey("DB_URL") && env.containsKey("DB_USERNAME") && env.containsKey("DB_PASSWORD"),
                "Local .env database settings are required");
        long tenantId = 9_900_000_000L + (System.nanoTime() % 1_000_000L);

        try (Connection setup = connect(env)) {
            long firstRun = insertRun(setup, tenantId, "skip-locked-a");
            long secondRun = insertRun(setup, tenantId, "skip-locked-b");
            insertTask(setup, tenantId, firstRun);
            insertTask(setup, tenantId, secondRun);
        }

        try (Connection firstWorker = connect(env); Connection secondWorker = connect(env)) {
            firstWorker.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            secondWorker.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            firstWorker.setAutoCommit(false);
            secondWorker.setAutoCommit(false);
            Long firstTask = selectNextTaskForUpdate(firstWorker, tenantId);
            Long secondTask = selectNextTaskForUpdate(secondWorker, tenantId);

            assertNotNull(firstTask);
            assertNotNull(secondTask);
            assertNotEquals(firstTask, secondTask);

            firstWorker.rollback();
            secondWorker.rollback();
        } finally {
            try (Connection cleanup = connect(env)) {
                cleanup.prepareStatement("DELETE FROM agent_task WHERE tenant_id = " + tenantId).executeUpdate();
                cleanup.prepareStatement("DELETE FROM agent_run WHERE tenant_id = " + tenantId).executeUpdate();
            }
        }
    }

    private Long selectNextTaskForUpdate(Connection connection, long tenantId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id FROM agent_task FORCE INDEX (idx_agent_task_claim_tenant)
                WHERE tenant_id = ?
                  AND is_deleted = 0
                  AND status = 'QUEUED'
                  AND task_type = 'RUN'
                  AND parent_task_id IS NULL
                  AND available_at <= NOW()
                ORDER BY available_at ASC, id ASC
                LIMIT 1 FOR UPDATE SKIP LOCKED
                """)) {
            statement.setLong(1, tenantId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : null;
            }
        }
    }

    private long insertRun(Connection connection, long tenantId, String businessId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO agent_run (
                  tenant_id, project_key, business_type, business_id, agent_id, agent_version_id,
                  definition_snapshot, definition_hash, idempotency_key, status, input_json, trace_id,
                  max_steps, max_child_tasks, max_total_tokens, total_tokens, version, is_deleted
                ) VALUES (?, 'TEST', 'MYSQL_CONCURRENCY', ?, 1, 1, '{}', REPEAT('a', 64), ?, 'QUEUED',
                  '{}', ?, 8, 5, 8000, 0, 1, 0)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, tenantId);
            statement.setString(2, businessId);
            statement.setString(3, tenantId + "-" + businessId);
            statement.setString(4, "trace-" + businessId);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private void insertTask(Connection connection, long tenantId, long runId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO agent_task (
                  tenant_id, run_id, task_type, status, payload_json, attempt_count, max_attempts, available_at, is_deleted
                ) VALUES (?, ?, 'RUN', 'QUEUED', '{}', 0, 3, NOW(), 0)
                """)) {
            statement.setLong(1, tenantId);
            statement.setLong(2, runId);
            statement.executeUpdate();
        }
    }

    private Connection connect(Map<String, String> env) throws Exception {
        return DriverManager.getConnection(env.get("DB_URL"), env.get("DB_USERNAME"), env.get("DB_PASSWORD"));
    }

    private Map<String, String> loadEnv() throws Exception {
        Path cwd = Path.of("").toAbsolutePath();
        Path envPath = Files.exists(cwd.resolve(".env")) ? cwd.resolve(".env") : cwd.resolve("..").resolve(".env");
        if (!Files.exists(envPath)) return Map.of();
        Map<String, String> values = new HashMap<>();
        for (String rawLine : Files.readAllLines(envPath)) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) continue;
            int index = line.indexOf('=');
            values.put(line.substring(0, index), line.substring(index + 1));
        }
        return values;
    }
}
