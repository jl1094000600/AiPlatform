# AI中台管理系统 v2.0 需求文档

| 版本 | 日期       | 作者 | 备注 |
|------|------------|------|------|
| 2.0  | 2026-04-14 | PM  | 新增SpringAI集成、Agent在线监控、A2A调用 |

---

## 1. v2.0 愿景与目标

### 1.1 版本定位

v2.0 是 AI中台从"管理平台"向"智能协作平台"演进的关键版本。在v1.0统一管理、监控、权限控制基础上，v2.0引入SpringAI作为AI能力底座，构建Agent在线监控体系，支撑Agent间协作调度，使AI中台成为真正的Agent编排与运行中心。

### 1.2 v2.0 核心目标

| 目标 | 描述 |
|------|------|
| **AI能力统一接入** | 通过SpringAI框架接入多种LLM模型，简化AI能力开发，统一调用标准 |
| **Agent状态可视化** | 实现Agent实时在线监控，主动发现Agent故障，缩短MTTR |
| **Agent协作编排** | 支持A2A（Agent to Agent）通信，实现复杂任务的多Agent协作 |

### 1.3 与v1.0的关系

v2.0基于v1.0架构，对以下模块进行增强：

- **扩展**：Agent管理新增SpringAI集成配置、在线状态字段
- **增强**：监控模块新增Agent心跳检测、在线状态看板
- **新增**：A2A调用模块（全新功能）

---

## 2. SpringAI 集成

### 2.1 背景与价值

v1.0中Agent对接AI模型依赖各业务方自行实现，调用方式不统一，难以管理。v2.0引入SpringAI框架作为AI能力底座，提供：

- 多模型统一抽象（OpenAI、Anthropic、通义千问等）
- 标准化Prompt模板管理
- 内置对话、生成、函数调用等能力
- 统一的Token计量与成本统计

### 2.2 功能需求

#### 2.2.1 SpringAI配置管理

- 支持在管理后台配置SpringAI连接参数：
  - 模型厂商（OpenAI / Anthropic / 阿里云 /ollama等）
  - API Key / 密钥（加密存储）
  - Endpoint地址
  - 模型名称（如 gpt-4o、claude-3-opus、qwen-plus）
  - 连接超时、请求超时配置
- 支持配置多个模型实例，支持模型间切换
- 支持为不同业务模块分配默认模型

#### 2.2.2 Agent与SpringAI绑定

- Agent注册时可选择绑定SpringAI模型
- 支持为Agent配置专属Prompt模板（系统提示词、few-shot示例）
- 支持为Agent配置模型参数（temperature、max_tokens、top_p等）
- 支持配置Agent的能力类型：对话型 / 生成型 / 函数调用型

#### 2.2.3 SpringAI调用代理

- 通过中台统一代理SpringAI调用，业务模块无需直接对接模型厂商
- 支持调用链追踪（记录每次调用的请求、响应、Token消耗）
- 支持在调用层注入业务逻辑（参数校验、结果过滤、日志记录）
- 支持流式响应（Streaming），适用于对话场景

#### 2.2.4 Prompt模板市场

- 内置常用Prompt模板库（摘要生成、翻译、代码审查等）
- 支持管理员上传、编辑、删除模板
- 支持为模板设置分类标签
- 支持预览模板效果（测试窗口）

### 2.3 数据模型

#### 2.3.1 ai_springai_model（SpringAI模型配置表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| model_code | VARCHAR(50) | 模型编码（唯一） |
| model_name | VARCHAR(100) | 模型名称（如 GPT-4o） |
| provider | VARCHAR(50) | 厂商（openai/anthropic/aliyun/ollama） |
| endpoint | VARCHAR(500) | API地址 |
| api_key_encrypted | VARCHAR(500) | 加密后的API Key |
| default_params | JSON | 默认参数（temperature、max_tokens等） |
| status | TINYINT | 状态（0禁用 1启用） |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

#### 2.3.2 ai_prompt_template（Prompt模板表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| template_code | VARCHAR(50) | 模板编码 |
| template_name | VARCHAR(100) | 模板名称 |
| category | VARCHAR(50) | 分类 |
| system_prompt | TEXT | 系统提示词 |
| user_prompt_template | TEXT | 用户模板（支持占位符） |
| description | TEXT | 说明 |
| status | TINYINT | 状态 |
| create_time | DATETIME | 创建时间 |

### 2.4 API接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/springai/models | 添加SpringAI模型配置 |
| GET | /api/v1/springai/models | 查询模型列表 |
| PUT | /api/v1/springai/models/{id} | 更新模型配置 |
| DELETE | /api/v1/springai/models/{id} | 删除模型配置 |
| POST | /api/v1/springai/models/{id}/test | 测试模型连接 |
| POST | /api/v1/springai/prompts | 创建Prompt模板 |
| GET | /api/v1/springai/prompts | 查询模板列表 |
| POST | /api/v1/springai/prompts/{id}/preview | 预览模板效果 |

---

## 3. Agent 在线状态监控

### 3.1 背景与价值

v1.0的监控侧重于调用层面的metrics统计，但无法感知Agent本身的在线状态。当Agent服务宕机或网络异常时，业务模块仍会持续投递请求，导致大量失败调用。

v2.0新增主动心跳检测机制，实现：

- **主动发现**：Agent离线时快速感知，秒级通知
- **告警收敛**：避免大量失败调用产生的告警噪音
- **状态自愈**：结合健康检查，支持自动重连

### 3.2 功能需求

#### 3.2.1 Agent心跳注册

- Agent启动时自动向中台发送心跳注册请求
- 心跳请求包含：Agent ID、实例ID、当前负载、版本号
- 支持Agent主动上报额外信息（GPU内存、队列长度等）
- Agent需定期发送心跳（默认30秒），超时未收到视为离线

#### 3.2.2 心跳协议

```
POST /api/v1/agent-heartbeat/register
{
  "agentId": "agent-001",
  "instanceId": "ins-001",
  "version": "1.2.0",
  "load": 0.45,
  "capabilities": ["text-generation", "image-understanding"],
  "metadata": {}
}

POST /api/v1/agent-heartbeat/beat
{
  "agentId": "agent-001",
  "instanceId": "ins-001",
  "load": 0.55,
  "queueSize": 10,
  "timestamp": 1713000000000
}
```

#### 3.2.3 在线状态看板

- **Agent状态大屏**：展示所有Agent在线/离线/告警状态
- **状态指标**：
  - 在线数 / 离线数 / 总数
  - Agent实例数（一个Agent可部署多个实例）
  - 平均负载、峰值负载
  - 最近一次心跳时间
- **状态筛选**：支持按Agent名称、分类、状态筛选
- **状态更新**：实时推送（WebSocket），延迟<1秒

#### 3.2.4 离线告警

- Agent连续3次心跳超时（默认90秒无响应）自动标记为离线
- 支持配置告警规则：
  - 离线后立即发送通知（邮件/钉钉/企微）
  - 恢复上线后发送恢复通知
- 支持告警静默期配置（避免维护窗口期干扰）
- 告警记录支持查询和导出

#### 3.2.5 健康检查配置

- 支持为Agent配置健康检查URL（可选）
- 健康检查项：接口连通性、响应时间阈值、业务指标
- 支持手动触发健康检查
- 健康检查结果记录历史

### 3.3 数据模型

#### 3.3.1 ai_agent_instance（Agent实例表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| agent_id | BIGINT | Agent ID |
| instance_id | VARCHAR(100) | 实例唯一标识 |
| version | VARCHAR(20) | 实例版本 |
| status | TINYINT | 状态（0离线 1在线 2告警） |
| load | DECIMAL(5,2) | 当前负载 |
| queue_size | INT | 队列长度 |
| last_heartbeat_time | DATETIME | 最近心跳时间 |
| register_time | DATETIME | 注册时间 |
| create_time | DATETIME | 创建时间 |

#### 3.3.2 ai_heartbeat_log（心跳日志表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| instance_id | VARCHAR(100) | 实例ID |
| agent_id | BIGINT | Agent ID |
| heartbeat_type | VARCHAR(20) | 类型（register/beat/unregister） |
| load | DECIMAL(5,2) | 当时负载 |
| create_time | DATETIME | 时间 |

#### 3.3.3 ai_alert_record（告警记录表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| alert_type | VARCHAR(50) | 告警类型（agent_offline/load_high） |
| agent_id | BIGINT | Agent ID |
| instance_id | VARCHAR(100) | 实例ID |
| alert_content | TEXT | 告警内容 |
| alert_level | VARCHAR(20) | 告警级别 |
| status | VARCHAR(20) | 状态（triggered/resolved） |
| trigger_time | DATETIME | 触发时间 |
| resolve_time | DATETIME | 解决时间 |

### 3.4 API接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/agent-heartbeat/register | Agent注册 |
| POST | /api/v1/agent-heartbeat/beat | Agent心跳 |
| POST | /api/v1/agent-heartbeat/unregister | Agent注销 |
| GET | /api/v1/monitor/agent-status | Agent在线状态列表 |
| GET | /api/v1/monitor/agent-status/{instanceId} | 实例状态详情 |
| GET | /api/v1/monitor/agent-alerts | 告警记录查询 |
| PUT | /api/v1/monitor/agent-alerts/{id}/resolve | 标记告警已解决 |
| POST | /api/v1/monitor/health-check/{agentId} | 触发健康检查 |

---

## 4. A2A 调用（Agent to Agent）

### 4.1 背景与价值

企业复杂业务场景中，单一Agent往往难以独立完成全部任务。例如：一个"会议助手"Agent可能需要调用"日历Agent"确认时间，再调用"邮件Agent"发送邀请。

v2.0引入A2A（Agent to Agent）通信协议，实现：

- **任务分解**：复杂任务拆解为多个子任务，分配给专业Agent
- **信息传递**：子任务结果在Agent间流转，形成完整工作流
- **协作编排**：可视化编排Agent调用顺序、条件分支、并行执行

### 4.2 功能需求

#### 4.2.1 A2A协议支持

- Agent可通过中台向其他Agent发起调用请求
- A2A请求包含：目标Agent、任务描述、上下文信息、期望响应格式
- 支持同步调用（等待结果）和异步调用（任务队列）
- 支持调用超时配置和重试策略

```
POST /api/v1/a2a/call
{
  "sourceAgentId": "agent-meeting",
  "targetAgentId": "agent-calendar",
  "taskType": "query_available_time",
  "taskDescription": "查询明天下午2-4点是否有空",
  "context": {
    "date": "2026-04-15",
    "startHour": 14,
    "endHour": 16
  },
  "responseFormat": "json",
  "timeout": 30000,
  "async": false
}
```

#### 4.2.2 Agent协作编排

- **编排工作流**：支持在管理后配置Agent调用流程
- **节点类型**：
  - Agent节点：指定调用的Agent
  - 条件节点：if/else条件分支
  - 并行节点：多个Agent同时执行
  - 聚合节点：合并多个Agent结果
  - LLM路由节点：基于LLM判断下一步
- **执行引擎**：
  - 支持同步执行（顺序流水线）
  - 支持异步执行（任务队列）
  - 支持执行状态查询
  - 支持执行日志记录

#### 4.2.3 工作流管理

- 支持创建、编辑、删除工作流
- 工作流信息：名称、描述、节点配置、变量定义
- 支持工作流版本管理
- 支持工作流草稿/发布状态
- 支持手动触发和API触发

#### 4.2.4 任务管理与追踪

- 支持查看A2A任务列表（进行中/已完成/失败）
- 支持查看任务详情（执行链路、每个节点输入输出）
- 支持取消进行中的任务
- 支持任务重试
- 任务记录保留90天

#### 4.2.5 A2A安全控制

- A2A调用需校验权限：调用方Agent必须有目标Agent的调用授权
- 支持为A2A调用配置QPS限制，防止循环调用
- 支持配置Agent间的信任关系，白名单/黑名单
- 记录所有A2A调用日志，便于审计

### 4.3 数据模型

#### 4.3.1 ai_a2a_task（A2A任务表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| task_id | VARCHAR(50) | 任务唯一ID |
| workflow_id | BIGINT | 工作流ID（手动触发时关联） |
| source_agent_id | BIGINT | 源Agent ID |
| target_agent_id | BIGINT | 目标Agent ID |
| task_type | VARCHAR(50) | 任务类型 |
| task_description | TEXT | 任务描述 |
| context | JSON | 上下文信息 |
| status | VARCHAR(20) | 状态（pending/running/success/failed） |
| result | JSON | 执行结果 |
| error_message | TEXT | 错误信息 |
| start_time | DATETIME | 开始时间 |
| end_time | DATETIME | 结束时间 |
| create_time | DATETIME | 创建时间 |

#### 4.3.2 ai_workflow（工作流表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| workflow_code | VARCHAR(50) | 工作流编码（唯一） |
| workflow_name | VARCHAR(100) | 工作流名称 |
| description | TEXT | 描述 |
| workflow_config | JSON | 节点配置 |
| variables | JSON | 变量定义 |
| status | TINYINT | 状态（0草稿 1已发布） |
| version | INT | 版本号 |
| create_time | DATETIME | 创建时间 |
| publish_time | DATETIME | 发布时间 |

#### 4.3.3 ai_workflow_execution（工作流执行记录表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| execution_id | VARCHAR(50) | 执行ID |
| workflow_id | BIGINT | 工作流ID |
| trigger_type | VARCHAR(20) | 触发类型（manual/api/schedule） |
| status | VARCHAR(20) | 状态 |
| node_executions | JSON | 各节点执行结果 |
| start_time | DATETIME | 开始时间 |
| end_time | DATETIME | 结束时间 |
| create_time | DATETIME | 创建时间 |

#### 4.3.4 ai_a2a_auth（Agent调用授权表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| caller_agent_id | BIGINT | 调用方Agent ID |
| callee_agent_id | BIGINT | 被调用方Agent ID |
| auth_type | VARCHAR(20) | 授权类型（allow/deny） |
| qps_limit | INT | QPS限制 |
| timeout | INT | 超时时间（毫秒） |
| status | TINYINT | 状态 |
| create_time | DATETIME | 创建时间 |

### 4.4 API接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/a2a/call | 发起A2A调用 |
| GET | /api/v1/a2a/tasks | 查询任务列表 |
| GET | /api/v1/a2a/tasks/{taskId} | 查询任务详情 |
| POST | /api/v1/a2a/tasks/{taskId}/cancel | 取消任务 |
| POST | /api/v1/a2a/tasks/{taskId}/retry | 重试任务 |
| POST | /api/v1/a2a/workflows | 创建工作流 |
| GET | /api/v1/a2a/workflows | 查询工作流列表 |
| GET | /api/v1/a2a/workflows/{id} | 查询工作流详情 |
| PUT | /api/v1/a2a/workflows/{id} | 更新工作流 |
| DELETE | /api/v1/a2a/workflows/{id} | 删除工作流 |
| POST | /api/v1/a2a/workflows/{id}/publish | 发布工作流 |
| POST | /api/v1/a2a/workflows/{id}/execute | 执行工作流 |
| GET | /api/v1/a2a/workflows/{id}/executions | 查询执行记录 |
| POST | /api/v1/a2a/auth | 创建调用授权 |
| GET | /api/v1/a2a/auth | 查询授权列表 |
| DELETE | /api/v1/a2a/auth/{id} | 删除授权 |

### 4.5 A2A业务流程

#### 4.5.1 同步A2A调用流程

```
1. Agent-A 发起A2A调用请求到中台
2. 中台校验：
   a. 权限校验（Agent-A是否有权限调用Agent-B）
   b. 参数校验（请求格式是否符合要求）
   c. 限流校验（是否超过QPS限制）
3. 权限/参数校验失败 → 返回错误，任务结束
4. 权限/参数校验通过：
   a. 创建A2A任务记录（status=pending）
   b. 将任务状态更新为running
   c. 向Agent-B投递调用请求
5. Agent-B 执行任务，返回结果
6. 中台更新任务记录：
   a. 成功 → status=success，result=Agent-B返回
   b. 失败 → status=failed，error_message=错误信息
7. 中台返回结果给Agent-A
```

#### 4.5.2 工作流执行流程

```
1. 用户或API触发工作流执行
2. 创建工作流执行记录（status=running）
3. 按节点配置顺序执行：
   a. 解析节点类型
   b. 根据节点类型执行对应逻辑
   c. 记录节点执行结果到node_executions
   d. 条件判断，决定下一步节点
4. 工作流执行完成（status=success/failed）
5. 返回执行结果给调用方
```

---

## 5. 其他 v2.0 增强需求

### 5.1 v1.0功能增强

| 模块 | 增强点 |
|------|--------|
| Agent管理 | 新增SpringAI配置绑定字段、新增实例管理 |
| 监控 | 新增Agent在线状态Tab、新增心跳配置 |
| 模型管理 | SpringAI模型与原有模型分开管理 |

### 5.2 技术架构调整

| 组件 | 调整 |
|------|------|
| 新增 SpringAI | AI能力接入层，支持多模型统一调用 |
| 新增 A2A Engine | Agent协作编排引擎 |
| 新增 Agent Registry | Agent注册与心跳管理中心 |
| WebSocket | 支持Agent状态实时推送 |

---

## 6. 里程碑规划

| 阶段 | 目标 | 交付物 |
|------|------|--------|
| M1 | SpringAI基础集成 | SpringAI模型配置、Agent绑定、基础调用 |
| M2 | Agent在线监控 | 心跳注册、状态看板、离线告警 |
| M3 | A2A调用 | A2A调用、工作流编排、任务管理 |
| M4 | 增强优化 | 性能优化、安全加固、UT覆盖 |

---

## 7. 风险与依赖

| 风险/依赖 | 影响 | 应对措施 |
|----------|------|----------|
| SpringAI版本迭代 | 框架API可能变化 | 封装抽象层，解耦业务与框架 |
| Agent心跳对性能的影响 | 高频心跳可能影响系统性能 | 心跳批量处理，非实时写入 |
| A2A循环调用风险 | Agent间可能形成调用环 | QPS限制 + 调用链路深度限制 |
| 多模型兼容性 | 不同模型响应格式差异 | 统一抽象Response结构 |
