-- Code quality standards and AI evaluation runs

CREATE TABLE IF NOT EXISTS code_quality_standard (
    id BIGINT NOT NULL AUTO_INCREMENT,
    standard_code VARCHAR(64) NOT NULL,
    standard_name VARCHAR(128) NOT NULL,
    description TEXT DEFAULT NULL,
    language VARCHAR(64) DEFAULT 'GENERAL',
    framework VARCHAR(64) DEFAULT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    gate_config MEDIUMTEXT DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_code_quality_standard_code (standard_code),
    KEY idx_code_quality_standard_status (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS code_quality_rule (
    id BIGINT NOT NULL AUTO_INCREMENT,
    standard_id BIGINT NOT NULL,
    rule_code VARCHAR(64) NOT NULL,
    category VARCHAR(64) DEFAULT 'maintainability',
    severity VARCHAR(32) DEFAULT 'MAJOR',
    title VARCHAR(256) NOT NULL,
    description TEXT DEFAULT NULL,
    check_prompt TEXT DEFAULT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_code_quality_rule_standard (standard_id, enabled),
    KEY idx_code_quality_rule_severity (severity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS automation_code_quality_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    pipeline_id BIGINT NOT NULL,
    stage_run_id BIGINT NOT NULL,
    code_stage_run_id BIGINT DEFAULT NULL,
    standard_id BIGINT DEFAULT NULL,
    standard_snapshot MEDIUMTEXT DEFAULT NULL,
    gate_snapshot MEDIUMTEXT DEFAULT NULL,
    model_code VARCHAR(64) DEFAULT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
    overall_score INT DEFAULT 0,
    passed TINYINT NOT NULL DEFAULT 0,
    summary TEXT DEFAULT NULL,
    metrics_json MEDIUMTEXT DEFAULT NULL,
    raw_result MEDIUMTEXT DEFAULT NULL,
    input_tokens INT DEFAULT 0,
    output_tokens INT DEFAULT 0,
    total_tokens INT DEFAULT 0,
    error_message TEXT DEFAULT NULL,
    start_time DATETIME DEFAULT NULL,
    end_time DATETIME DEFAULT NULL,
    duration_ms INT DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_code_quality_run_pipeline (pipeline_id, create_time),
    KEY idx_code_quality_run_stage (stage_run_id),
    KEY idx_code_quality_run_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS automation_code_quality_issue (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id BIGINT NOT NULL,
    pipeline_id BIGINT NOT NULL,
    stage_run_id BIGINT NOT NULL,
    rule_code VARCHAR(64) DEFAULT NULL,
    severity VARCHAR(32) DEFAULT 'MAJOR',
    category VARCHAR(64) DEFAULT NULL,
    file_path VARCHAR(512) DEFAULT NULL,
    line_start INT DEFAULT NULL,
    line_end INT DEFAULT NULL,
    title VARCHAR(256) NOT NULL,
    description TEXT DEFAULT NULL,
    suggestion TEXT DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_code_quality_issue_run (run_id),
    KEY idx_code_quality_issue_pipeline (pipeline_id),
    KEY idx_code_quality_issue_severity (severity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS automation_code_quality_evidence (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    run_id BIGINT NOT NULL,
    pipeline_id BIGINT NOT NULL,
    stage_run_id BIGINT NOT NULL,
    evidence_type VARCHAR(64) NOT NULL,
    tool_name VARCHAR(128) DEFAULT NULL,
    command_text VARCHAR(512) DEFAULT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    score INT DEFAULT 0,
    summary TEXT DEFAULT NULL,
    raw_output MEDIUMTEXT DEFAULT NULL,
    parsed_result_json MEDIUMTEXT DEFAULT NULL,
    duration_ms INT DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_code_quality_evidence_run (tenant_id, run_id),
    KEY idx_code_quality_evidence_type (tenant_id, evidence_type),
    KEY idx_code_quality_evidence_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE automation_pipeline ADD COLUMN code_quality_enabled TINYINT NOT NULL DEFAULT 0;
ALTER TABLE automation_pipeline ADD COLUMN code_quality_standard_id BIGINT DEFAULT NULL;
ALTER TABLE automation_pipeline ADD COLUMN code_quality_standard_snapshot MEDIUMTEXT DEFAULT NULL;
ALTER TABLE automation_pipeline ADD COLUMN code_quality_gate_snapshot MEDIUMTEXT DEFAULT NULL;
ALTER TABLE automation_pipeline ADD COLUMN quality_model_code VARCHAR(64) DEFAULT NULL;
