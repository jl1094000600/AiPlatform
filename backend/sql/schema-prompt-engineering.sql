CREATE TABLE IF NOT EXISTS prompt_engineering_prompt (
    id BIGINT NOT NULL AUTO_INCREMENT,
    prompt_code VARCHAR(64) NOT NULL,
    prompt_name VARCHAR(128) NOT NULL,
    description TEXT DEFAULT NULL,
    agent_id BIGINT NOT NULL,
    agent_code VARCHAR(64) NOT NULL,
    agent_name VARCHAR(128) DEFAULT NULL,
    project_name VARCHAR(128) DEFAULT NULL,
    project_key VARCHAR(128) DEFAULT NULL,
    pipeline_id BIGINT DEFAULT NULL,
    latest_version_id BIGINT DEFAULT NULL,
    published_version_id BIGINT DEFAULT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_prompt_code (prompt_code),
    KEY idx_prompt_agent (agent_id, status),
    KEY idx_prompt_project (project_key, pipeline_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS prompt_engineering_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    prompt_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    version_name VARCHAR(128) DEFAULT NULL,
    system_prompt MEDIUMTEXT DEFAULT NULL,
    user_prompt_template MEDIUMTEXT DEFAULT NULL,
    variable_definitions MEDIUMTEXT DEFAULT NULL,
    changelog TEXT DEFAULT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    latest_score INT DEFAULT NULL,
    publish_time DATETIME DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_prompt_version_no (prompt_id, version_no),
    KEY idx_prompt_version_status (prompt_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS prompt_engineering_test_case (
    id BIGINT NOT NULL AUTO_INCREMENT,
    prompt_id BIGINT NOT NULL,
    case_name VARCHAR(128) NOT NULL,
    input_json MEDIUMTEXT DEFAULT NULL,
    expected_output MEDIUMTEXT DEFAULT NULL,
    scoring_rule TEXT DEFAULT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_prompt_case (prompt_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS prompt_engineering_eval_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_code VARCHAR(64) NOT NULL,
    prompt_id BIGINT NOT NULL,
    version_id BIGINT NOT NULL,
    model_id BIGINT DEFAULT NULL,
    model_code VARCHAR(64) DEFAULT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
    overall_score INT DEFAULT 0,
    metrics_json MEDIUMTEXT DEFAULT NULL,
    summary TEXT DEFAULT NULL,
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
    UNIQUE KEY uk_prompt_eval_run_code (run_code),
    KEY idx_prompt_eval_version (version_id, create_time),
    KEY idx_prompt_eval_prompt (prompt_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS prompt_engineering_eval_result (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id BIGINT NOT NULL,
    prompt_id BIGINT NOT NULL,
    version_id BIGINT NOT NULL,
    test_case_id BIGINT DEFAULT NULL,
    case_name VARCHAR(128) DEFAULT NULL,
    input_json MEDIUMTEXT DEFAULT NULL,
    expected_output MEDIUMTEXT DEFAULT NULL,
    predicted_output MEDIUMTEXT DEFAULT NULL,
    score INT DEFAULT 0,
    passed TINYINT NOT NULL DEFAULT 0,
    dimension_scores MEDIUMTEXT DEFAULT NULL,
    feedback TEXT DEFAULT NULL,
    raw_result MEDIUMTEXT DEFAULT NULL,
    duration_ms INT DEFAULT NULL,
    error_message TEXT DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_prompt_eval_result_run (run_id),
    KEY idx_prompt_eval_result_version (version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS prompt_engineering_optimize_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    prompt_id BIGINT NOT NULL,
    source_version_id BIGINT NOT NULL,
    target_version_id BIGINT DEFAULT NULL,
    model_id BIGINT DEFAULT NULL,
    model_code VARCHAR(64) DEFAULT NULL,
    optimize_goal TEXT DEFAULT NULL,
    optimization_summary TEXT DEFAULT NULL,
    raw_result MEDIUMTEXT DEFAULT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
    error_message TEXT DEFAULT NULL,
    input_tokens INT DEFAULT 0,
    output_tokens INT DEFAULT 0,
    total_tokens INT DEFAULT 0,
    start_time DATETIME DEFAULT NULL,
    end_time DATETIME DEFAULT NULL,
    duration_ms INT DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_prompt_optimize_version (source_version_id, create_time),
    KEY idx_prompt_optimize_prompt (prompt_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Existing deployments are upgraded by PromptEngineeringSchemaInitializer,
-- which adds these runtime prompt columns only when they are missing:
-- prompt_id, prompt_version_id, system_prompt, user_prompt_template.
