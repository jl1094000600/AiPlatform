-- ===============================================
-- 市场营销Agent 数据库表
-- ===============================================

USE ai_platform;

-- ===============================================
-- marketing_sales_data (营销销售数据表)
-- ===============================================
DROP TABLE IF EXISTS marketing_sales_data;
CREATE TABLE marketing_sales_data (
    id                  BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '主键ID',
    sales_date          DATE            NOT NULL                    COMMENT '销售日期',
    region              VARCHAR(50)                             DEFAULT NULL COMMENT '销售区域',
    product_code        VARCHAR(50)                             DEFAULT NULL COMMENT '产品编码',
    product_name        VARCHAR(100)                            DEFAULT NULL COMMENT '产品名称',
    sales_amount        DECIMAL(12,2)   NOT NULL    DEFAULT 0   COMMENT '销售额',
    sales_quantity      INT             NOT NULL    DEFAULT 0   COMMENT '销售数量',
    profit_amount       DECIMAL(12,2)   NOT NULL    DEFAULT 0   COMMENT '利润金额',
    data_source_config  VARCHAR(500)                            DEFAULT NULL COMMENT '数据来源配置',
    create_time         DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    is_deleted          TINYINT         NOT NULL    DEFAULT 0   COMMENT '软删除标记',
    PRIMARY KEY (id),
    KEY idx_sales_date (sales_date),
    KEY idx_region (region),
    KEY idx_product_code (product_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='营销销售数据表';
