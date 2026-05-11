-- Automation deployment profiles and run records

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @schema_name = DATABASE();
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'automation_pipeline' AND column_name = 'auto_deploy_enabled') = 0,
    'ALTER TABLE automation_pipeline ADD COLUMN auto_deploy_enabled TINYINT NOT NULL DEFAULT 0',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'automation_pipeline' AND column_name = 'deploy_profile_id') = 0,
    'ALTER TABLE automation_pipeline ADD COLUMN deploy_profile_id BIGINT DEFAULT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'automation_pipeline' AND column_name = 'deploy_profile_snapshot') = 0,
    'ALTER TABLE automation_pipeline ADD COLUMN deploy_profile_snapshot MEDIUMTEXT DEFAULT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
