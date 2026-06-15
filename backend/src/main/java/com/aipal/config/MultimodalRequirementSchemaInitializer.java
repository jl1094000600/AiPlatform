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
public class MultimodalRequirementSchemaInitializer {
    private final DataSource dataSource;

    @PostConstruct
    public void initialize() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS requirement_attachment (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL,
                        user_id BIGINT NOT NULL,
                        request_id VARCHAR(80) NOT NULL,
                        attachment_type VARCHAR(16) NOT NULL,
                        original_file_name VARCHAR(255) NOT NULL,
                        mime_type VARCHAR(128) NOT NULL,
                        file_size BIGINT NOT NULL,
                        storage_path VARCHAR(768) NOT NULL,
                        checksum VARCHAR(64) NOT NULL,
                        expires_at DATETIME DEFAULT NULL,
                        create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                        update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        is_deleted TINYINT NOT NULL DEFAULT 0,
                        PRIMARY KEY (id),
                        KEY idx_requirement_attachment_request (tenant_id, user_id, request_id, create_time),
                        KEY idx_requirement_attachment_expiry (tenant_id, expires_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS requirement_parse_task (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL,
                        attachment_id BIGINT NOT NULL,
                        status VARCHAR(24) NOT NULL,
                        model_code VARCHAR(128) DEFAULT NULL,
                        raw_result MEDIUMTEXT DEFAULT NULL,
                        edited_result MEDIUMTEXT DEFAULT NULL,
                        error_message VARCHAR(1000) DEFAULT NULL,
                        retry_count INT NOT NULL DEFAULT 0,
                        start_time DATETIME DEFAULT NULL,
                        end_time DATETIME DEFAULT NULL,
                        create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                        update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        is_deleted TINYINT NOT NULL DEFAULT 0,
                        PRIMARY KEY (id),
                        KEY idx_requirement_task_attachment (tenant_id, attachment_id, create_time),
                        KEY idx_requirement_task_status (tenant_id, status, create_time)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize multimodal requirement schema: " + e.getMessage(), e);
        }
    }
}
