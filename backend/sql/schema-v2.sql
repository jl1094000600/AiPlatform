-- ===============================================
-- AI中台管理系统 v2.0 增量SQL
-- 新增：Agent 心跳表
-- ===============================================

USE ai_platform;

-- ===============================================
-- ai_agent_heartbeat (Agent心跳表)
-- ===============================================
DROP TABLE IF EXISTS ai_agent_heartbeat;
CREATE TABLE ai_agent_heartbeat (
    id                  BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '主键ID',
    agent_id            BIGINT          NOT NULL                    COMMENT 'Agent ID',
    instance_id         VARCHAR(64)     NOT NULL    DEFAULT 'default' COMMENT 'Agent实例ID',
    last_heartbeat      DATETIME                                DEFAULT NULL COMMENT '最后心跳时间',
    health_score        INT             NOT NULL    DEFAULT 100 COMMENT '健康评分（0-100）',
    endpoint            VARCHAR(512)                            DEFAULT NULL COMMENT 'Agent端点地址',
    status              TINYINT         NOT NULL    DEFAULT 1   COMMENT '状态（1=在线 2=离线）',
    create_time         DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_instance (agent_id, instance_id),
    KEY idx_agent_id (agent_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent心跳表';
