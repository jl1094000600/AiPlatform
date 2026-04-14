-- ===============================================
-- AI中台管理系统 数据库初始化脚本
-- 数据库: ai_platform
-- 版本: 1.0
-- 创建时间: 2026-04-13
-- ===============================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS ai_platform DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE ai_platform;

-- ===============================================
-- 1. sys_user (用户表)
-- ===============================================
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id              BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '主键ID',
    username        VARCHAR(50)     NOT NULL                    COMMENT '用户名（唯一）',
    password        VARCHAR(255)    NOT NULL                    COMMENT '密码（加密存储）',
    real_name       VARCHAR(50)                         DEFAULT NULL COMMENT '真实姓名',
    email           VARCHAR(100)                        DEFAULT NULL COMMENT '邮箱',
    phone           VARCHAR(20)                          DEFAULT NULL COMMENT '手机号',
    department      VARCHAR(100)                         DEFAULT NULL COMMENT '部门',
    status          TINYINT       NOT NULL    DEFAULT 1   COMMENT '状态（0禁用 1启用）',
    create_time     DATETIME      NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted      TINYINT       NOT NULL    DEFAULT 0   COMMENT '软删除标记（0未删除 1已删除）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ===============================================
-- 2. sys_role (角色表)
-- ===============================================
DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role (
    id              BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '主键ID',
    role_code       VARCHAR(50)     NOT NULL                    COMMENT '角色代码（唯一）',
    role_name       VARCHAR(50)     NOT NULL                    COMMENT '角色名称',
    description     VARCHAR(255)                         DEFAULT NULL COMMENT '角色描述',
    create_time     DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted      TINYINT         NOT NULL    DEFAULT 0   COMMENT '软删除标记',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- ===============================================
-- 3. sys_permission (权限表)
-- ===============================================
DROP TABLE IF EXISTS sys_permission;
CREATE TABLE sys_permission (
    id              BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '主键ID',
    permission_code VARCHAR(100)    NOT NULL                    COMMENT '权限代码（唯一）',
    permission_name VARCHAR(100)    NOT NULL                    COMMENT '权限名称',
    resource_type   VARCHAR(50)                         DEFAULT NULL COMMENT '资源类型',
    path            VARCHAR(255)                         DEFAULT NULL COMMENT '路径',
    method          VARCHAR(10)                          DEFAULT NULL COMMENT '请求方法',
    description     VARCHAR(255)                         DEFAULT NULL COMMENT '权限描述',
    parent_id       BIGINT                                DEFAULT NULL COMMENT '父权限ID',
    create_time     DATETIME      NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    is_deleted      TINYINT       NOT NULL    DEFAULT 0   COMMENT '软删除标记',
    PRIMARY KEY (id),
    UNIQUE KEY uk_permission_code (permission_code),
    KEY idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

-- ===============================================
-- 4. sys_user_role (用户角色关联表)
-- ===============================================
DROP TABLE IF EXISTS sys_user_role;
CREATE TABLE sys_user_role (
    id              BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '主键ID',
    user_id         BIGINT          NOT NULL                    COMMENT '用户ID',
    role_id         BIGINT          NOT NULL                    COMMENT '角色ID',
    create_time     DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id),
    KEY idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- ===============================================
-- 5. sys_role_permission (角色权限关联表)
-- ===============================================
DROP TABLE IF EXISTS sys_role_permission;
CREATE TABLE sys_role_permission (
    id              BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '主键ID',
    role_id         BIGINT          NOT NULL                    COMMENT '角色ID',
    permission_id   BIGINT          NOT NULL                    COMMENT '权限ID',
    create_time     DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_permission (role_id, permission_id),
    KEY idx_permission_id (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

-- ===============================================
-- 6. ai_agent (Agent表)
-- ===============================================
DROP TABLE IF EXISTS ai_agent;
CREATE TABLE ai_agent (
    id              BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '主键ID',
    agent_code      VARCHAR(50)     NOT NULL                    COMMENT 'Agent编码（唯一）',
    agent_name      VARCHAR(100)    NOT NULL                    COMMENT 'Agent名称',
    description     TEXT                                DEFAULT NULL COMMENT '描述',
    category        VARCHAR(50)                         DEFAULT NULL COMMENT '分类',
    api_url         VARCHAR(500)                        DEFAULT NULL COMMENT '接口地址',
    http_method     VARCHAR(10)                          DEFAULT NULL COMMENT '调用方式（GET/POST）',
    request_schema  JSON                                 DEFAULT NULL COMMENT '请求参数模板',
    response_schema JSON                                 DEFAULT NULL COMMENT '响应格式说明',
    status          TINYINT       NOT NULL    DEFAULT 2   COMMENT '状态（0草稿 1上线 2下线）',
    owner_id        BIGINT                                DEFAULT NULL COMMENT '负责人用户ID',
    create_time     DATETIME      NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted      TINYINT       NOT NULL    DEFAULT 0   COMMENT '软删除标记（0未删除 1已删除）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_code (agent_code),
    KEY idx_status_category (status, category),
    KEY idx_owner_id (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent表';

-- ===============================================
-- 7. ai_agent_version (Agent版本表)
-- ===============================================
DROP TABLE IF EXISTS ai_agent_version;
CREATE TABLE ai_agent_version (
    id              BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '主键ID',
    agent_id        BIGINT          NOT NULL                    COMMENT 'Agent ID',
    version         VARCHAR(20)     NOT NULL                    COMMENT '版本号（主版本号.次版本号）',
    changelog       TEXT                                DEFAULT NULL COMMENT '变更说明',
    config          JSON                                 DEFAULT NULL COMMENT '版本配置',
    status          TINYINT       NOT NULL    DEFAULT 0   COMMENT '状态（0未发布 1已发布）',
    publish_time    DATETIME                                DEFAULT NULL COMMENT '发布时间',
    create_time     DATETIME      NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_agent_id (agent_id),
    KEY idx_status (status),
    UNIQUE KEY uk_agent_version (agent_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent版本表';

-- ===============================================
-- 8. ai_model (模型表)
-- ===============================================
DROP TABLE IF EXISTS ai_model;
CREATE TABLE ai_model (
    id              BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '主键ID',
    model_code      VARCHAR(50)     NOT NULL                    COMMENT '模型编码（唯一）',
    model_name      VARCHAR(100)    NOT NULL                    COMMENT '模型名称',
    provider        VARCHAR(50)                         DEFAULT NULL COMMENT '模型厂商（OpenAI/Anthropic/阿里云等）',
    model_version   VARCHAR(50)                         DEFAULT NULL COMMENT '模型版本',
    endpoint        VARCHAR(500)                        DEFAULT NULL COMMENT 'API地址',
    api_version     VARCHAR(50)                         DEFAULT NULL COMMENT 'API版本',
    price_per_1k_token DECIMAL(10,6)                     DEFAULT NULL COMMENT '价格（元/千Token）',
    status          TINYINT       NOT NULL    DEFAULT 1   COMMENT '状态（0禁用 1启用）',
    create_time     DATETIME      NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted      TINYINT       NOT NULL    DEFAULT 0   COMMENT '软删除标记',
    PRIMARY KEY (id),
    UNIQUE KEY uk_model_code (model_code),
    KEY idx_provider (provider),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型表';

-- ===============================================
-- 9. biz_module (业务模块表)
-- ===============================================
DROP TABLE IF EXISTS biz_module;
CREATE TABLE biz_module (
    id              BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '主键ID',
    module_code     VARCHAR(50)     NOT NULL                    COMMENT '模块编码（唯一）',
    module_name     VARCHAR(100)    NOT NULL                    COMMENT '模块名称',
    description     TEXT                                DEFAULT NULL COMMENT '描述',
    owner_id        BIGINT                                DEFAULT NULL COMMENT '负责人用户ID',
    status          TINYINT       NOT NULL    DEFAULT 1   COMMENT '状态（0禁用 1启用）',
    create_time     DATETIME      NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted      TINYINT       NOT NULL    DEFAULT 0   COMMENT '软删除标记',
    PRIMARY KEY (id),
    UNIQUE KEY uk_module_code (module_code),
    KEY idx_owner_id (owner_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务模块表';

-- ===============================================
-- 10. biz_agent_auth (业务模块Agent授权表)
-- ===============================================
DROP TABLE IF EXISTS biz_agent_auth;
CREATE TABLE biz_agent_auth (
    id              BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '主键ID',
    biz_module_id   BIGINT          NOT NULL                    COMMENT '业务模块ID',
    agent_id        BIGINT          NOT NULL                    COMMENT 'Agent ID',
    agent_version   VARCHAR(20)                         DEFAULT NULL COMMENT '指定Agent版本（为空则不限制版本）',
    qps_limit       INT             NOT NULL    DEFAULT 10  COMMENT 'QPS限制',
    daily_limit     INT             NOT NULL    DEFAULT 10000 COMMENT '日调用量上限',
    status          TINYINT       NOT NULL    DEFAULT 1   COMMENT '状态（0禁用 1启用）',
    create_time     DATETIME      NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_module_agent (biz_module_id, agent_id),
    KEY idx_agent_id (agent_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务模块Agent授权表';

-- ===============================================
-- 11. mon_call_record (调用记录表)
-- ===============================================
DROP TABLE IF EXISTS mon_call_record;
CREATE TABLE mon_call_record (
    id              BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '主键ID',
    trace_id        VARCHAR(64)     NOT NULL                    COMMENT '链路ID（全局唯一）',
    agent_id        BIGINT          NOT NULL                    COMMENT 'Agent ID',
    agent_version   VARCHAR(20)                         DEFAULT NULL COMMENT 'Agent版本',
    biz_module_id   BIGINT          NOT NULL                    COMMENT '业务模块ID',
    model_id        BIGINT                                DEFAULT NULL COMMENT '模型ID',
    request_time    DATETIME        NOT NULL                    COMMENT '请求时间',
    response_time   DATETIME                                DEFAULT NULL COMMENT '响应时间',
    duration_ms     INT                                 DEFAULT NULL COMMENT '耗时（毫秒）',
    input_tokens    INT                                 DEFAULT 0   COMMENT '输入Token数',
    output_tokens   INT                                 DEFAULT 0   COMMENT '输出Token数',
    total_tokens    INT                                 DEFAULT 0   COMMENT '总Token数',
    status_code     INT                                 DEFAULT NULL COMMENT 'HTTP状态码',
    success         TINYINT       NOT NULL    DEFAULT 1   COMMENT '是否成功（0失败 1成功）',
    error_message   TEXT                                DEFAULT NULL COMMENT '错误信息',
    request_params  JSON                                 DEFAULT NULL COMMENT '请求参数',
    response_result JSON                                 DEFAULT NULL COMMENT '响应结果',
    create_time     DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_trace_id (trace_id),
    KEY idx_agent_id_create_time (agent_id, create_time),
    KEY idx_biz_module_id_create_time (biz_module_id, create_time),
    KEY idx_model_id_create_time (model_id, create_time),
    KEY idx_request_time (request_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='调用记录表';

-- ===============================================
-- 12. mon_api_metrics (接口指标表)
-- ===============================================
DROP TABLE IF EXISTS mon_api_metrics;
CREATE TABLE mon_api_metrics (
    id              BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '主键ID',
    agent_id        BIGINT          NOT NULL                    COMMENT 'Agent ID',
    biz_module_id   BIGINT          NOT NULL                    COMMENT '业务模块ID',
    model_id        BIGINT                                DEFAULT NULL COMMENT '模型ID',
    stat_date        DATE            NOT NULL                    COMMENT '统计日期',
    stat_hour        TINYINT                                 DEFAULT NULL COMMENT '统计小时（0-23）',
    total_calls     INT             NOT NULL    DEFAULT 0   COMMENT '总调用次数',
    success_calls   INT             NOT NULL    DEFAULT 0   COMMENT '成功次数',
    failed_calls    INT             NOT NULL    DEFAULT 0   COMMENT '失败次数',
    total_duration_ms BIGINT        NOT NULL    DEFAULT 0   COMMENT '总耗时（毫秒）',
    avg_duration_ms  DECIMAL(10,2)                         DEFAULT NULL COMMENT '平均响应时间（毫秒）',
    p95_duration_ms  INT                                 DEFAULT NULL COMMENT 'P95响应时间（毫秒）',
    p99_duration_ms  INT                                 DEFAULT NULL COMMENT 'P99响应时间（毫秒）',
    max_duration_ms INT                                 DEFAULT NULL COMMENT '最大响应时间（毫秒）',
    total_input_tokens BIGINT       NOT NULL    DEFAULT 0   COMMENT '总输入Token',
    total_output_tokens BIGINT      NOT NULL    DEFAULT 0   COMMENT '总输出Token',
    create_time     DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_metrics (agent_id, biz_module_id, model_id, stat_date, stat_hour),
    KEY idx_stat_date (stat_date),
    KEY idx_agent_id (agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='接口指标表（小时聚合）';

-- ===============================================
-- 13. sys_audit_log (审计日志表)
-- ===============================================
DROP TABLE IF EXISTS sys_audit_log;
CREATE TABLE sys_audit_log (
    id              BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '主键ID',
    user_id         BIGINT          NOT NULL                    COMMENT '操作人用户ID',
    username        VARCHAR(50)                         DEFAULT NULL COMMENT '操作人用户名',
    operation       VARCHAR(50)     NOT NULL                    COMMENT '操作类型',
    resource_type   VARCHAR(50)     NOT NULL                    COMMENT '资源类型',
    resource_id     BIGINT                                DEFAULT NULL COMMENT '资源ID',
    resource_code   VARCHAR(100)                         DEFAULT NULL COMMENT '资源代码',
    before_value    JSON                                 DEFAULT NULL COMMENT '修改前内容',
    after_value     JSON                                 DEFAULT NULL COMMENT '修改后内容',
    ip_address      VARCHAR(50)                          DEFAULT NULL COMMENT 'IP地址',
    user_agent      VARCHAR(500)                         DEFAULT NULL COMMENT '用户代理',
    result          TINYINT       NOT NULL    DEFAULT 1   COMMENT '操作结果（0失败 1成功）',
    error_message   VARCHAR(500)                         DEFAULT NULL COMMENT '错误信息',
    create_time     DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_user_id_create_time (user_id, create_time),
    KEY idx_operation (operation),
    KEY idx_resource_type_id (resource_type, resource_id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志表';

-- ===============================================
-- 注意：本项目不使用外键约束，所有外键关联通过应用层逻辑实现
-- 删除约束的好处：
-- 1. 避免级联删除导致的意外数据丢失
-- 2. 提高大批量数据导入性能
-- 3. 便于数据库分库分表
-- 4. 关联数据一致性由应用层保证
-- ===============================================

-- ===============================================
-- 初始化内置角色数据
-- ===============================================
INSERT INTO sys_role (role_code, role_name, description) VALUES
('SYSTEM_ADMIN', '系统管理员', '拥有系统全部权限'),
('OPS_ADMIN', '运维人员', '负责系统运维、监控管理'),
('MODULE_OWNER', '业务模块负责人', '负责所辖业务模块的Agent调用授权'),
('NORMAL_USER', '普通用户', '基础查看权限');

-- ===============================================
-- 初始化管理员用户（密码: admin123，SHA256加密）
-- admin123 的 SHA256 = 240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9
-- ===============================================
INSERT INTO sys_user (username, password, real_name, email, status) VALUES
('admin', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', '系统管理员', 'admin@example.com', 1);

-- 关联管理员与系统管理员角色
INSERT INTO sys_user_role (user_id, role_id) SELECT id, (SELECT id FROM sys_role WHERE role_code = 'SYSTEM_ADMIN') FROM sys_user WHERE username = 'admin';
