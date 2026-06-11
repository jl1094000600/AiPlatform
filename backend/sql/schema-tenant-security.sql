-- Multi-tenant and RBAC migration.
-- Safe to run multiple times on MySQL. Historical data is migrated to tenant id 1.

CREATE TABLE IF NOT EXISTS sys_tenant (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_code VARCHAR(64) NOT NULL,
    tenant_name VARCHAR(128) NOT NULL,
    contact_name VARCHAR(128) DEFAULT NULL,
    contact_email VARCHAR(128) DEFAULT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    expire_time DATETIME DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_user_tenant (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    tenant_role VARCHAR(64) DEFAULT 'member',
    default_tenant TINYINT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_tenant (user_id, tenant_id),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (tenant_id, user_id, role_id),
    KEY idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_permission (tenant_id, role_id, permission_id),
    KEY idx_permission_id (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_menu (
    id BIGINT NOT NULL AUTO_INCREMENT,
    menu_code VARCHAR(64) NOT NULL,
    menu_name VARCHAR(128) NOT NULL,
    path VARCHAR(255) DEFAULT NULL,
    icon VARCHAR(64) DEFAULT NULL,
    permission_code VARCHAR(128) DEFAULT NULL,
    parent_id BIGINT DEFAULT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    visible TINYINT NOT NULL DEFAULT 1,
    status TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_menu_code (menu_code),
    KEY idx_permission_code (permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP PROCEDURE IF EXISTS add_column_if_missing;
DELIMITER $$
CREATE PROCEDURE add_column_if_missing(
    IN p_table_name VARCHAR(128),
    IN p_column_name VARCHAR(128),
    IN p_column_definition TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = p_table_name
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND column_name = p_column_name
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE ', p_table_name, ' ADD COLUMN ', p_column_name, ' ', p_column_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL add_column_if_missing('sys_user', 'default_tenant_id', 'BIGINT DEFAULT 1');
CALL add_column_if_missing('sys_user', 'platform_admin', 'TINYINT NOT NULL DEFAULT 0');
CALL add_column_if_missing('sys_role', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('sys_role', 'role_scope', 'VARCHAR(32) NOT NULL DEFAULT ''TENANT''');
CALL add_column_if_missing('sys_user_role', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('sys_role_permission', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('sys_audit_log', 'tenant_id', 'BIGINT DEFAULT 1');
CALL add_column_if_missing('sys_audit_log', 'permission_code', 'VARCHAR(128) DEFAULT NULL');
CALL add_column_if_missing('sys_audit_log', 'request_path', 'VARCHAR(255) DEFAULT NULL');

CALL add_column_if_missing('ai_a2a_task', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('ai_agent', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('ai_agent_graph_edge', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('ai_agent_heartbeat', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('ai_agent_quality_result', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('ai_agent_quality_run', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('ai_agent_registration', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('ai_agent_registration_event', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('ai_agent_runtime_config', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('ai_agent_version', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('ai_dataset', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('ai_evaluation', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('ai_evaluation_criteria', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('ai_model', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('ai_output_governance_record', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('ai_skill', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('ai_tts_config', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('ai_tts_task', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('ai_user_memory', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('ai_workflow', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('ai_workflow_execution', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('alert_event', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('alert_rule', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('automation_approval', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('automation_build_run', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('automation_code_requirement_feedback', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('automation_code_quality_issue', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('automation_code_quality_evidence', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('automation_code_quality_run', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('automation_deploy_profile', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('automation_deploy_run', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('automation_generated_code_batch', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('automation_generated_code_file', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('automation_generation_job', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('automation_pipeline', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('automation_report_snapshot', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('automation_stage_run', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('automation_test_run', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('billing_balance_transaction', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('billing_budget', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('billing_usage_daily', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('biz_agent_auth', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('biz_customer', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('biz_module', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('code_quality_rule', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('code_quality_standard', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('lowcode_invocation_record', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('mon_api_metrics', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('mon_call_record', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('prompt_engineering_eval_result', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('prompt_engineering_eval_run', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('prompt_engineering_optimize_run', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('prompt_engineering_prompt', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('prompt_engineering_test_case', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('prompt_engineering_version', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');
CALL add_column_if_missing('rag_ingestion_record', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1');

DROP PROCEDURE add_column_if_missing;

INSERT IGNORE INTO sys_tenant (id, tenant_code, tenant_name, status)
VALUES (1, 'aiplatform', 'AIPlatform', 1);

UPDATE sys_tenant
SET tenant_code = CASE
        WHEN tenant_code = 'think_land'
         AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_tenant WHERE tenant_code = 'aiplatform' AND id <> 1) existing_tenant)
        THEN 'aiplatform'
        ELSE tenant_code
    END,
    tenant_name = 'AIPlatform'
WHERE id = 1
  AND (tenant_code = 'think_land' OR tenant_name = 'Think Land');

UPDATE sys_user SET default_tenant_id = 1 WHERE default_tenant_id IS NULL;

INSERT IGNORE INTO sys_user_tenant (user_id, tenant_id, tenant_role, default_tenant, status)
SELECT id, 1, 'admin', 1, 1 FROM sys_user WHERE is_deleted = 0;

INSERT IGNORE INTO sys_permission (permission_code, permission_name, resource_type) VALUES
('benchmark:view', '基准测试查看', 'API'),
('benchmark:run', '基准测试执行', 'API'),
('benchmark:manage', '基准测试标准管理', 'API'),
('tts:invoke', '语音合成调用', 'API'),
('tts:manage', '语音配置管理', 'API');

INSERT IGNORE INTO sys_role_permission (tenant_id, role_id, permission_id)
SELECT 1, r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.tenant_id = 1 AND r.role_code IN ('platform_admin', 'tenant_admin')
  AND p.permission_code IN ('benchmark:run', 'benchmark:manage', 'tts:invoke', 'tts:manage');

INSERT IGNORE INTO sys_role_permission (tenant_id, role_id, permission_id)
SELECT 1, r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.tenant_id = 1 AND r.role_code = 'developer'
  AND p.permission_code IN ('benchmark:view', 'benchmark:run', 'tts:invoke');

INSERT IGNORE INTO sys_role_permission (tenant_id, role_id, permission_id)
SELECT 1, r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.tenant_id = 1 AND r.role_code IN ('reviewer', 'readonly')
  AND p.permission_code = 'benchmark:view';

DROP PROCEDURE IF EXISTS migrate_tenant_data_if_exists;
DELIMITER $$
CREATE PROCEDURE migrate_tenant_data_if_exists(IN p_table_name VARCHAR(128))
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = p_table_name
    ) AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND column_name = 'tenant_id'
    ) THEN
        SET @dml = CONCAT('UPDATE ', p_table_name, ' SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0');
        PREPARE stmt FROM @dml;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL migrate_tenant_data_if_exists('ai_a2a_task');
CALL migrate_tenant_data_if_exists('ai_agent');
CALL migrate_tenant_data_if_exists('ai_agent_graph_edge');
CALL migrate_tenant_data_if_exists('ai_agent_heartbeat');
CALL migrate_tenant_data_if_exists('ai_agent_quality_result');
CALL migrate_tenant_data_if_exists('ai_agent_quality_run');
CALL migrate_tenant_data_if_exists('ai_agent_registration');
CALL migrate_tenant_data_if_exists('ai_agent_registration_event');
CALL migrate_tenant_data_if_exists('ai_agent_runtime_config');
CALL migrate_tenant_data_if_exists('ai_agent_version');
CALL migrate_tenant_data_if_exists('ai_dataset');
CALL migrate_tenant_data_if_exists('ai_evaluation');
CALL migrate_tenant_data_if_exists('ai_evaluation_criteria');
CALL migrate_tenant_data_if_exists('ai_model');
CALL migrate_tenant_data_if_exists('ai_output_governance_record');
CALL migrate_tenant_data_if_exists('ai_skill');
CALL migrate_tenant_data_if_exists('ai_tts_config');
CALL migrate_tenant_data_if_exists('ai_tts_task');
CALL migrate_tenant_data_if_exists('ai_user_memory');
CALL migrate_tenant_data_if_exists('ai_workflow');
CALL migrate_tenant_data_if_exists('ai_workflow_execution');
CALL migrate_tenant_data_if_exists('alert_event');
CALL migrate_tenant_data_if_exists('alert_rule');
CALL migrate_tenant_data_if_exists('automation_approval');
CALL migrate_tenant_data_if_exists('automation_build_run');
CALL migrate_tenant_data_if_exists('automation_code_requirement_feedback');
CALL migrate_tenant_data_if_exists('automation_code_quality_issue');
CALL migrate_tenant_data_if_exists('automation_code_quality_evidence');
CALL migrate_tenant_data_if_exists('automation_code_quality_run');
CALL migrate_tenant_data_if_exists('automation_deploy_profile');
CALL migrate_tenant_data_if_exists('automation_deploy_run');
CALL migrate_tenant_data_if_exists('automation_generated_code_batch');
CALL migrate_tenant_data_if_exists('automation_generated_code_file');
CALL migrate_tenant_data_if_exists('automation_generation_job');
CALL migrate_tenant_data_if_exists('automation_pipeline');
CALL migrate_tenant_data_if_exists('automation_report_snapshot');
CALL migrate_tenant_data_if_exists('automation_stage_run');
CALL migrate_tenant_data_if_exists('automation_test_run');
CALL migrate_tenant_data_if_exists('billing_balance_transaction');
CALL migrate_tenant_data_if_exists('billing_budget');
CALL migrate_tenant_data_if_exists('billing_usage_daily');
CALL migrate_tenant_data_if_exists('biz_agent_auth');
CALL migrate_tenant_data_if_exists('biz_customer');
CALL migrate_tenant_data_if_exists('biz_module');
CALL migrate_tenant_data_if_exists('code_quality_rule');
CALL migrate_tenant_data_if_exists('code_quality_standard');
CALL migrate_tenant_data_if_exists('lowcode_invocation_record');
CALL migrate_tenant_data_if_exists('mon_api_metrics');
CALL migrate_tenant_data_if_exists('mon_call_record');
CALL migrate_tenant_data_if_exists('prompt_engineering_eval_result');
CALL migrate_tenant_data_if_exists('prompt_engineering_eval_run');
CALL migrate_tenant_data_if_exists('prompt_engineering_optimize_run');
CALL migrate_tenant_data_if_exists('prompt_engineering_prompt');
CALL migrate_tenant_data_if_exists('prompt_engineering_test_case');
CALL migrate_tenant_data_if_exists('prompt_engineering_version');
CALL migrate_tenant_data_if_exists('rag_ingestion_record');
CALL migrate_tenant_data_if_exists('sys_audit_log');

DROP PROCEDURE migrate_tenant_data_if_exists;
