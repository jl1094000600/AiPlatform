USE ai_platform;

CREATE TABLE IF NOT EXISTS ai_dataset (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    dataset_code  VARCHAR(50)  NOT NULL COMMENT 'Dataset code',
    dataset_name  VARCHAR(100) NOT NULL COMMENT 'Dataset name',
    description   TEXT                  COMMENT 'Dataset description',
    category      VARCHAR(50)            COMMENT 'Dataset category',
    format        VARCHAR(20)  NOT NULL COMMENT 'Data format, such as jsonl',
    size          BIGINT                 COMMENT 'File size in bytes',
    file_path     VARCHAR(500)           COMMENT 'Stored file path',
    record_count  INT          DEFAULT 0 COMMENT 'Record count',
    field_schema  TEXT                  COMMENT 'Field schema JSON',
    status        TINYINT      DEFAULT 1 COMMENT '1 enabled, 0 disabled',
    owner_id      BIGINT                 COMMENT 'Owner user id',
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    is_deleted    TINYINT      DEFAULT 0 COMMENT '0 active, 1 deleted',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dataset_code (dataset_code),
    KEY idx_dataset_category_format (category, format),
    KEY idx_dataset_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Dataset management table';
