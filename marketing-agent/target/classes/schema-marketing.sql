-- ===============================================
-- 市场营销Agent 数据库表
-- ===========================================

USE ai_platform;

-- ===============================================
-- marketing_sales_data (销售数据表)
-- ===============================================
DROP TABLE IF EXISTS marketing_sales_data;
CREATE TABLE marketing_sales_data (
    id                  BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '主键ID',
    sales_date          DATE            NOT NULL                    COMMENT '销售日期',
    region              VARCHAR(50)     NOT NULL                    COMMENT '区域',
    product_code        VARCHAR(50)                         DEFAULT NULL COMMENT '产品编码',
    product_name        VARCHAR(100)    NOT NULL                    COMMENT '产品名称',
    product_category    VARCHAR(50)                         DEFAULT NULL COMMENT '产品类别',
    sales_amount        DECIMAL(12,2)   NOT NULL    DEFAULT 0   COMMENT '销售额',
    sales_quantity      INT             NOT NULL    DEFAULT 0   COMMENT '销售数量',
    profit_amount       DECIMAL(12,2)                         DEFAULT NULL COMMENT '利润金额',
    create_time         DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted          TINYINT         NOT NULL    DEFAULT 0   COMMENT '逻辑删除（0=未删除 1=已删除）',
    PRIMARY KEY (id),
    KEY idx_sales_date (sales_date),
    KEY idx_region (region),
    KEY idx_product_code (product_code),
    KEY idx_product_category (product_category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='销售数据表';

-- ===============================================
-- 初始化示例数据
-- ===============================================
INSERT INTO marketing_sales_data (sales_date, region, product_code, product_name, product_category, sales_amount, sales_quantity, profit_amount) VALUES
('2026-01-01', '华东', 'ELEC001', 'iPhone 15', '电子产品', 15000.00, 30, 4500.00),
('2026-01-01', '华东', 'ELEC002', 'MacBook Pro', '电子产品', 25000.00, 15, 7500.00),
('2026-01-01', '华北', 'ELEC003', 'Galaxy S24', '电子产品', 12000.00, 25, 3600.00),
('2026-01-01', '华南', 'CLO001', '衬衫', '服装', 5000.00, 100, 1500.00),
('2026-01-01', '华中', 'FOOD001', '有机食品', '食品', 8000.00, 80, 2400.00),
('2026-01-15', '华东', 'ELEC001', 'iPhone 15', '电子产品', 18000.00, 36, 5400.00),
('2026-01-15', '华北', 'ELEC002', 'MacBook Pro', '电子产品', 22000.00, 12, 6600.00),
('2026-02-01', '华东', 'ELEC001', 'iPhone 15', '电子产品', 20000.00, 40, 6000.00),
('2026-02-01', '华南', 'CLO001', '衬衫', '服装', 6000.00, 120, 1800.00),
('2026-02-15', '华中', 'FOOD001', '有机食品', '食品', 9500.00, 95, 2850.00),
('2026-03-01', '华东', 'ELEC003', 'Galaxy S24', '电子产品', 14000.00, 28, 4200.00),
('2026-03-01', '华北', 'ELEC001', 'iPhone 15', '电子产品', 16000.00, 32, 4800.00),
('2026-03-15', '华南', 'CLO002', '牛仔裤', '服装', 4500.00, 90, 1350.00);
