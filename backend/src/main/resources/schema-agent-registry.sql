-- Agent注册表
CREATE TABLE IF NOT EXISTS ai_agent_registration (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_code VARCHAR(64) NOT NULL COMMENT 'Agent编码',
    agent_name VARCHAR(128) NOT NULL COMMENT 'Agent名称',
    description VARCHAR(512) COMMENT '能力描述',
    category VARCHAR(64) COMMENT '分类',
    registry_type VARCHAR(16) NOT NULL DEFAULT 'PUSH' COMMENT '注册方式: PUSH/PULL',
    api_url VARCHAR(256) COMMENT 'API地址',
    health_endpoint VARCHAR(128) DEFAULT '/health' COMMENT '健康检查端点',
    request_schema TEXT COMMENT '请求Schema',
    response_schema TEXT COMMENT '响应Schema',
    instance_id VARCHAR(64) NOT NULL DEFAULT 'default' COMMENT '实例ID',
    heartbeat_interval INT DEFAULT 30 COMMENT '心跳间隔(秒)',
    heartbeat_timeout INT DEFAULT 90 COMMENT '心跳超时(秒)',
    status TINYINT DEFAULT 0 COMMENT '状态: 0-待激活, 1-在线, 2-离线, 3-已注销',
    owner_id BIGINT COMMENT '所有者ID',
    last_heartbeat DATETIME COMMENT '最后心跳时间',
    registered_time DATETIME COMMENT '注册时间',
    unregistered_time DATETIME COMMENT '注销时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_agent_instance (agent_code, instance_id),
    INDEX idx_status (status),
    INDEX idx_registry_type (registry_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Agent注册事件表
CREATE TABLE IF NOT EXISTS ai_agent_registration_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_code VARCHAR(64) NOT NULL COMMENT 'Agent编码',
    instance_id VARCHAR(64) NOT NULL DEFAULT 'default',
    event_type VARCHAR(32) NOT NULL COMMENT '事件类型',
    previous_status TINYINT COMMENT '变更前状态',
    current_status TINYINT COMMENT '变更后状态',
    event_data JSON COMMENT '事件详情',
    source VARCHAR(32) COMMENT '事件来源',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_agent_time (agent_code, create_time),
    INDEX idx_event_type (event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 编排配置表
CREATE TABLE IF NOT EXISTS ai_workflow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_code VARCHAR(64) NOT NULL COMMENT '编排编码',
    workflow_name VARCHAR(128) NOT NULL COMMENT '编排名称',
    description VARCHAR(512) COMMENT '编排描述',
    trigger_type VARCHAR(16) NOT NULL COMMENT '触发类型: MANUAL/SCHEDULE/EVENT',
    trigger_config JSON COMMENT '触发配置',
    workflow_definition JSON NOT NULL COMMENT '编排定义',
    status TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    owner_id BIGINT,
    last_trigger_time DATETIME COMMENT '最后触发时间',
    trigger_count INT DEFAULT 0 COMMENT '触发次数',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_workflow_code (workflow_code),
    INDEX idx_trigger_type (trigger_type),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 编排执行记录表
CREATE TABLE IF NOT EXISTS ai_workflow_execution (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    execution_id VARCHAR(64) NOT NULL COMMENT '执行ID',
    workflow_id BIGINT NOT NULL COMMENT '编排ID',
    trigger_type VARCHAR(16) NOT NULL COMMENT '触发类型',
    trigger_source VARCHAR(128) COMMENT '触发来源',
    status VARCHAR(16) NOT NULL COMMENT '状态',
    start_params JSON COMMENT '启动参数',
    execution_context JSON COMMENT '执行上下文',
    result TEXT COMMENT '执行结果',
    error_message TEXT COMMENT '错误信息',
    start_time DATETIME COMMENT '开始时间',
    end_time DATETIME COMMENT '结束时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_execution_id (execution_id),
    INDEX idx_workflow_status (workflow_id, status),
    INDEX idx_trigger_type (trigger_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 修改ai_agent_heartbeat表，添加agent_code字段
ALTER TABLE ai_agent_heartbeat ADD COLUMN agent_code VARCHAR(64) COMMENT 'Agent编码' AFTER agent_id;
