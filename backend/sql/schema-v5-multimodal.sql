-- AI model capabilities and per-tenant defaults for v5.0.0.

SET @schema_name = DATABASE();

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE ai_model ADD COLUMN capability_type VARCHAR(16) NOT NULL DEFAULT ''CHAT'' AFTER api_key',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'ai_model' AND COLUMN_NAME = 'capability_type'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE ai_model ADD COLUMN default_for_capability TINYINT NOT NULL DEFAULT 0 AFTER capability_type',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'ai_model' AND COLUMN_NAME = 'default_for_capability'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE ai_model SET capability_type = 'CHAT'
WHERE capability_type IS NULL OR capability_type = '';

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'CREATE INDEX idx_model_capability_default ON ai_model (tenant_id, capability_type, default_for_capability, status)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'ai_model' AND INDEX_NAME = 'idx_model_capability_default'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
