-- TTS Agent Schema for AI Platform v3.0
-- Created: 2026-04-18

-- TTS配置表
CREATE TABLE IF NOT EXISTS `ai_tts_config` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `config_key` VARCHAR(100) NOT NULL COMMENT '配置键',
    `provider` VARCHAR(50) NOT NULL DEFAULT 'edge' COMMENT 'TTS提供商: edge, volcengine, azure, google',
    `default_voice` VARCHAR(100) COMMENT '默认语音',
    `api_key` VARCHAR(500) COMMENT 'API密钥(加密存储)',
    `api_endpoint` VARCHAR(500) COMMENT 'API端点',
    `max_text_length` INT DEFAULT 5000 COMMENT '最大文本长度',
    `output_format` VARCHAR(8) DEFAULT 'mp3' COMMENT '输出格式',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
    UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TTS配置表';

-- TTS任务记录表
CREATE TABLE IF NOT EXISTS `ai_tts_task` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `task_id` VARCHAR(64) NOT NULL COMMENT '任务ID',
    `agent_code` VARCHAR(100) NOT NULL COMMENT 'Agent代码',
    `text_content` TEXT NOT NULL COMMENT '待合成文本',
    `voice_id` VARCHAR(32) COMMENT '音色ID',
    `speed` FLOAT DEFAULT 1.0 COMMENT '语速(0.5-2.0)',
    `volume` INT DEFAULT 100 COMMENT '音量(0-100)',
    `output_format` VARCHAR(8) DEFAULT 'mp3' COMMENT '输出格式',
    `status` VARCHAR(16) DEFAULT 'PENDING' COMMENT '状态: PENDING, PROCESSING, SUCCESS, FAILED',
    `audio_url` VARCHAR(256) COMMENT '音频URL',
    `duration` FLOAT COMMENT '音频时长(秒)',
    `error_message` VARCHAR(512) COMMENT '错误信息',
    `session_id` VARCHAR(100) COMMENT '会话ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY `uk_task_id` (`task_id`),
    KEY `idx_agent_code` (`agent_code`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TTS任务记录表';

-- 插入默认TTS配置
INSERT INTO `ai_tts_config` (`config_key`, `provider`, `default_voice`, `max_text_length`, `output_format`, `status`)
VALUES ('default', 'edge', 'zh-CN-female-1', 5000, 'mp3', 1)
ON DUPLICATE KEY UPDATE `update_time` = CURRENT_TIMESTAMP;

-- 添加Agent心跳记录表索引(如果不存在)
-- 注意: ai_agent_heartbeat 表已在之前创建
