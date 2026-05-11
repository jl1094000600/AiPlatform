-- User memory management for short-term Redis memories and compressed MySQL summaries.

CREATE TABLE IF NOT EXISTS ai_user_memory (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    memory_code        VARCHAR(64)  NOT NULL,
    user_key           VARCHAR(128) NOT NULL,
    user_id            BIGINT       DEFAULT NULL,
    username           VARCHAR(64)  DEFAULT NULL,
    source_type        VARCHAR(32)  NOT NULL DEFAULT 'PIPELINE',
    source_id          BIGINT       DEFAULT NULL,
    summary_content    MEDIUMTEXT   NOT NULL,
    raw_count          INT          NOT NULL DEFAULT 0,
    compression_model  VARCHAR(128) DEFAULT NULL,
    memory_start_time  DATETIME     DEFAULT NULL,
    memory_end_time    DATETIME     DEFAULT NULL,
    create_time        DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted         TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_memory_code (memory_code),
    KEY idx_memory_user_time (user_key, create_time),
    KEY idx_memory_source (source_type, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Existing deployments are patched automatically by UserMemorySchemaInitializer.
-- Fresh deployments should use schema-automation-pipeline.sql, which already includes
-- automation_pipeline.initiator_user_id and automation_pipeline.initiator_username.
