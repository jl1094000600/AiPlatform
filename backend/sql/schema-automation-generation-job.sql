-- Automation generation job queue for PRD and code generation

CREATE TABLE IF NOT EXISTS automation_generation_job (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    pipeline_id      BIGINT       NOT NULL,
    stage_run_id     BIGINT       NOT NULL,
    job_type         VARCHAR(32)  NOT NULL COMMENT 'PRD or CODE',
    status           VARCHAR(32)  NOT NULL DEFAULT 'QUEUED',
    request_user_id  VARCHAR(64)  DEFAULT NULL,
    trace_id         VARCHAR(64)  NOT NULL,
    context_snapshot MEDIUMTEXT   DEFAULT NULL,
    artifact_path    VARCHAR(512) DEFAULT NULL,
    error_message    TEXT         DEFAULT NULL,
    start_time       DATETIME     DEFAULT NULL,
    end_time         DATETIME     DEFAULT NULL,
    duration_ms      INT          DEFAULT NULL,
    create_time      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_job_status_time (status, create_time),
    KEY idx_job_pipeline_stage (pipeline_id, stage_run_id),
    KEY idx_job_trace (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
