# AI Platform v3.0 架构设计评审

**评审日期**: 2026-04-17
**评审人**: architect
**评审状态**: 通过（有条件）

---

## 1. 评审范围

本次评审覆盖 v3.0 架构设计的三大核心模块：

1. **图像识别Agent技术方案**
2. **市场营销Agent技术方案**
3. **数据库表设计**

---

## 2. 图像识别Agent技术方案评审

### 2.1 方案概述

图像识别Agent（ImageRecognitionAgent）定位为视觉理解型Agent，支持多格式图像识别和文档内容提取。

### 2.2 评审意见

#### 2.2.1 技术选型 - 通过

| 评估项 | 结论 | 说明 |
|--------|------|------|
| SpringAI多模态模型集成 | 通过 | 利用SpringAI的Vision模型能力，符合SpringAI 1.0.0-M4特性 |
| Apache PDFBox/POI文档解析 | 通过 | 业界标准库，成熟稳定 |
| Base64编码传输 | 通过 | 适合小文件传输，与A2A协议兼容 |

#### 2.2.2 架构设计 - 通过

- Agent通过A2A协议与平台通信，符合现有架构规范
- 心跳机制与平台一致（30秒间隔，90秒超时）
- 多实例部署支持通过instance_id实现

#### 2.2.3 建议改进

1. **图像大小限制**: 当前设计10MB限制，建议在config中可配置化
2. **OCR能力**: 建议明确是使用云厂商OCR还是本地模型识别
3. **并发控制**: 建议增加任务队列积压告警机制

### 2.3 评审结论

**通过** - 图像识别Agent的技术方案设计合理，可进入开发阶段。

---

## 3. 市场营销Agent技术方案评审

### 3.1 方案概述

市场营销Agent（MarketingAgent）定位为数据分析型Agent，提供三个工具：
- 销售数据查询 (sales_data_query)
- 同比环比趋势分析 (trend_analysis)
- 统计汇总与排名 (statistics_ranking)

### 3.2 评审意见

#### 3.2.1 三个工具设计 - 通过

| 工具 | 评审结论 | 说明 |
|------|----------|------|
| sales_data_query | 通过 | 多维度查询设计合理，支持聚合 |
| trend_analysis | 通过 | YoY/MoM计算逻辑清晰 |
| statistics_ranking | 通过 | TOP N排名功能完整 |

#### 3.2.2 数据源问题 - 需明确

**问题**: PRD中未明确销售数据的数据来源。

**建议方案**:
1. 方案A: 接入现有数据库表（推荐，需要确认表结构）
2. 方案B: 通过A2A调用其他Agent获取数据
3. 方案C: 提供模拟数据进行演示

**建议**: 在数据库设计中增加 `agent_marketing` 表的 `data_source_config` 字段，支持灵活配置。

#### 3.2.3 工具调用设计 - 通过

工具调用通过A2A消息的content字段传递，统一输入输出格式设计合理。

### 3.3 评审结论

**通过（附带数据源需确认）** - 三个工具设计完整，工具调用协议规范。需在开发阶段明确数据来源。

---

## 4. 数据库表设计评审

### 4.1 设计的表结构

| 表名 | 说明 | 状态 |
|------|------|------|
| ai_agent_info | Agent信息表 | 通过 |
| agent_image_recognition | 图像识别Agent配置 | 通过 |
| agent_marketing | 市场营销Agent配置 | 通过 |
| ai_agent_invocation_log | Agent调用记录表 | 通过 |

### 4.2 逐表评审

#### 4.2.1 ai_agent_info - 通过

```sql
CREATE TABLE ai_agent_info (
    id                  BIGINT          NOT NULL    AUTO_INCREMENT,
    agent_id            VARCHAR(64)     NOT NULL,
    agent_name          VARCHAR(128)    NOT NULL,
    agent_type          TINYINT         NOT NULL,    -- 1=IMAGE_RECOGNITION 2=MARKETING
    agent_description   VARCHAR(512),
    capability          JSON,                        -- Agent能力列表
    status              TINYINT         NOT NULL    DEFAULT 0,  -- 0=OFFLINE 1=ONLINE
    instance_id         VARCHAR(64)     NOT NULL    DEFAULT 'default',
    created_time        DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP,
    updated_time        DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted          TINYINT         NOT NULL    DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_id (agent_id),
    KEY idx_agent_type (agent_type),
    KEY idx_status (status),
    KEY idx_instance_id (instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**评审意见**:
- agent_type使用TINYINT + 枚举值，符合规范
- capability字段使用JSON格式，支持动态扩展
- instance_id支持多实例，设计合理
- 逻辑删除is_deleted字段符合要求

#### 4.2.2 agent_image_recognition - 通过

```sql
CREATE TABLE agent_image_recognition (
    id                  BIGINT          NOT NULL    AUTO_INCREMENT,
    agent_id            VARCHAR(64)     NOT NULL,
    supported_formats   VARCHAR(512),              -- jpg,png,gif,bmp,webp
    fallback_parser     VARCHAR(64),               -- 非图像文件解析方式
    max_file_size       INT             NOT NULL    DEFAULT 10485760,
    config              JSON,                      -- 其他配置
    created_time        DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP,
    updated_time        DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted          TINYINT         NOT NULL    DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_id (agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**评审意见**:
- supported_formats字段支持格式可配置化，通过
- fallback_parser设计灵活，支持多种解析器
- max_file_size 10MB默认值合理
- 缺少与ai_agent_info的关联说明（应用层保证一致性，符合项目规范）

#### 4.2.3 agent_marketing - 通过（建议改进）

```sql
CREATE TABLE agent_marketing (
    id                  BIGINT          NOT NULL    AUTO_INCREMENT,
    agent_id            VARCHAR(64)     NOT NULL,
    tools_config        JSON,                      -- 3个工具的配置
    scene_configs       JSON,                      -- 场景配置
    default_scene      VARCHAR(64),
    created_time        DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP,
    updated_time        DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted          TINYINT         NOT NULL    DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_id (agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**评审意见**:
- tools_config和scene_configs的JSON结构清晰，通过
- **建议增加**: data_source_config字段，明确数据来源配置

#### 4.2.4 ai_agent_invocation_log - 通过

```sql
CREATE TABLE ai_agent_invocation_log (
    id                  BIGINT          NOT NULL    AUTO_INCREMENT,
    agent_id            VARCHAR(64)     NOT NULL,
    invocation_type     TINYINT         NOT NULL,   -- 1=IMAGE_RECOGNITION 2=TOOL_CALL
    input_summary       VARCHAR(512),
    output_summary      VARCHAR(512),
    duration_ms         INT             NOT NULL    DEFAULT 0,
    status              TINYINT         NOT NULL    DEFAULT 1,  -- 1=SUCCESS 2=FAILED
    error_message       VARCHAR(1024),
    created_time        DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP,
    is_deleted          TINYINT         NOT NULL    DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_agent_id (agent_id),
    KEY idx_invocation_type (invocation_type),
    KEY idx_status (status),
    KEY idx_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**评审意见**:
- invocation_type区分调用类型，设计合理
- input_summary和output_summary字段命名统一，符合规范
- duration_ms记录耗时，便于性能分析
- 索引设计合理，查询效率有保障

### 4.3 设计规范符合度检查

| 规范项 | 是否符合 | 说明 |
|--------|----------|------|
| 不使用外键约束 | 是 | 应用层保证一致性 |
| 下划线命名 | 是 | 全部使用下划线命名 |
| 逻辑删除is_deleted | 是 | 所有表都有is_deleted字段 |
| datetime类型时间字段 | 是 | 使用DATETIME类型 |
| TINYINT状态枚举 | 是 | status使用TINYINT |

### 4.4 评审结论

**通过** - 数据库表设计符合项目规范，设计合理。agent_marketing表建议增加data_source_config字段。

---

## 5. 总体评审结论

### 5.1 评审结果汇总

| 模块 | 评审结论 | 备注 |
|------|----------|------|
| 图像识别Agent技术方案 | 通过 | 可进入开发阶段 |
| 市场营销Agent技术方案 | 通过（附带条件） | 数据来源需确认 |
| 数据库表设计 | 通过 | agent_marketing建议增加字段 |

### 5.2 后续行动项

| 行动项 | 负责人 | 优先级 |
|--------|--------|--------|
| 确认市场营销Agent数据来源 | product-manager | 高 |
| agent_marketing表增加data_source_config字段 | architect | 中 |
| 确认OCR能力实现方式（云厂商/本地） | backend-dev | 中 |

### 5.3 风险提示

1. **数据源风险**: 市场营销Agent依赖销售数据，需尽早确认数据来源
2. **多模态模型风险**: 图像识别依赖SpringAI的Vision能力，需确认模型配置
3. **性能风险**: 大文件图像处理可能耗时较长，建议增加超时控制和降级机制

---

**评审签字**: architect
**日期**: 2026-04-17
