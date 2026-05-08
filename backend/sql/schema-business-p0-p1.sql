-- AI Platform P0+P1 业务运营能力扩展
-- 日期：2026-05-07
-- 说明：不删除旧表；新增业务驾驶舱、计费、告警、客户、低代码调用所需表。

CREATE TABLE IF NOT EXISTS biz_customer (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    customer_code VARCHAR(64) NOT NULL COMMENT '客户编码',
    customer_name VARCHAR(128) NOT NULL COMMENT '客户名称',
    contact_name VARCHAR(64) DEFAULT NULL COMMENT '联系人',
    contact_email VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    contact_phone VARCHAR(32) DEFAULT NULL COMMENT '电话',
    balance DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '账户余额',
    warning_balance DECIMAL(18,4) NOT NULL DEFAULT 100 COMMENT '余额预警线',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0冻结 1正常',
    remark VARCHAR(512) DEFAULT NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_customer_code (customer_code),
    KEY idx_customer_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户账户表';

CREATE TABLE IF NOT EXISTS billing_budget (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    budget_name VARCHAR(128) NOT NULL COMMENT '预算名称',
    scope_type VARCHAR(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '范围：GLOBAL/AGENT/MODULE/CUSTOMER',
    scope_id BIGINT DEFAULT NULL COMMENT '范围ID',
    amount DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '预算金额',
    alert_threshold DECIMAL(5,2) NOT NULL DEFAULT 80 COMMENT '告警阈值百分比',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用 1启用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_budget_scope (scope_type, scope_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='计费预算表';

CREATE TABLE IF NOT EXISTS billing_balance_transaction (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    customer_id BIGINT NOT NULL COMMENT '客户ID',
    transaction_type VARCHAR(32) NOT NULL COMMENT 'RECHARGE/DEDUCT/ADJUST',
    amount DECIMAL(18,4) NOT NULL COMMENT '变动金额',
    balance_before DECIMAL(18,4) NOT NULL COMMENT '变动前余额',
    balance_after DECIMAL(18,4) NOT NULL COMMENT '变动后余额',
    remark VARCHAR(512) DEFAULT NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_customer_time (customer_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户余额流水表';

CREATE TABLE IF NOT EXISTS billing_usage_daily (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    usage_date DATE NOT NULL COMMENT '使用日期',
    agent_id BIGINT DEFAULT NULL COMMENT 'Agent ID',
    customer_id BIGINT DEFAULT NULL COMMENT '客户ID',
    biz_module_id BIGINT DEFAULT NULL COMMENT '业务线ID',
    total_calls BIGINT NOT NULL DEFAULT 0 COMMENT '调用次数',
    total_tokens BIGINT NOT NULL DEFAULT 0 COMMENT 'Token总数',
    total_cost DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '估算成本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usage_scope (usage_date, agent_id, customer_id, biz_module_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每日计费用量表';

CREATE TABLE IF NOT EXISTS alert_rule (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    rule_name VARCHAR(128) NOT NULL COMMENT '规则名称',
    metric_type VARCHAR(64) NOT NULL COMMENT 'error_rate/response_time/offline_agents',
    operator VARCHAR(8) NOT NULL DEFAULT '>' COMMENT '比较符',
    threshold_value DECIMAL(18,4) NOT NULL COMMENT '阈值',
    level VARCHAR(8) NOT NULL DEFAULT 'P1' COMMENT 'P0/P1/P2',
    notify_channel VARCHAR(64) DEFAULT NULL COMMENT '通知渠道',
    notify_target VARCHAR(512) DEFAULT NULL COMMENT '通知目标',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用 1启用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_alert_rule_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警规则表';

CREATE TABLE IF NOT EXISTS alert_event (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    rule_id BIGINT DEFAULT NULL COMMENT '规则ID',
    rule_name VARCHAR(128) DEFAULT NULL COMMENT '规则名称',
    metric_type VARCHAR(64) NOT NULL COMMENT '指标类型',
    metric_value DECIMAL(18,4) DEFAULT NULL COMMENT '指标值',
    threshold_value DECIMAL(18,4) DEFAULT NULL COMMENT '阈值',
    level VARCHAR(8) NOT NULL COMMENT 'P0/P1/P2',
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/ACKED',
    message VARCHAR(512) DEFAULT NULL COMMENT '消息',
    trigger_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '触发时间',
    ack_time DATETIME DEFAULT NULL COMMENT '确认时间',
    ack_user VARCHAR(64) DEFAULT NULL COMMENT '确认人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_alert_event_status (status),
    KEY idx_alert_event_time (trigger_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警事件表';

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
