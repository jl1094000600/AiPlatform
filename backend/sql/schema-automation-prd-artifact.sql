-- Automation PRD artifact support

SET @schema_name = DATABASE();

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE automation_stage_run ADD COLUMN artifact_path VARCHAR(512) DEFAULT NULL AFTER output_summary',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'automation_stage_run' AND COLUMN_NAME = 'artifact_path'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE automation_stage_run ADD COLUMN artifact_content MEDIUMTEXT DEFAULT NULL AFTER artifact_path',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'automation_stage_run' AND COLUMN_NAME = 'artifact_content'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
