-- Agent quality monitoring and runtime config

CREATE TABLE IF NOT EXISTS ai_agent_runtime_config (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    agent_id        BIGINT       NOT NULL,
    agent_code      VARCHAR(64)  NOT NULL,
    model_id        BIGINT       DEFAULT NULL,
    dataset_id      BIGINT       DEFAULT NULL,
    prompt_id       BIGINT       DEFAULT NULL,
    prompt_version_id BIGINT     DEFAULT NULL,
    system_prompt   MEDIUMTEXT   DEFAULT NULL,
    user_prompt_template MEDIUMTEXT DEFAULT NULL,
    top_k           INT          NOT NULL DEFAULT 5,
    temperature     DOUBLE       NOT NULL DEFAULT 0.7,
    input_field     VARCHAR(64)  NOT NULL DEFAULT 'input',
    expected_field  VARCHAR(64)  NOT NULL DEFAULT 'expectedOutput',
    enabled         TINYINT      NOT NULL DEFAULT 1,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_id (agent_id),
    KEY idx_agent_code (agent_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_agent_quality_run (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    run_code         VARCHAR(64)  NOT NULL,
    agent_id         BIGINT       NOT NULL,
    agent_code       VARCHAR(64)  NOT NULL,
    dataset_id       BIGINT       NOT NULL,
    model_id         BIGINT       DEFAULT NULL,
    top_k            INT          NOT NULL DEFAULT 5,
    temperature      DOUBLE       NOT NULL DEFAULT 0.7,
    input_field      VARCHAR(64)  NOT NULL DEFAULT 'input',
    expected_field   VARCHAR(64)  NOT NULL DEFAULT 'expectedOutput',
    sample_count     INT          DEFAULT 0,
    accuracy         DOUBLE       DEFAULT 0,
    precision_score  DOUBLE       DEFAULT 0,
    recall_score     DOUBLE       DEFAULT 0,
    f1_score         DOUBLE       DEFAULT 0,
    status           TINYINT      NOT NULL DEFAULT 1,
    start_time       DATETIME     DEFAULT NULL,
    end_time         DATETIME     DEFAULT NULL,
    error_message    TEXT         DEFAULT NULL,
    create_time      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_run_code (run_code),
    KEY idx_agent_time (agent_id, create_time),
    KEY idx_dataset_id (dataset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_agent_quality_result (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    run_id            BIGINT       NOT NULL,
    sample_index      INT          NOT NULL,
    input_text        TEXT         DEFAULT NULL,
    expected_output   TEXT         DEFAULT NULL,
    predicted_output  TEXT         DEFAULT NULL,
    matched           TINYINT      NOT NULL DEFAULT 0,
    duration_ms       INT          DEFAULT 0,
    error_message     TEXT         DEFAULT NULL,
    create_time       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_run_id (run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
