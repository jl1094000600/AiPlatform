# AI Platform v3.0 产品需求文档 (PRD)

**版本**: v3.0
**日期**: 2026-04-17
**状态**: 初稿

---

## 1. 产品概述

### 1.1 产品愿景
AI Platform v3.0 在 v2.0 的 Agent 管理和 A2A 通信协议基础上，新增两个垂直领域的专业 Agent：图像识别 Agent 和市场营销 Agent。通过标准化的 A2A 协议和平台统一管理，实现 Agent 能力的无缝集成与协作。

### 1.2 新增 Agent 列表

| Agent 名称 | Agent 类型 | 核心能力 |
|------------|------------|----------|
| 图像识别 Agent | 视觉理解型 | 多格式图像识别、文档内容提取 |
| 市场营销 Agent | 数据分析型 | 销售查询、趋势分析、统计排名 |

---

## 2. 图像识别 Agent (ImageRecognitionAgent)

### 2.1 Agent 基本信息

| 属性 | 值 |
|------|-----|
| Agent ID | `image-recognition-agent` |
| Agent 名称 | 图像识别 Agent |
| Agent 类型 | 视觉理解型 (Visual Understanding) |
| 版本 | 1.0.0 |
| 能力描述 | 支持多格式图像识别、PDF/文档内容提取，为平台提供视觉信息理解能力 |
| 所属领域 | Computer Vision / Document Understanding |

### 2.2 技术规格

#### 2.2.1 支持的图像格式

| 格式 | MIME Type | 最大尺寸 | 备注 |
|------|-----------|----------|------|
| JPEG | `image/jpeg` | 10MB | 照片、截图 |
| PNG | `image/png` | 10MB | 带透明通道 |
| GIF | `image/gif` | 5MB | 动态图 |
| BMP | `image/bmp` | 10MB | 位图 |
| WebP | `image/webp` | 10MB | 新型压缩格式 |

#### 2.2.2 非图像文件处理

对于非图像文件，Agent 采用代码解析方式提取内容：

| 文件类型 | 处理方式 | 输出格式 |
|----------|----------|----------|
| PDF | Apache PDFBox 文本提取 | 纯文本 / 结构化文本 |
| Word (.docx) | Apache POI 解析 | 纯文本 + 关键信息 |
| Excel (.xlsx) | Apache POI 解析 | 结构化表格数据 |
| TXT | 直接读取 | 纯文本 |
| Markdown | 正则解析 | 纯文本 |

### 2.3 核心功能

#### 2.3.1 图像识别能力

**输入**:
```json
{
  "taskType": "image_recognition",
  "imageData": "base64编码的图像数据",
  "imageUrl": "图像URL（二选一）",
  "options": {
    "language": "zh-CN",
    "detailLevel": "high",
    "enableOCR": true
  }
}
```

**输出**:
```json
{
  "success": true,
  "taskId": "uuid",
  "result": {
    "imageType": "jpeg",
    "size": {"width": 1920, "height": 1080},
    "description": "图像描述内容",
    "objects": [
      {"label": "对象标签", "confidence": 0.95, "bbox": [x, y, w, h]}
    ],
    "text": "OCR识别文字",
    "colors": ["主色调列表"]
  },
  "timestamp": "2026-04-17T10:00:00Z"
}
```

#### 2.3.2 文档内容提取

**输入**:
```json
{
  "taskType": "document_extraction",
  "fileData": "base64编码的文件数据",
  "fileName": "document.pdf",
  "options": {
    "extractImages": false,
    "pageRange": "1-10"
  }
}
```

**输出**:
```json
{
  "success": true,
  "taskId": "uuid",
  "result": {
    "fileType": "pdf",
    "pageCount": 10,
    "text": "提取的文本内容",
    "metadata": {
      "title": "文档标题",
      "author": "作者",
      "createdDate": "创建日期"
    },
    "tables": [
      {"page": 1, "data": [["列1", "列2"], ["值1", "值2"]]}
    ]
  },
  "timestamp": "2026-04-17T10:00:00Z"
}
```

### 2.4 与平台集成

#### 2.4.1 Agent 注册要求

```yaml
agent:
  id: image-recognition-agent
  name: 图像识别 Agent
  type: VISUAL_UNDERSTANDING
  capabilities:
    - image_recognition
    - document_extraction
    - ocr
  endpoint: http://localhost:8081/agent/image-recognition
  maxConcurrentTasks: 5
  timeout: 30000  # 30秒超时
```

#### 2.4.2 心跳监控要求

| 指标 | 要求 |
|------|------|
| 心跳间隔 | 30秒（与平台一致） |
| 超时阈值 | 90秒 |
| 实例 ID | 支持多实例部署 |
| 状态上报 | STARTING, RUNNING, PROCESSING, IDLE, ERROR |

#### 2.4.3 A2A 通信规范

**消息格式**:
```json
{
  "messageId": "uuid",
  "sender": "image-recognition-agent",
  "receiver": "target-agent-id",
  "messageType": "TASK_REQUEST",
  "content": {
    "action": "recognize",
    "payload": {...}
  },
  "timestamp": "2026-04-17T10:00:00Z"
}
```

**支持的消息类型**:
- `TASK_REQUEST`: 任务请求
- `TASK_RESPONSE`: 任务响应
- `STATUS_UPDATE`: 状态更新
- `HEARTBEAT`: 心跳

---

## 3. 市场营销 Agent (MarketingAgent)

### 3.1 Agent 基本信息

| 属性 | 值 |
|------|-----|
| Agent ID | `marketing-agent` |
| Agent 名称 | 市场营销 Agent |
| Agent 类型 | 数据分析型 (Data Analytics) |
| 版本 | 1.0.0 |
| 能力描述 | 提供销售数据分析、趋势对比、统计排名等营销场景工具 |
| 所属领域 | Business Intelligence / Data Analytics |

### 3.2 三个场景工具

#### 3.2.1 销售数据查询 (SalesDataQuery)

**功能描述**: 支持按时间、区域、产品等多个维度查询销售数据。

**输入参数**:

| 参数名 | 类型 | 必填 | 说明 | 示例 |
|--------|------|------|------|------|
| startDate | String | 是 | 开始日期 | `2026-01-01` |
| endDate | String | 是 | 结束日期 | `2026-03-31` |
| region | String | 否 | 区域（为空则查全部） | `华东`, `华北` |
| productCategory | String | 否 | 产品类别 | `电子产品` |
| aggregation | String | 否 | 聚合方式: day/week/month | `month` |

**输出格式**:
```json
{
  "success": true,
  "toolName": "sales_data_query",
  "result": {
    "queryParams": {
      "startDate": "2026-01-01",
      "endDate": "2026-03-31",
      "region": "华东",
      "productCategory": "电子产品",
      "aggregation": "month"
    },
    "data": [
      {"date": "2026-01", "region": "华东", "category": "电子产品", "salesAmount": 150000, "orderCount": 320},
      {"date": "2026-02", "region": "华东", "category": "电子产品", "salesAmount": 180000, "orderCount": 385}
    ],
    "totalSalesAmount": 330000,
    "totalOrderCount": 705
  },
  "timestamp": "2026-04-17T10:00:00Z"
}
```

#### 3.2.2 同比环比趋势分析 (TrendAnalysis)

**功能描述**: 支持多维度对比分析，计算同比（YoY）和环比（MoM）增长率。

**输入参数**:

| 参数名 | 类型 | 必填 | 说明 | 示例 |
|--------|------|------|------|------|
| currentPeriod | Object | 是 | 本期数据 | `{"start": "2026-01", "end": "2026-03"}` |
| previousPeriod | Object | 是 | 上期数据（环比）或去年同期（同比） | `{"start": "2025-01", "end": "2025-03"}` |
| compareType | String | 是 | 对比类型: `yoy` / `mom` / `both` | `both` |
| metrics | String[] | 否 | 分析指标列表 | `["salesAmount", "orderCount"]` |
| groupBy | String | 否 | 分组维度: region/category/product | `region` |

**输出格式**:
```json
{
  "success": true,
  "toolName": "trend_analysis",
  "result": {
    "compareType": "both",
    "currentPeriod": {"start": "2026-01", "end": "2026-03", "salesAmount": 500000},
    "previousPeriod": {"start": "2025-01", "end": "2025-03", "salesAmount": 420000},
    "growth": {
      "salesAmount": {
        "absoluteChange": 80000,
        "percentageChange": 19.05,
        "trend": "up"
      }
    },
    "groupedData": [
      {"region": "华东", "currentSales": 300000, "previousSales": 250000, "growth": 20.0},
      {"region": "华北", "currentSales": 200000, "previousSales": 170000, "growth": 17.65}
    ],
    "insights": [
      "华东地区增长最快，达到20%",
      "整体呈上升趋势"
    ]
  },
  "timestamp": "2026-04-17T10:00:00Z"
}
```

#### 3.2.3 统计汇总与排名 (StatisticsRanking)

**功能描述**: 支持数据汇总、求和、平均值计算，以及多维度排名。

**输入参数**:

| 参数名 | 类型 | 必填 | 说明 | 示例 |
|--------|------|------|------|------|
| dataSource | String | 是 | 数据源: sales/inventory/customer | `sales` |
| dimension | String | 是 | 排名维度: region/product/salesperson | `product` |
| startDate | String | 否 | 开始日期 | `2026-01-01` |
| endDate | String | 否 | 结束日期 | `2026-03-31` |
| rankingType | String | 否 | 排名类型: total/ranking/growth | `ranking` |
| topN | Integer | 否 | 返回前N条 | `10` |
| metrics | String[] | 否 | 计算指标 | `["salesAmount", "profit"]` |

**输出格式**:
```json
{
  "success": true,
  "toolName": "statistics_ranking",
  "result": {
    "dataSource": "sales",
    "dimension": "product",
    "rankingType": "ranking",
    "summary": {
      "totalSalesAmount": 1500000,
      "totalOrderCount": 3500,
      "averageOrderValue": 428.57,
      "productCount": 45
    },
    "ranking": [
      {"rank": 1, "product": "iPhone 15", "salesAmount": 150000, "orderCount": 300, "growthRate": 25.3},
      {"rank": 2, "product": "MacBook Pro", "salesAmount": 120000, "orderCount": 150, "growthRate": 18.7},
      {"rank": 3, "product": "AirPods Pro", "salesAmount": 95000, "orderCount": 475, "growthRate": 12.5}
    ],
    "chartData": {
      "labels": ["iPhone 15", "MacBook Pro", "AirPods Pro"],
      "values": [150000, 120000, 95000]
    }
  },
  "timestamp": "2026-04-17T10:00:00Z"
}
```

### 3.3 工具调用规范

#### 3.3.1 统一输入格式

所有工具调用通过 A2A 消息的 `content` 字段传递：

```json
{
  "messageId": "uuid",
  "sender": "calling-agent",
  "receiver": "marketing-agent",
  "messageType": "TASK_REQUEST",
  "content": {
    "tool": "sales_data_query",
    "parameters": {
      "startDate": "2026-01-01",
      "endDate": "2026-03-31",
      "region": "华东"
    }
  }
}
```

#### 3.3.2 统一输出格式

```json
{
  "success": true,
  "toolName": "<工具名称>",
  "result": { ... },
  "errors": [],
  "timestamp": "2026-04-17T10:00:00Z"
}
```

**错误响应**:
```json
{
  "success": false,
  "toolName": "<工具名称>",
  "result": null,
  "errors": [
    {"code": "INVALID_DATE_RANGE", "message": "开始日期不能晚于结束日期"}
  ],
  "timestamp": "2026-04-17T10:00:00Z"
}
```

### 3.4 场景化使用说明

#### 3.4.1 典型场景 1: 月度销售复盘

**场景**: 运营团队每月进行销售复盘，需要了解当月各区域销售情况。

**调用流程**:
1. 使用 `sales_data_query` 查询本月销售数据（按区域聚合）
2. 使用 `trend_analysis` 对比上月数据（环比分析）
3. 使用 `statistics_ranking` 查看各区域排名

**示例对话**:
```
用户: 分析一下华东区3月份的销售情况
Agent: 调用 sales_data_query(region="华东", startDate="2026-03-01", endDate="2026-03-31", aggregation="day")
      返回华东区3月份每日销售数据
```

#### 3.4.2 典型场景 2: 季度业绩对比

**场景**: 管理层需要季度业绩报告，对比本季度与去年同期的增长情况。

**调用流程**:
1. 使用 `trend_analysis` 进行同比分析（Q1 2026 vs Q1 2025）
2. 使用 `statistics_ranking` 查看产品类别的业绩排名
3. 综合分析生成报告

**示例对话**:
```
用户: 今年Q1和去年Q1相比业绩如何？
Agent: 调用 trend_analysis(compareType="yoy", currentPeriod={Q1 2026}, previousPeriod={Q1 2025})
      返回同比增长率和分析洞察
```

#### 3.4.3 典型场景 3: 产品销售排行榜

**场景**: 产品团队需要了解哪些产品卖得最好，以便制定促销计划。

**调用流程**:
1. 使用 `statistics_ranking` 按产品维度排名（rankingType="ranking", topN=10）
2. 使用 `sales_data_query` 单独查看 TOP 产品的月度趋势

**示例对话**:
```
用户: 给我看看卖得最好的10款产品
Agent: 调用 statistics_ranking(dimension="product", rankingType="ranking", topN=10)
      返回产品销售排行榜
```

### 3.5 与平台集成

#### 3.5.1 Agent 注册配置

```yaml
agent:
  id: marketing-agent
  name: 市场营销 Agent
  type: DATA_ANALYTICS
  capabilities:
    - sales_data_query
    - trend_analysis
    - statistics_ranking
  endpoint: http://localhost:8082/agent/marketing
  maxConcurrentTasks: 10
  timeout: 60000  # 60秒超时（数据分析可能较慢）
```

#### 3.5.2 心跳监控要求

与图像识别 Agent 一致：30秒间隔，90秒超时。

#### 3.5.3 A2A 通信支持

支持与平台内任意 Agent 的 A2A 通信，例如：
- 向图像识别 Agent 请求产品图片分析
- 向报告生成 Agent 发送分析结果

---

## 4. 平台集成要求

### 4.1 通用注册流程

所有 Agent 需完成以下注册步骤：

1. **启动时注册**: 向 `POST /api/agents/register` 发送 Agent 信息
2. **心跳维持**: 每 30 秒向 `POST /api/agents/heartbeat` 发送心跳
3. **能力声明**: 在注册时声明 `capabilities` 列表
4. **消息监听**: 监听 A2A 消息队列 (`redis-stream: agent-messages`)

### 4.2 数据库表结构

新增 `ai_agent_info` 表记录 Agent 基础信息：

```sql
CREATE TABLE ai_agent_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id VARCHAR(64) NOT NULL UNIQUE,
    agent_name VARCHAR(128) NOT NULL,
    agent_type VARCHAR(32) NOT NULL,
    endpoint VARCHAR(256),
    capabilities JSON,
    status VARCHAR(16) DEFAULT 'OFFLINE',
    instance_id VARCHAR(64),
    version VARCHAR(32),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_agent_id (agent_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 4.3 API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/agents/register` | POST | Agent 注册 |
| `/api/agents/heartbeat` | POST | 心跳上报 |
| `/api/agents/{agentId}/status` | GET | 查询 Agent 状态 |
| `/api/agents/capabilities` | GET | 查询平台能力列表 |
| `/api/a2a/send` | POST | 发送 A2A 消息 |
| `/api/a2a/messages/{agentId}` | GET | 获取 Agent 消息 |

---

## 5. 验收标准

### 5.1 图像识别 Agent

- [ ] 支持 JPEG、PNG、GIF、BMP、WebP 格式图像识别
- [ ] 支持 PDF、Word、Excel、TXT 文件内容提取
- [ ] 识别结果包含图像描述、对象检测、OCR 文字
- [ ] 正确注册到平台并发送心跳
- [ ] 响应 A2A 消息并返回标准格式结果

### 5.2 市场营销 Agent

- [ ] `sales_data_query` 支持按时间、区域、产品多维度查询
- [ ] `trend_analysis` 支持同比（YoY）和环比（MoM）计算
- [ ] `statistics_ranking` 支持 TOP N 排名和数据汇总
- [ ] 所有工具输出格式符合统一规范
- [ ] 正确注册到平台并发送心跳

### 5.3 平台集成

- [ ] 两个 Agent 能在平台 Web UI 中显示状态
- [ ] 能通过平台界面向 Agent 发送任务
- [ ] 心跳检测正常工作（Agent 离线时平台能检测到）

---

## 6. 里程碑计划

| 阶段 | 内容 | 目标日期 |
|------|------|----------|
| M1 | 完成 PRD 评审 | 2026-04-18 |
| M2 | 后端 API 开发 | 2026-04-25 |
| M3 | Agent 核心逻辑实现 | 2026-05-02 |
| M4 | 平台集成与联调 | 2026-05-09 |
| M5 | 测试与 Bug 修复 | 2026-05-16 |
| M6 | v3.0 发布 | 2026-05-20 |

---

**文档作者**: 产品经理
**评审人**: 待定
**版本历史**:
- v1.0 (2026-04-17): 初稿创建
