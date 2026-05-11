-- Automation pipeline template and code generation options

SET @schema_name = DATABASE();

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'automation_pipeline' AND column_name = 'template_file') = 0,
    'ALTER TABLE automation_pipeline ADD COLUMN template_file VARCHAR(255) DEFAULT NULL COMMENT ''Markdown template file used for code generation''',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'automation_pipeline' AND column_name = 'project_mode') = 0,
    'ALTER TABLE automation_pipeline ADD COLUMN project_mode VARCHAR(32) NOT NULL DEFAULT ''scratch'' COMMENT ''scratch or existing project''',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'automation_pipeline' AND column_name = 'code_level') = 0,
    'ALTER TABLE automation_pipeline ADD COLUMN code_level VARCHAR(32) NOT NULL DEFAULT ''module'' COMMENT ''project/module/package/component''',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'automation_pipeline' AND column_name = 'generate_frontend') = 0,
    'ALTER TABLE automation_pipeline ADD COLUMN generate_frontend TINYINT NOT NULL DEFAULT 1',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'automation_pipeline' AND column_name = 'generate_backend') = 0,
    'ALTER TABLE automation_pipeline ADD COLUMN generate_backend TINYINT NOT NULL DEFAULT 1',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'automation_pipeline' AND column_name = 'frontend_output_path') = 0,
    'ALTER TABLE automation_pipeline ADD COLUMN frontend_output_path VARCHAR(255) DEFAULT ''front/src/generated''',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'automation_pipeline' AND column_name = 'backend_output_path') = 0,
    'ALTER TABLE automation_pipeline ADD COLUMN backend_output_path VARCHAR(255) DEFAULT ''backend/src/main/java/com/aipal/generated''',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

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
