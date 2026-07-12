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

@Component
@ConditionalOnProperty(name = "aipal.schema-initialization.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ProjectWorkspaceSchemaInitializer {
    private final DataSource dataSource;

    @PostConstruct
    public void initialize() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            addColumnIfMissing(connection, statement, "automation_pipeline", "tenant_id",
                    "ALTER TABLE automation_pipeline ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1");
            addColumnIfMissing(connection, statement, "automation_pipeline", "project_key",
                    "ALTER TABLE automation_pipeline ADD COLUMN project_key VARCHAR(128) DEFAULT NULL");
            addColumnIfMissing(connection, statement, "automation_stage_run", "tenant_id",
                    "ALTER TABLE automation_stage_run ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 AFTER id");
            addColumnIfMissing(connection, statement, "automation_approval", "tenant_id",
                    "ALTER TABLE automation_approval ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 AFTER id");
            backfillAutomationChildTenants(statement);
            statement.executeUpdate("""
                    UPDATE automation_pipeline
                    SET project_key = TRIM(BOTH '-' FROM LOWER(REGEXP_REPLACE(TRIM(project_name), '[^A-Za-z0-9]+', '-')))
                    WHERE (project_key IS NULL OR project_key = '')
                      AND project_name IS NOT NULL
                      AND TRIM(project_name) <> ''
                    """);
            captureLegacyBadKeyWorkspaces(statement);
            statement.executeUpdate("""
                    UPDATE automation_pipeline
                    SET project_key = CONCAT('pipeline-', id)
                    WHERE project_key IS NULL
                       OR project_key = ''
                       OR project_key REGEXP '^-+$'
                       OR project_key REGEXP '^-|-$'
                    """);
            addIndexIfMissing(connection, statement, "automation_pipeline", "idx_automation_pipeline_project_key",
                    "ALTER TABLE automation_pipeline ADD KEY idx_automation_pipeline_project_key (tenant_id, project_key, create_time)");
            addIndexIfMissing(connection, statement, "automation_stage_run", "idx_stage_status",
                    "ALTER TABLE automation_stage_run ADD KEY idx_stage_status (tenant_id, status, stage_order)");
            addIndexIfMissing(connection, statement, "automation_approval", "idx_status_role",
                    "ALTER TABLE automation_approval ADD KEY idx_status_role (tenant_id, status, reviewer_role)");
            archiveLegacyBadKeyWorkspaces(statement);
            backfillProjectWorkspaces(statement);
            backfillLegacyBadKeyMembers(statement);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize project workspace schema: " + e.getMessage(), e);
        }
    }

    private void backfillAutomationChildTenants(Statement statement) throws SQLException {
        statement.executeUpdate("""
                UPDATE automation_stage_run s
                JOIN automation_pipeline p ON p.id = s.pipeline_id
                SET s.tenant_id = p.tenant_id
                WHERE s.tenant_id IS NULL OR s.tenant_id = 0
                """);
        statement.executeUpdate("""
                UPDATE automation_approval a
                JOIN automation_pipeline p ON p.id = a.pipeline_id
                SET a.tenant_id = p.tenant_id
                WHERE a.tenant_id IS NULL OR a.tenant_id = 0
                """);
    }

    private void captureLegacyBadKeyWorkspaces(Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TEMPORARY TABLE IF NOT EXISTS legacy_delivery_project_key_map (
                    tenant_id BIGINT NOT NULL,
                    old_project_key VARCHAR(128) NOT NULL,
                    new_project_key VARCHAR(128) NOT NULL,
                    old_project_id BIGINT DEFAULT NULL,
                    pipeline_id BIGINT NOT NULL
                )
                """);
        statement.executeUpdate("DELETE FROM legacy_delivery_project_key_map");
        statement.executeUpdate("""
                INSERT INTO legacy_delivery_project_key_map
                    (tenant_id, old_project_key, new_project_key, old_project_id, pipeline_id)
                SELECT
                    a.tenant_id,
                    a.project_key,
                    CONCAT('pipeline-', a.id),
                    p.id,
                    a.id
                FROM automation_pipeline a
                LEFT JOIN ai_memory_project p
                  ON p.tenant_id = a.tenant_id
                 AND p.project_key = a.project_key
                 AND p.project_type = 'DELIVERY_PROJECT'
                WHERE a.project_key IS NOT NULL
                  AND a.project_key <> ''
                  AND (a.project_key REGEXP '^-+$'
                       OR a.project_key REGEXP '^-|-$')
                """);
    }

    private void archiveLegacyBadKeyWorkspaces(Statement statement) throws SQLException {
        statement.executeUpdate("""
                UPDATE ai_memory_project
                SET status = 'ARCHIVED',
                    update_time = NOW()
                WHERE project_type = 'DELIVERY_PROJECT'
                  AND status = 'ACTIVE'
                  AND (project_key REGEXP '^-+$'
                       OR project_key REGEXP '^-|-$')
                """);
    }

    private void backfillProjectWorkspaces(Statement statement) throws SQLException {
        statement.executeUpdate("""
                INSERT IGNORE INTO ai_memory_project
                    (tenant_id, project_key, project_name, project_type, owner_user_id, status, create_time, update_time, is_deleted)
                SELECT
                    tenant_id,
                    project_key,
                    COALESCE(NULLIF(TRIM(project_name), ''), project_key),
                    'DELIVERY_PROJECT',
                    COALESCE(initiator_user_id, 1),
                    'ACTIVE',
                    COALESCE(create_time, NOW()),
                    COALESCE(update_time, NOW()),
                    0
                FROM automation_pipeline
                WHERE project_key IS NOT NULL
                  AND project_key <> ''
                """);
        statement.executeUpdate("""
                INSERT IGNORE INTO ai_memory_project_member
                    (tenant_id, project_id, user_id, member_role, status, create_time, update_time, is_deleted)
                SELECT
                    p.tenant_id,
                    p.id,
                    COALESCE(a.initiator_user_id, p.owner_user_id, 1),
                    'OWNER',
                    'ACTIVE',
                    NOW(),
                    NOW(),
                    0
                FROM ai_memory_project p
                JOIN automation_pipeline a
                  ON a.tenant_id = p.tenant_id
                 AND a.project_key = p.project_key
                WHERE p.project_type = 'DELIVERY_PROJECT'
                """);
    }

    private void backfillLegacyBadKeyMembers(Statement statement) throws SQLException {
        statement.executeUpdate("""
                INSERT IGNORE INTO ai_memory_project_member
                    (tenant_id, project_id, user_id, member_role, status, create_time, update_time, is_deleted)
                SELECT
                    target.tenant_id,
                    target.id,
                    source.user_id,
                    source.member_role,
                    'ACTIVE',
                    NOW(),
                    NOW(),
                    0
                FROM legacy_delivery_project_key_map map
                JOIN ai_memory_project target
                  ON target.tenant_id = map.tenant_id
                 AND target.project_key = map.new_project_key
                 AND target.project_type = 'DELIVERY_PROJECT'
                JOIN ai_memory_project_member source
                  ON source.tenant_id = map.tenant_id
                 AND source.project_id = map.old_project_id
                 AND source.status = 'ACTIVE'
                """);
    }

    private void addColumnIfMissing(Connection connection, Statement statement, String tableName,
                                    String columnName, String alterSql) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet columns = metadata.getColumns(connection.getCatalog(), null, tableName, columnName)) {
            if (!columns.next()) {
                statement.executeUpdate(alterSql);
            }
        }
    }

    private void addIndexIfMissing(Connection connection, Statement statement, String tableName,
                                   String indexName, String alterSql) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet indexes = metadata.getIndexInfo(connection.getCatalog(), null, tableName, false, false)) {
            while (indexes.next()) {
                if (indexName.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                    return;
                }
            }
        }
        statement.executeUpdate(alterSql);
    }
}
