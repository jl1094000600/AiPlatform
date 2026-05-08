-- Model SDK/API configuration

SET @schema_name = DATABASE();

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE ai_model ADD COLUMN sdk_type VARCHAR(64) DEFAULT ''openai-compatible'' AFTER endpoint',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'ai_model' AND COLUMN_NAME = 'sdk_type'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE ai_model ADD COLUMN api_key VARCHAR(512) DEFAULT NULL AFTER sdk_type',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'ai_model' AND COLUMN_NAME = 'api_key'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE ai_model ADD COLUMN default_temperature DECIMAL(4,2) DEFAULT 1.00 AFTER api_key',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'ai_model' AND COLUMN_NAME = 'default_temperature'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE ai_model ADD COLUMN max_tokens INT DEFAULT 4096 AFTER default_temperature',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'ai_model' AND COLUMN_NAME = 'max_tokens'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
