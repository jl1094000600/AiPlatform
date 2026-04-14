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

-- ===============================================
-- ai_a2a_task (A2A任务表)
-- ===============================================
DROP TABLE IF EXISTS ai_a2a_task;
CREATE TABLE ai_a2a_task (
    id                  BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '主键ID',
    task_id             VARCHAR(64)     NOT NULL                    COMMENT '任务ID',
    session_id          VARCHAR(64)                             DEFAULT NULL COMMENT '会话ID',
    workflow_id         BIGINT                                  DEFAULT NULL COMMENT '工作流ID',
    source_agent_id     BIGINT         NOT NULL                    COMMENT '源Agent ID（调用方）',
    target_agent_id     BIGINT         NOT NULL                    COMMENT '目标Agent ID（被调用方）',
    task_type           VARCHAR(32)                             DEFAULT NULL COMMENT '任务类型',
    task_description    VARCHAR(512)                            DEFAULT NULL COMMENT '任务描述',
    context             TEXT                                    DEFAULT NULL COMMENT '上下文',
    response_format     VARCHAR(32)                             DEFAULT NULL COMMENT '响应格式',
    status              VARCHAR(16)                             DEFAULT NULL COMMENT '状态',
    result              TEXT                                    DEFAULT NULL COMMENT '结果',
    error_message       VARCHAR(1024)                           DEFAULT NULL COMMENT '错误信息',
    timeout             INT             NOT NULL    DEFAULT 30  COMMENT '超时时间（秒）',
    retry_count         INT             NOT NULL    DEFAULT 0   COMMENT '重试次数',
    start_time          DATETIME                                DEFAULT NULL COMMENT '开始时间',
    end_time            DATETIME                                DEFAULT NULL COMMENT '结束时间',
    create_time         DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_task_id (task_id),
    KEY idx_session_id (session_id),
    KEY idx_source_agent (source_agent_id),
    KEY idx_target_agent (target_agent_id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='A2A任务表';
