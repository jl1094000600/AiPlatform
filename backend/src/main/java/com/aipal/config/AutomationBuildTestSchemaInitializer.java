package com.aipal.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@Component
@ConditionalOnProperty(name = "aipal.schema-initialization.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class AutomationBuildTestSchemaInitializer {
    private final DataSource dataSource;

    @PostConstruct
    public void initialize() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS automation_build_run (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        pipeline_id BIGINT NOT NULL,
                        stage_run_id BIGINT NOT NULL,
                        generated_code_batch_id BIGINT DEFAULT NULL,
                        status VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
                        command_text TEXT DEFAULT NULL,
                        work_dir VARCHAR(512) DEFAULT NULL,
                        exit_code INT DEFAULT NULL,
                        command_log MEDIUMTEXT DEFAULT NULL,
                        error_message TEXT DEFAULT NULL,
                        start_time DATETIME DEFAULT NULL,
                        end_time DATETIME DEFAULT NULL,
                        duration_ms INT DEFAULT NULL,
                        create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                        update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        PRIMARY KEY (id),
                        KEY idx_build_run_pipeline (pipeline_id, create_time),
                        KEY idx_build_run_stage (stage_run_id),
                        KEY idx_build_run_status (status)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS automation_test_run (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        pipeline_id BIGINT NOT NULL,
                        stage_run_id BIGINT NOT NULL,
                        generated_code_batch_id BIGINT DEFAULT NULL,
                        status VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
                        command_text TEXT DEFAULT NULL,
                        work_dir VARCHAR(512) DEFAULT NULL,
                        exit_code INT DEFAULT NULL,
                        total_count INT DEFAULT 0,
                        passed_count INT DEFAULT 0,
                        failed_count INT DEFAULT 0,
                        skipped_count INT DEFAULT 0,
                        command_log MEDIUMTEXT DEFAULT NULL,
                        error_message TEXT DEFAULT NULL,
                        start_time DATETIME DEFAULT NULL,
                        end_time DATETIME DEFAULT NULL,
                        duration_ms INT DEFAULT NULL,
                        create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                        update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        PRIMARY KEY (id),
                        KEY idx_test_run_pipeline (pipeline_id, create_time),
                        KEY idx_test_run_stage (stage_run_id),
                        KEY idx_test_run_status (status)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize automation build/test schema: " + e.getMessage(), e);
        }
    }
}
