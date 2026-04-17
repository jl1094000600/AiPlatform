-- ===============================================
-- AI Platform v3.0 Agent 数据库表
-- 数据库名: ai_platform
-- 版本: v3.0
-- 创建时间: 2026-04-17
-- ===============================================

USE ai_platform;

-- ===============================================
-- 1. ai_agent_info (Agent信息表)
-- ===============================================
DROP TABLE IF EXISTS ai_agent_info;
CREATE TABLE ai_agent_info (
    id                  BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '主键ID',
    agent_id            VARCHAR(64)     NOT NULL                    COMMENT 'Agent ID（唯一标识）',
    agent_name          VARCHAR(128)    NOT NULL                    COMMENT 'Agent名称',
    agent_type          TINYINT         NOT NULL                    COMMENT 'Agent类型（1=IMAGE_RECOGNITION 2=MARKETING）',
    agent_description   VARCHAR(512)                            DEFAULT NULL COMMENT 'Agent描述',
    capability          JSON                                    DEFAULT NULL COMMENT 'Agent能力（JSON格式）',
    status              TINYINT         NOT NULL    DEFAULT 0   COMMENT '状态（0=OFFLINE 1=ONLINE）',
    instance_id         VARCHAR(64)     NOT NULL    DEFAULT 'default' COMMENT '实例ID（支持多实例）',
    created_time        DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time        DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted          TINYINT         NOT NULL    DEFAULT 0   COMMENT '逻辑删除（0=未删除 1=已删除）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_id (agent_id),
    KEY idx_agent_type (agent_type),
    KEY idx_status (status),
    KEY idx_instance_id (instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent信息表';

-- ===============================================
-- 2. agent_image_recognition (图像识别Agent配置表)
-- ===============================================
DROP TABLE IF EXISTS agent_image_recognition;
CREATE TABLE agent_image_recognition (
    id                  BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '主键ID',
    agent_id            VARCHAR(64)     NOT NULL                    COMMENT 'Agent ID（关联ai_agent_info）',
    supported_formats   VARCHAR(512)                            DEFAULT NULL COMMENT '支持的图片格式（如jpg,png,gif,bmp,webp）',
    fallback_parser     VARCHAR(64)                             DEFAULT NULL COMMENT '非图像文件解析方式',
    max_file_size       INT             NOT NULL    DEFAULT 10485760 COMMENT '最大文件大小（字节，默认10MB）',
    config              JSON                                    DEFAULT NULL COMMENT '其他配置（JSON格式）',
    created_time        DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time        DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted          TINYINT         NOT NULL    DEFAULT 0   COMMENT '逻辑删除（0=未删除 1=已删除）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_id (agent_id),
    KEY idx_agent_id (agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='图像识别Agent配置表';

-- ===============================================
-- 3. agent_marketing (市场营销Agent配置表)
-- ===============================================
DROP TABLE IF EXISTS agent_marketing;
CREATE TABLE agent_marketing (
    id                  BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '主键ID',
    agent_id            VARCHAR(64)     NOT NULL                    COMMENT 'Agent ID（关联ai_agent_info）',
    tools_config        JSON                                    DEFAULT NULL COMMENT '工具配置（3个工具的JSON配置）',
    scene_configs       JSON                                    DEFAULT NULL COMMENT '场景配置（JSON格式）',
    default_scene      VARCHAR(64)                             DEFAULT NULL COMMENT '默认场景',
    created_time        DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time        DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted          TINYINT         NOT NULL    DEFAULT 0   COMMENT '逻辑删除（0=未删除 1=已删除）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_id (agent_id),
    KEY idx_agent_id (agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='市场营销Agent配置表';

-- ===============================================
-- 4. ai_agent_invocation_log (Agent调用记录表)
-- ===============================================
DROP TABLE IF EXISTS ai_agent_invocation_log;
CREATE TABLE ai_agent_invocation_log (
    id                  BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '主键ID',
    agent_id            VARCHAR(64)     NOT NULL                    COMMENT 'Agent ID',
    invocation_type     TINYINT         NOT NULL                    COMMENT '调用类型（1=IMAGE_RECOGNITION 2=TOOL_CALL）',
    input_summary       VARCHAR(512)                            DEFAULT NULL COMMENT '输入摘要',
    output_summary      VARCHAR(512)                            DEFAULT NULL COMMENT '输出摘要',
    duration_ms         INT             NOT NULL    DEFAULT 0   COMMENT '调用耗时（毫秒）',
    status              TINYINT         NOT NULL    DEFAULT 1   COMMENT '状态（1=SUCCESS 2=FAILED）',
    error_message       VARCHAR(1024)                           DEFAULT NULL COMMENT '错误信息',
    created_time        DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    is_deleted          TINYINT         NOT NULL    DEFAULT 0   COMMENT '逻辑删除（0=未删除 1=已删除）',
    PRIMARY KEY (id),
    KEY idx_agent_id (agent_id),
    KEY idx_invocation_type (invocation_type),
    KEY idx_status (status),
    KEY idx_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent调用记录表';

-- ===============================================
-- 5. marketing_sales_data (营销销售数据表) - 市场营销Agent测试数据
-- ===============================================
DROP TABLE IF EXISTS marketing_sales_data;
CREATE TABLE marketing_sales_data (
    id                  BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '主键ID',
    sales_date          DATE            NOT NULL                    COMMENT '销售日期',
    region              VARCHAR(64)                             DEFAULT NULL COMMENT '销售区域',
    product_category    VARCHAR(64)                             DEFAULT NULL COMMENT '产品类别',
    product_name        VARCHAR(128)                            DEFAULT NULL COMMENT '产品名称',
    sales_amount        DECIMAL(15,2)   NOT NULL    DEFAULT 0   COMMENT '销售额',
    order_count         INT             NOT NULL    DEFAULT 0   COMMENT '订单数',
    profit              DECIMAL(15,2)   NOT NULL    DEFAULT 0   COMMENT '利润',
    created_time        DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time        DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted          TINYINT         NOT NULL    DEFAULT 0   COMMENT '逻辑删除（0=未删除 1=已删除）',
    PRIMARY KEY (id),
    KEY idx_sales_date (sales_date),
    KEY idx_region (region),
    KEY idx_product_category (product_category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='营销销售数据表';

-- ===============================================
-- 初始化Agent数据
-- ===============================================
INSERT INTO ai_agent_info (agent_id, agent_name, agent_type, agent_description, capability, status, instance_id)
VALUES
('image-recognition-agent', '图像识别Agent', 1, '提供图像识别和非图像文件文本提取功能', '["recognize", "process", "health"]', 0, 'default'),
('marketing-agent', '市场营销Agent', 2, '提供销售数据分析、同比环比分析、统计汇总排名功能', '["sales_data_query", "trend_analysis", "statistics_ranking"]', 0, 'default');

-- ===============================================
-- 初始化图像识别Agent配置
-- ===============================================
INSERT INTO agent_image_recognition (agent_id, supported_formats, fallback_parser, max_file_size, config)
VALUES
('image-recognition-agent', 'jpg,jpeg,png,gif,bmp,webp', 'tika', 10485760, '{"ocrEnabled": true, "detailLevel": "high"}');

-- ===============================================
-- 初始化市场营销Agent配置
-- ===============================================
INSERT INTO agent_marketing (agent_id, tools_config, scene_configs, default_scene)
VALUES
('marketing-agent',
 '{"sales_data_query": {"enabled": true, "timeout": 5000}, "trend_analysis": {"enabled": true, "timeout": 8000}, "statistics_ranking": {"enabled": true, "timeout": 6000}}',
 '{"monthly_review": {"name": "月度销售复盘", "tools": ["sales_data_query", "trend_analysis", "statistics_ranking"]}, "quarterly_report": {"name": "季度业绩报告", "tools": ["trend_analysis", "statistics_ranking"]}}',
 'monthly_review');

-- ===============================================
-- 初始化测试销售数据
-- ===============================================
INSERT INTO marketing_sales_data (sales_date, region, product_category, product_name, sales_amount, order_count, profit) VALUES
-- 2025年Q1数据（用于同比分析）
('2025-01-01', '华东', '电子产品', 'iPhone 15', 150000.00, 300, 45000.00),
('2025-01-01', '华东', '电子产品', 'MacBook Pro', 120000.00, 150, 36000.00),
('2025-01-01', '华东', '配件', 'AirPods Pro', 95000.00, 475, 28500.00),
('2025-01-01', '华北', '电子产品', 'iPhone 15', 130000.00, 260, 39000.00),
('2025-01-01', '华北', '电子产品', 'Samsung Galaxy', 85000.00, 170, 25500.00),
('2025-01-01', '华南', '电子产品', 'iPhone 15', 140000.00, 280, 42000.00),
('2025-02-01', '华东', '电子产品', 'iPhone 15', 180000.00, 360, 54000.00),
('2025-02-01', '华东', '电子产品', 'MacBook Pro', 145000.00, 180, 43500.00),
('2025-02-01', '华北', '电子产品', 'iPhone 15', 155000.00, 310, 46500.00),
('2025-03-01', '华东', '电子产品', 'iPhone 15', 165000.00, 330, 49500.00),
('2025-03-01', '华南', '配件', 'AirPods Pro', 110000.00, 550, 33000.00),
-- 2026年Q1数据（用于测试）
('2026-01-01', '华东', '电子产品', 'iPhone 15', 180000.00, 360, 54000.00),
('2026-01-01', '华东', '电子产品', 'MacBook Pro', 150000.00, 200, 45000.00),
('2026-01-01', '华东', '配件', 'AirPods Pro', 115000.00, 575, 34500.00),
('2026-01-01', '华北', '电子产品', 'iPhone 15', 155000.00, 310, 46500.00),
('2026-01-01', '华北', '电子产品', 'Samsung Galaxy', 95000.00, 190, 28500.00),
('2026-01-01', '华南', '电子产品', 'iPhone 15', 170000.00, 340, 51000.00),
('2026-02-01', '华东', '电子产品', 'iPhone 15', 210000.00, 420, 63000.00),
('2026-02-01', '华东', '电子产品', 'MacBook Pro', 175000.00, 220, 52500.00),
('2026-02-01', '华北', '电子产品', 'iPhone 15', 180000.00, 360, 54000.00),
('2026-03-01', '华东', '电子产品', 'iPhone 15', 195000.00, 390, 58500.00),
('2026-03-01', '华南', '配件', 'AirPods Pro', 130000.00, 650, 39000.00),
('2026-03-01', '华东', '电子产品', 'iPad Pro', 85000.00, 170, 25500.00);
