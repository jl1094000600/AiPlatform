-- 数据集管理表
CREATE TABLE IF NOT EXISTS `ai_dataset` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `dataset_code` VARCHAR(50) NOT NULL COMMENT '数据集编码',
  `dataset_name` VARCHAR(100) NOT NULL COMMENT '数据集名称',
  `description` TEXT COMMENT '数据集描述',
  `category` VARCHAR(50) COMMENT '数据集类别',
  `format` VARCHAR(20) NOT NULL COMMENT '数据格式: json,csv,xml,xlsx,txt,parquet',
  `size` BIGINT COMMENT '文件大小(字节)',
  `file_path` VARCHAR(500) COMMENT '存储路径',
  `record_count` INT DEFAULT 0 COMMENT '记录数',
  `field_schema` TEXT COMMENT '字段schema(JSON)',
  `status` TINYINT DEFAULT 1 COMMENT '状态: 1-正常, 0-禁用',
  `owner_id` BIGINT COMMENT '所有者ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dataset_code` (`dataset_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据集管理表';

-- 测评任务表
CREATE TABLE IF NOT EXISTS `ai_evaluation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `evaluation_code` VARCHAR(50) NOT NULL COMMENT '测评编码',
  `evaluation_name` VARCHAR(200) COMMENT '测评名称',
  `description` TEXT COMMENT '测评描述',
  `dataset_id` BIGINT NOT NULL COMMENT '数据集ID',
  `agent_id` BIGINT NOT NULL COMMENT 'Agent ID',
  `criteria_config` TEXT COMMENT '测评标准配置(JSON)',
  `result_data` TEXT COMMENT '测评结果(JSON)',
  `total_score` DOUBLE COMMENT '总分',
  `status` TINYINT DEFAULT 1 COMMENT '状态: 0-待执行, 1-执行中, 2-已完成, 3-失败',
  `start_time` DATETIME COMMENT '开始时间',
  `end_time` DATETIME COMMENT '结束时间',
  `executor_id` BIGINT COMMENT '执行者ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_evaluation_code` (`evaluation_code`),
  KEY `idx_dataset_id` (`dataset_id`),
  KEY `idx_agent_id` (`agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测评任务表';

-- 测评标准表
CREATE TABLE IF NOT EXISTS `ai_evaluation_criteria` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `criteria_code` VARCHAR(50) NOT NULL COMMENT '标准编码',
  `criteria_name` VARCHAR(100) NOT NULL COMMENT '标准名称',
  `description` TEXT COMMENT '标准描述',
  `type` VARCHAR(20) COMMENT '类型: accuracy,latency,stability,custom',
  `formula` VARCHAR(500) COMMENT '评分公式',
  `weight` DOUBLE DEFAULT 1.0 COMMENT '权重',
  `thresholds` VARCHAR(200) COMMENT '阈值配置(JSON)',
  `status` TINYINT DEFAULT 1 COMMENT '状态: 1-启用, 0-禁用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_criteria_code` (`criteria_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测评标准表';