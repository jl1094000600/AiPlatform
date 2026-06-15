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
public class AutomationDeploySchemaInitializer {
    private final DataSource dataSource;

    @PostConstruct
    public void initialize() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS automation_deploy_profile (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        profile_name VARCHAR(128) NOT NULL,
                        deploy_type VARCHAR(32) NOT NULL,
                        environment_name VARCHAR(64) NOT NULL DEFAULT 'dev',
                        status TINYINT NOT NULL DEFAULT 1,
                        build_command TEXT DEFAULT NULL,
                        test_command TEXT DEFAULT NULL,
                        health_check_url VARCHAR(512) DEFAULT NULL,
                        timeout_seconds INT NOT NULL DEFAULT 600,
                        docker_config MEDIUMTEXT DEFAULT NULL,
                        jenkins_config MEDIUMTEXT DEFAULT NULL,
                        create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                        update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        is_deleted TINYINT NOT NULL DEFAULT 0,
                        PRIMARY KEY (id),
                        KEY idx_deploy_profile_status (status, create_time),
                        KEY idx_deploy_profile_type (deploy_type)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS automation_deploy_run (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        pipeline_id BIGINT NOT NULL,
                        stage_run_id BIGINT NOT NULL,
                        deploy_profile_id BIGINT DEFAULT NULL,
                        stage_key VARCHAR(64) NOT NULL,
                        deploy_type VARCHAR(32) DEFAULT NULL,
                        environment_name VARCHAR(64) DEFAULT NULL,
                        status VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
                        profile_snapshot MEDIUMTEXT DEFAULT NULL,
                        command_log MEDIUMTEXT DEFAULT NULL,
                        exit_code INT DEFAULT NULL,
                        image_name VARCHAR(255) DEFAULT NULL,
                        container_name VARCHAR(255) DEFAULT NULL,
                        jenkins_build_number INT DEFAULT NULL,
                        jenkins_build_url VARCHAR(512) DEFAULT NULL,
                        health_status_code INT DEFAULT NULL,
                        health_response_ms INT DEFAULT NULL,
                        health_message VARCHAR(512) DEFAULT NULL,
                        error_message TEXT DEFAULT NULL,
                        start_time DATETIME DEFAULT NULL,
                        end_time DATETIME DEFAULT NULL,
                        duration_ms INT DEFAULT NULL,
                        create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                        update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        PRIMARY KEY (id),
                        KEY idx_deploy_run_pipeline (pipeline_id, create_time),
                        KEY idx_deploy_run_stage (stage_run_id),
                        KEY idx_deploy_run_status (status)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
            addColumnIfMissing(connection, statement, "automation_pipeline", "auto_deploy_enabled",
                    "ALTER TABLE automation_pipeline ADD COLUMN auto_deploy_enabled TINYINT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, statement, "automation_pipeline", "deploy_profile_id",
                    "ALTER TABLE automation_pipeline ADD COLUMN deploy_profile_id BIGINT DEFAULT NULL");
            addColumnIfMissing(connection, statement, "automation_pipeline", "deploy_profile_snapshot",
                    "ALTER TABLE automation_pipeline ADD COLUMN deploy_profile_snapshot MEDIUMTEXT DEFAULT NULL");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize automation deploy schema: " + e.getMessage(), e);
        }
    }

    private void addColumnIfMissing(Connection connection, Statement statement, String tableName,
                                    String columnName, String alterSql) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        String catalog = connection.getCatalog();
        try (ResultSet columns = metadata.getColumns(catalog, null, tableName, columnName)) {
            if (!columns.next()) {
                statement.executeUpdate(alterSql);
            }
        }
    }
}
