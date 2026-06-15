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
public class AutomationGeneratedCodeSchemaInitializer {
    private final DataSource dataSource;

    @PostConstruct
    public void initialize() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS automation_generated_code_batch (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        pipeline_id BIGINT NOT NULL,
                        stage_run_id BIGINT NOT NULL,
                        generation_job_id BIGINT DEFAULT NULL,
                        artifact_path VARCHAR(512) DEFAULT NULL,
                        manifest_json MEDIUMTEXT DEFAULT NULL,
                        file_count INT NOT NULL DEFAULT 0,
                        total_bytes BIGINT NOT NULL DEFAULT 0,
                        model_code VARCHAR(64) DEFAULT NULL,
                        create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                        update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        PRIMARY KEY (id),
                        KEY idx_generated_code_batch_pipeline (pipeline_id, create_time),
                        KEY idx_generated_code_batch_stage (stage_run_id),
                        KEY idx_generated_code_batch_job (generation_job_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS automation_generated_code_file (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        batch_id BIGINT NOT NULL,
                        pipeline_id BIGINT NOT NULL,
                        stage_run_id BIGINT NOT NULL,
                        generation_job_id BIGINT DEFAULT NULL,
                        file_index INT NOT NULL DEFAULT 0,
                        file_path VARCHAR(512) NOT NULL,
                        file_type VARCHAR(32) DEFAULT NULL,
                        size_bytes BIGINT NOT NULL DEFAULT 0,
                        content_hash VARCHAR(64) DEFAULT NULL,
                        content MEDIUMTEXT DEFAULT NULL,
                        create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_generated_code_file_batch_path (batch_id, file_path),
                        KEY idx_generated_code_file_batch (batch_id, file_index),
                        KEY idx_generated_code_file_pipeline (pipeline_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS automation_code_requirement_feedback (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        batch_id BIGINT NOT NULL,
                        pipeline_id BIGINT NOT NULL,
                        stage_run_id BIGINT NOT NULL,
                        generation_job_id BIGINT DEFAULT NULL,
                        feedback_source VARCHAR(32) NOT NULL,
                        alignment_status VARCHAR(32) NOT NULL,
                        alignment_score INT DEFAULT NULL,
                        summary TEXT DEFAULT NULL,
                        failure_reason TEXT DEFAULT NULL,
                        detail_json MEDIUMTEXT DEFAULT NULL,
                        raw_result MEDIUMTEXT DEFAULT NULL,
                        reviewed_by VARCHAR(64) DEFAULT NULL,
                        create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (id),
                        KEY idx_code_feedback_batch (batch_id, create_time),
                        KEY idx_code_feedback_pipeline (pipeline_id, create_time),
                        KEY idx_code_feedback_status (alignment_status)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize generated code schema: " + e.getMessage(), e);
        }
    }
}
