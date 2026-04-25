# AI Platform v3.0 架构评审文档

| 版本 | 日期 | 作者 | 备注 |
|------|------|------|------|
| 1.0 | 2026-04-18 | Architect | 初始评审 |

---

## 1. 现有系统架构概述

### 1.1 技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| 后端 | Spring Boot 3.2 + JDK 21 | 主框架 |
| ORM | MyBatis-Plus | 数据访问 |
| AI集成 | SpringAI 1.0.0-M4 | Agent调用 |
| 数据库 | MySQL 8.0 | 主数据存储 |
| 缓存 | Redis | 心跳/消息队列 |
| 前端 | Vue3 + Element Plus + Vite | 管理后台 |

### 1.2 现有数据库表结构

| 表名 | 说明 | Agent类型 |
|------|------|-----------|
| ai_agent_info | Agent基础信息表 | 通用 |
| agent_image_recognition | 图像识别Agent配置 | IMAGE_RECOGNITION(1) |
| agent_marketing | 市场营销Agent配置 | MARKETING(2) |
| ai_agent_invocation_log | Agent调用记录表 | 通用 |
| marketing_sales_data | 营销测试数据 | MARKETING |

---

## 2. TTS Agent 数据库设计评审

### 2.1 设计方案

TTS Agent 需要新增以下表结构：

```sql
-- TTS Agent配置表
CREATE TABLE agent_tts (
    id                  BIGINT          NOT NULL    AUTO_INCREMENT,
    agent_id            VARCHAR(64)     NOT NULL    COMMENT 'Agent ID',
    voice_name          VARCHAR(64)     NOT NULL    DEFAULT 'zh-CN-XiaoxiaoNeural'  COMMENT '语音名称',
    speech_rate         INT             NOT NULL    DEFAULT 0       COMMENT '语速(-500到500)',
    pitch               INT             NOT NULL    DEFAULT 0       COMMENT '音调(-500到500)',
    volume              INT             NOT NULL    DEFAULT 100     COMMENT '音量(0-200)',
    output_format       VARCHAR(16)     NOT NULL    DEFAULT 'audio-16khz-32kbitrate-mono-mp3'  COMMENT '输出格式',
    config              JSON            DEFAULT NULL COMMENT '其他配置',
    created_time        DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP,
    updated_time        DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted          TINYINT         NOT NULL    DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_id (agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='TTS语音合成Agent配置表';
```

### 2.2 评审意见

| 评审项 | 评分 | 说明 |
|--------|------|------|
| 表结构设计 | 8/10 | 设计合理，遵循项目规范 |
| 字段完整性 | 7/10 | 缺少音频输出编码、采样率等高级配置 |
| 扩展性 | 7/10 | config字段提供了一定的扩展能力 |
| 索引设计 | 9/10 | 唯一索引设计合理 |

### 2.3 优化建议

1. **增强配置字段**：
   - 建议增加 `audio_encoding` 字段明确编码格式
   - 建议增加 `sample_rate` 字段支持不同采样率

2. **兼容性设计**：
   - TTS引擎支持多种后端（Edge TTS、阿里云、腾讯云）
   - 建议增加 `provider` 字段区分不同TTS服务商

3. **优化后的表结构**：

```sql
CREATE TABLE agent_tts (
    id                  BIGINT          NOT NULL    AUTO_INCREMENT,
    agent_id            VARCHAR(64)     NOT NULL,
    provider            VARCHAR(32)     NOT NULL    DEFAULT 'EDGE'    COMMENT 'TTS服务商(EDGE/ALIYUN/TENCENT)',
    voice_name          VARCHAR(64)     NOT NULL    DEFAULT 'zh-CN-XiaoxiaoNeural',
    voice_id            VARCHAR(64)     DEFAULT NULL COMMENT '服务商语音ID',
    language            VARCHAR(16)     NOT NULL    DEFAULT 'zh-CN'   COMMENT '语言代码',
    speech_rate         INT             NOT NULL    DEFAULT 0,
    pitch               INT             NOT NULL    DEFAULT 0,
    volume              INT             NOT NULL    DEFAULT 100,
    output_format       VARCHAR(32)     NOT NULL    DEFAULT 'mp3',
    sample_rate         INT             NOT NULL    DEFAULT 16000,
    audio_encoding      VARCHAR(32)     DEFAULT 'mp3',
    config              JSON            DEFAULT NULL,
    api_key_alias       VARCHAR(64)     DEFAULT NULL COMMENT 'API密钥别名(不存储明文)',
    created_time        DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP,
    updated_time        DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted          TINYINT         NOT NULL    DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_id (agent_id),
    KEY idx_provider (provider)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='TTS语音合成Agent配置表';
```

---

## 3. 前端改造技术方案评审

### 3.1 现有前端架构

```
front/
├── src/
│   ├── views/
│   │   ├── AgentGraph.vue      # Agent调用关系图谱
│   │   ├── AgentList.vue       # Agent列表
│   │   ├── Monitor.vue         # 监控页面
│   │   └── ...
│   └── api/
│       └── index.js            # API调用封装
```

### 3.2 AgentGraph.vue 技术评估

| 评估项 | 现状 | 评分 |
|--------|------|------|
| 图表技术 | ECharts force graph | 8/10 |
| 交互设计 | 支持节点/边点击详情 | 8/10 |
| 状态过滤 | 支持在线/离线筛选 | 7/10 |
| 数据轮询 | 10秒轮询刷新 | 7/10 |
| 响应式 | 支持resize | 8/10 |

### 3.3 前端改造建议

1. **图谱优化**：
   - 当前使用 force layout，建议增加切换到 circular/hierarchical 布局的选项
   - 建议增加节点搜索定位功能
   - 建议增加边权重可视化增强

2. **性能优化**：
   - 当节点数超过50个时，建议使用 WebGL 渲染（ECharts GL）
   - 建议增加虚拟滚动，避免大数据量下的性能问题

3. **新功能建议**：
   - 节点分组：按Agent类型分组展示
   - 实时告警：高亮异常节点
   - 时间旅行：支持查看历史某一时刻的图谱状态

---

## 4. 架构优化建议

### 4.1 数据库层面

1. **分表策略**：
   - `ai_agent_invocation_log` 调用日志表建议按月分表
   - 历史数据归档到冷存储

2. **读写分离**：
   - 主从复制配置，支持读写分离
   - 统计查询走从库

3. **索引优化**：
   - `ai_agent_invocation_log` 建议增加 composite index `(agent_id, created_time)`
   - 心跳表建议增加 `(instance_id, status)` 复合索引

### 4.2 服务层面

1. **Agent注册中心**：
   - 当前 `AgentRegistry` 使用内存存储
   - 建议接入 Redis 实现分布式注册中心
   - 支持多实例部署时的状态同步

2. **消息队列**：
   - A2A消息已使用Redis Stream实现
   - 建议增加消息持久化到数据库的能力
   - 建议增加死信队列处理失败消息

3. **心跳检测**：
   - 当前30秒间隔，90秒超时
   - 建议调整为可配置
   - 建议增加心跳告警通知

### 4.3 前端层面

1. **状态管理**：
   - 建议引入 Pinia 统一管理全局状态
   - 分离API调用状态和UI状态

2. **组件库**：
   - 建议封装通用Agent卡片组件
   - 建议封装统一的图表配置组件

3. **TypeScript迁移**：
   - 建议逐步迁移到TypeScript
   - 增加类型检查减少运行时错误

---

## 5. 新增数据库表设计

### 5.1 TTS Agent 配置表

```sql
-- ===============================================
-- TTS语音合成Agent配置表
-- ===============================================
DROP TABLE IF EXISTS agent_tts;
CREATE TABLE agent_tts (
    id                  BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '主键ID',
    agent_id            VARCHAR(64)     NOT NULL                    COMMENT 'Agent ID',
    provider            VARCHAR(32)     NOT NULL    DEFAULT 'EDGE'   COMMENT 'TTS服务商',
    voice_name          VARCHAR(64)     NOT NULL    DEFAULT 'zh-CN-XiaoxiaoNeural' COMMENT '语音名称',
    voice_id            VARCHAR(64)                             DEFAULT NULL COMMENT '服务商语音ID',
    language            VARCHAR(16)     NOT NULL    DEFAULT 'zh-CN'  COMMENT '语言代码',
    speech_rate         INT             NOT NULL    DEFAULT 0        COMMENT '语速(-500到500)',
    pitch               INT             NOT NULL    DEFAULT 0        COMMENT '音调(-500到500)',
    volume              INT             NOT NULL    DEFAULT 100      COMMENT '音量(0-200)',
    output_format       VARCHAR(32)     NOT NULL    DEFAULT 'mp3'     COMMENT '输出格式',
    sample_rate         INT             NOT NULL    DEFAULT 16000     COMMENT '采样率',
    audio_encoding      VARCHAR(32)                             DEFAULT NULL COMMENT '音频编码',
    config              JSON                                    DEFAULT NULL COMMENT '扩展配置',
    api_key_alias       VARCHAR(64)                             DEFAULT NULL COMMENT 'API密钥别名',
    created_time        DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time        DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted          TINYINT         NOT NULL    DEFAULT 0        COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_id (agent_id),
    KEY idx_provider (provider)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TTS语音合成Agent配置表';

-- ===============================================
-- 初始化TTS Agent数据
-- ===============================================
INSERT INTO ai_agent_info (agent_id, agent_name, agent_type, agent_description, capability, status, instance_id)
VALUES
('tts-agent', 'TTS语音合成Agent', 3, '提供文本转语音合成功能', '["synthesize", "get_voices", "health"]', 0, 'default');

INSERT INTO agent_tts (agent_id, provider, voice_name, language, speech_rate, pitch, volume, output_format, sample_rate)
VALUES
('tts-agent', 'EDGE', 'zh-CN-XiaoxiaoNeural', 'zh-CN', 0, 0, 100, 'mp3', 16000);
```

### 5.2 Agent类型枚举更新

```sql
-- Agent类型枚举更新为:
-- 1 = IMAGE_RECOGNITION (图像识别)
-- 2 = MARKETETING (市场营销)
-- 3 = TTS (语音合成)
-- 4 = ASR (语音识别)
-- 5 = TRANSLATION (翻译)
```

---

## 6. 架构决策记录

| ID | 决策 | 理由 | 状态 |
|----|------|------|------|
| ADR-001 | TTS Agent使用Edge TTS作为默认引擎 | 微软Edge TTS免费、质量好、易于集成 | 待定 |
| ADR-002 | Agent配置使用JSON字段存储 | 支持灵活扩展，避免表结构变更 | 已采纳 |
| ADR-003 | 使用Redis Stream实现A2A消息队列 | 轻量级、支持持久化、消费组 | 已采纳 |
| ADR-004 | 心跳检测间隔30s，超时90s | 平衡实时性与资源消耗 | 已采纳 |

---

## 7. 总结

1. **TTS Agent设计**：方案基本可行，建议按优化后的表结构实施
2. **前端改造**：现有架构合理，建议逐步增强图谱交互能力
3. **架构优化**：重点关注分布式注册中心和消息持久化
4. **后续工作**：
   - 完成TTS Agent的Spring Boot集成
   - 实现TTS与现有A2A协议的兼容
   - 前端增加TTS Agent的状态展示

---

## 8. 参考文档

- [SpringAI 官方文档](https://docs.spring.io/spring-ai/reference/)
- [Edge TTS 官方文档](https://learn.microsoft.com/en-us/azure/cognitive-services/speech-service/language-support/text-to-speech)
- [ECharts Graph 示例](https://echarts.apache.org/examples/zh/graph/)
