-- Skill management and automation pipeline skill selection

CREATE TABLE IF NOT EXISTS ai_skill (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    skill_code           VARCHAR(64)  NOT NULL,
    skill_name           VARCHAR(128) NOT NULL,
    description          TEXT         DEFAULT NULL,
    status               TINYINT      NOT NULL DEFAULT 1,
    prompt_content       MEDIUMTEXT   DEFAULT NULL,
    function_definitions JSON         DEFAULT NULL,
    create_time          DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time          DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted           TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_skill_code (skill_code),
    KEY idx_skill_status_time (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @schema_name = DATABASE();

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'automation_pipeline' AND column_name = 'skill_id') = 0,
    'ALTER TABLE automation_pipeline ADD COLUMN skill_id BIGINT DEFAULT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'automation_pipeline' AND column_name = 'skill_snapshot') = 0,
    'ALTER TABLE automation_pipeline ADD COLUMN skill_snapshot MEDIUMTEXT DEFAULT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
