-- Image Recognition Agent Database Schema
-- 创建图像识别任务表
CREATE TABLE IF NOT EXISTS `image_recognition_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id` VARCHAR(64) NOT NULL COMMENT '任务ID',
    `agent_id` BIGINT COMMENT 'Agent ID',
    `instance_id` VARCHAR(64) COMMENT '实例ID',
    `input_type` VARCHAR(32) NOT NULL COMMENT '输入类型: url, base64',
    `input_data` TEXT NOT NULL COMMENT '输入数据',
    `file_type` VARCHAR(32) COMMENT '文件类型',
    `result` TEXT COMMENT '识别/解析结果',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-处理中, 2-成功, 3-失败',
    `error_message` TEXT COMMENT '错误信息',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_id` (`task_id`),
    KEY `idx_agent_id` (`agent_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图像识别任务表';
