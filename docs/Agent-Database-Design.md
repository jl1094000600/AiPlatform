# Agent 数据库表结构设计 v3.0

## 1. 设计概述

本文档定义 AI Platform v3.0 中图像识别Agent和市场营销Agent的数据库表结构。

### 1.1 表清单

| 表名 | 说明 |
|------|------|
| ai_agent_info | Agent信息表 |
| agent_image_recognition | 图像识别Agent专属配置 |
| agent_marketing | 市场营销Agent专属配置 |
| ai_agent_invocation_log | Agent调用记录表 |

### 1.2 设计规范

- 不使用外键约束，应用层保证一致性
- 统一使用下划线命名
- 必须有逻辑删除字段 `is_deleted`
- `created_time` 和 `updated_time` 使用 `datetime` 类型
- `status` 使用 `TINYINT`，定义枚举值

---

## 2. 建表 SQL

### 2.1 ai_agent_info (Agent信息表)

```sql
-- ===============================================
-- ai_agent_info (Agent信息表)
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
```

**字段说明：**
- `agent_id`: Agent唯一标识，用于业务关联
- `agent_type`: 1=IMAGE_RECOGNITION(图像识别), 2=MARKETING(市场营销)
- `capability`: JSON格式，存储Agent的能力列表，如 `["image_parse", "ocr", "format_convert"]`
- `status`: 0=OFFLINE(离线), 1=ONLINE(在线)
- `instance_id`: 支持同一Agent多实例部署

---

### 2.2 agent_image_recognition (图像识别Agent配置表)

```sql
-- ===============================================
-- agent_image_recognition (图像识别Agent专属配置)
-- ===============================================
DROP TABLE IF EXISTS agent_image_recognition;
CREATE TABLE agent_image_recognition (
    id                  BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '主键ID',
    agent_id            VARCHAR(64)     NOT NULL                    COMMENT 'Agent ID（关联ai_agent_info）',
    supported_formats   VARCHAR(512)                            DEFAULT NULL COMMENT '支持的图片格式（如jpg,png,gif,bmp,webp）',
    fallback_parser     VARCHAR(64)                             DEFAULT NULL COMMENT '非图像文件解析方式',
    max_file_size      INT             NOT NULL    DEFAULT 10485760 COMMENT '最大文件大小（字节，默认10MB）',
    config              JSON                                    DEFAULT NULL COMMENT '其他配置（JSON格式）',
    created_time        DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time        DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted          TINYINT         NOT NULL    DEFAULT 0   COMMENT '逻辑删除（0=未删除 1=已删除）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_id (agent_id),
    KEY idx_agent_id (agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='图像识别Agent配置表';
```

**字段说明：**
- `supported_formats`: 支持的图片格式，多个用逗号分隔
- `fallback_parser`: 当上传非图片文件时的备用解析器
- `max_file_size`: 最大允许上传的文件大小（字节）
- `config`: 自定义配置JSON，可扩展

---

### 2.3 agent_marketing (市场营销Agent配置表)

```sql
-- ===============================================
-- agent_marketing (市场营销Agent配置表)
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
```

**字段说明：**
- `tools_config`: JSON格式，包含3个工具的配置
  ```json
  {
    "product_query": { "enabled": true, "timeout": 5000 },
    "review_generator": { "enabled": true, "timeout": 10000 },
    "social_media_poster": { "enabled": true, "timeout": 8000 }
  }
  ```
- `scene_configs`: 场景配置JSON
  ```json
  {
    "scene_1": { "name": "新品推广", "tools": ["product_query", "review_generator"] },
    "scene_2": { "name": "活动促销", "tools": ["social_media_poster"] }
  }
  ```

---

### 2.4 ai_agent_invocation_log (Agent调用记录表)

```sql
-- ===============================================
-- ai_agent_invocation_log (Agent调用记录表)
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
```

**字段说明：**
- `invocation_type`: 1=IMAGE_RECOGNITION(图像识别), 2=TOOL_CALL(工具调用)
- `input_summary`: 输入内容摘要（脱敏处理后的关键信息）
- `output_summary`: 输出内容摘要
- `duration_ms`: 整个调用的耗时
- `status`: 1=SUCCESS(成功), 2=FAILED(失败)

---

## 3. 枚举值定义

| 字段 | 值 | 说明 |
|------|-----|------|
| agent_type | 1 | IMAGE_RECOGNITION |
| agent_type | 2 | MARKETING |
| status (Agent) | 0 | OFFLINE |
| status (Agent) | 1 | ONLINE |
| invocation_type | 1 | IMAGE_RECOGNITION |
| invocation_type | 2 | TOOL_CALL |
| status (Log) | 1 | SUCCESS |
| status (Log) | 2 | FAILED |

---

## 4. 完整建表脚本

```sql
-- ===============================================
-- AI Platform v3.0 Agent 数据库表
-- 数据库名: ai_platform
-- ===============================================

USE ai_platform;

-- 1. Agent信息表
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

-- 2. 图像识别Agent配置表
DROP TABLE IF EXISTS agent_image_recognition;
CREATE TABLE agent_image_recognition (
    id                  BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '主键ID',
    agent_id            VARCHAR(64)     NOT NULL                    COMMENT 'Agent ID（关联ai_agent_info）',
    supported_formats   VARCHAR(512)                            DEFAULT NULL COMMENT '支持的图片格式',
    fallback_parser     VARCHAR(64)                             DEFAULT NULL COMMENT '非图像文件解析方式',
    max_file_size       INT             NOT NULL    DEFAULT 10485760 COMMENT '最大文件大小（字节）',
    config              JSON                                    DEFAULT NULL COMMENT '其他配置（JSON格式）',
    created_time        DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time        DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted          TINYINT         NOT NULL    DEFAULT 0   COMMENT '逻辑删除（0=未删除 1=已删除）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_id (agent_id),
    KEY idx_agent_id (agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='图像识别Agent配置表';

-- 3. 市场营销Agent配置表
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

-- 4. Agent调用记录表
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
```

---

## 5. 实体类对照

| 表名 | 实体类 | 包路径 |
|------|--------|--------|
| ai_agent_info | AgentInfo | com.aipal.entity |
| agent_image_recognition | AgentImageRecognition | com.aipal.entity |
| agent_marketing | AgentMarketing | com.aipal.entity |
| ai_agent_invocation_log | AgentInvocationLog | com.aipal.entity |
