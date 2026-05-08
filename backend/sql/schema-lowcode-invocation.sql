-- Low-code invocation records

CREATE TABLE IF NOT EXISTS lowcode_invocation_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    agent_id BIGINT NOT NULL COMMENT 'Agent ID',
    agent_code VARCHAR(64) DEFAULT NULL COMMENT 'Agent编码',
    template_code VARCHAR(64) DEFAULT NULL COMMENT '模板编码',
    input_params JSON DEFAULT NULL COMMENT '输入参数',
    output_result JSON DEFAULT NULL COMMENT '输出结果',
    status VARCHAR(16) NOT NULL COMMENT 'RUNNING/SUCCESS/FAILED',
    error_message VARCHAR(512) DEFAULT NULL COMMENT '错误信息',
    duration_ms INT DEFAULT NULL COMMENT '耗时',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_invocation_agent_time (agent_id, create_time),
    KEY idx_invocation_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='低代码调用记录表';
