# AI中台管理系统需求文档

| 版本 | 日期       | 作者 | 备注 |
|------|------------|------|------|
| 1.0  | 2026-04-13 | PM   | 初稿 |

---

## 1. 项目概述与背景

### 1.1 项目背景

随着AI技术的快速发展，企业内部已逐步沉淀出多个AI Agent业务能力。然而，这些Agent目前分散在各个业务模块中，缺乏统一管理，导致：

- Agent重复开发，资源浪费
- Agent接口标准不统一，调用方式各异
- Agent运行状态、调用链路不透明
- 难以进行统一的权限控制和资源调度

为解决上述问题，计划建设一套AI中台管理系统，实现对Agent的统一管理、监控与复用。

### 1.2 项目目标

- **统一管理**: 对企业内部所有AI Agent进行注册、发布、下线等生命周期管理
- **能力复用**: Agent可被多个业务模块复用，降低开发成本
- **可观测性**: 提供Agent调用监控、模型使用统计等能力
- **高可用**: 系统具备高鲁棒性、低耦合性、可扩展性

### 1.3 术语说明

| 术语 | 说明 |
|------|------|
| Agent | AI能力封装单元，提供特定业务功能（如文本生成、图像识别等） |
| 模型 | Agent调用的底层LLM模型（如GPT-4、Claude、通义千问等） |
| Token | 模型推理时的计量单位 |
| 业务模块 | 调用Agent的上层业务系统 |

---

## 2. 功能需求

### 2.1 Agent管理

#### 2.1.1 Agent注册

- 支持新增Agent注册，需填写以下信息：
  - Agent名称（必填，唯一）
  - Agent描述（必填）
  - 所属分类（必填，如：文本处理、图像识别、问答系统等）
  - 接口地址（必填，URL格式）
  - 调用方式（GET/POST）
  - 请求参数模板（JSON Schema）
  - 响应格式说明
  - 负责人
- 支持上传Agent对应的API文档（Markdown或OpenAPI JSON）
- 注册后Agent状态为**草稿**，需经审核后发布

#### 2.1.2 Agent查询

- 支持按Agent名称、分类、状态、负责人等条件组合查询
- 支持分页展示（默认每页20条）
- 支持列表排序（按创建时间、名称、调用量等）
- 支持查看Agent详情，包含基本信息、配置、统计指标

#### 2.1.3 Agent编辑

- 支持修改Agent基本信息（名称、描述、分类、接口地址等）
- 支持修改调用配置（请求模板、参数说明等）
- 修改记录需保留操作日志

#### 2.1.4 Agent删除

- 仅允许删除**已下线**状态的Agent
- 删除前需确认，删除后Agent数据保留90天（软删除）

#### 2.1.5 Agent发布与下线

- **发布**: Agent从草稿/下线状态切换为上线状态，可被业务模块调用
- **下线**: Agent从上线状态切换为下线状态，下线后不可被调用，但仍保留监控数据
- 上线前需完成配置校验（接口连通性、参数模板验证）

#### 2.1.6 Agent版本管理

- 支持同一Agent维护多个版本（主版本号.次版本号）
- 每次发布自动分配版本号
- 支持查看历史版本列表及版本详情
- 业务模块可绑定指定版本进行调用

### 2.2 接口调用监控

#### 2.2.1 实时监控

- 展示当前在线Agent的实时调用状态
- 关键指标：在线/离线、当前QPS、平均响应时间
- 支持设置告警阈值（响应时间超限、成功率低于阈值）

#### 2.2.2 调用统计

- **调用次数**: 按分钟/小时/天维度统计各Agent的调用量
- **响应时间**: 展示平均响应时间、P95响应时间、最大响应时间
- **成功率**: 统计成功调用次数、失败次数、成功率百分比
- 支持时间范围选择（自定义时间区间）
- 支持导出统计报表（Excel格式）

#### 2.2.3 调用链路追踪

- 支持查询单次调用的完整链路（请求时间、耗时、状态、响应内容）
- 支持按traceId进行全链路检索
- 保留最近30天的调用明细数据

#### 2.2.4 调用日志

- 记录每次API调用的详细日志
- 日志字段：时间戳、Agent ID、业务模块ID、请求参数、响应结果、耗时、状态码
- 支持按关键词搜索日志内容
- 日志保留策略：详细日志保留30天，聚合统计保留1年

### 2.3 模型使用统计

#### 2.3.1 模型目录管理

- 维护企业内部所有可用模型列表
- 模型信息：模型名称、模型厂商（如OpenAI、Anthropic、阿里云）、模型版本、API版本、Endpoint地址、计费方式
- 支持模型的增删改查

#### 2.3.2 使用量统计

- 按模型维度统计使用情况：
  - 使用次数
  - Token消耗量（输入Token、输出Token、总Token）
  - 费用估算
- 支持按业务模块、Agent、时间维度下钻分析
- 统计周期：支持日、周、月、自定义

#### 2.3.3 Token消费预警

- 支持设置Token额度预警
- 当消费达到额度的80%时发送通知
- 支持按业务模块或模型设置独立额度

### 2.4 权限管理

#### 2.4.1 用户与角色

- 内置角色：**系统管理员**、**运维人员**、**业务模块负责人**、**普通用户**
- 支持自定义角色，可灵活分配权限
- 用户信息：用户名、密码、姓名、邮箱、手机号、部门、角色

#### 2.4.2 权限控制

| 功能 | 系统管理员 | 运维人员 | 业务模块负责人 | 普通用户 |
|------|-----------|----------|----------------|----------|
| Agent管理（增删改查） | 全部 | 查看 | 查看+编辑 | 查看 |
| Agent发布/下线 | 有 | 有 | 无 | 无 |
| 模型管理 | 全部 | 编辑 | 查看 | 查看 |
| 统计数据 | 全部 | 全部 | 本模块数据 | 查看 |
| 用户管理 | 全部 | 无 | 无 | 无 |
| 系统配置 | 有 | 部分 | 无 | 无 |

#### 2.4.3 业务模块授权

- 业务模块需经授权后方可调用指定Agent
- 支持按业务模块授权不同Agent的调用权限
- 授权时需指定调用配额（QPS限制、日调用量上限）

#### 2.4.4 操作审计

- 记录所有管理操作日志（谁、何时、何操作、何内容）
- 支持按操作人、时间、操作类型筛选
- 操作日志永久保留

---

## 3. 非功能需求

### 3.1 高鲁棒性

- 系统具备故障隔离能力，单个Agent不可用不影响其他Agent
- 支持服务降级，在系统压力过大时优先保证核心功能
- 数据存储采用主从复制，保障数据安全
- 具备完善的异常处理机制，避免因外部依赖故障导致系统崩溃

### 3.2 低耦合性

- 采用微服务架构设计，各模块独立部署、独立扩展
- Agent调用层与业务逻辑层分离，便于替换底层实现
- 采用事件驱动架构，模块间通过消息队列解耦
- 提供标准API接口，调用方无需感知Agent内部实现细节

### 3.3 可扩展性

- 采用插件化架构，新增Agent类型或模型支持时无需修改核心代码
- 数据库设计支持字段扩展，无需进行表结构变更
- 支持水平扩展，可通过增加节点提升系统容量
- 支持多租户架构，便于后续扩展至SaaS模式

### 3.4 性能要求

- Agent调用接口响应时间 P99 < 500ms
- 系统管理后台页面加载时间 < 2s
- 支持100个以上Agent同时在线
- 支持1000 QPS的Agent调用量

### 3.5 安全性

- 所有API接口需认证授权
- 敏感数据（如密钥、密码）加密存储
- 支持操作日志追溯
- 防止SQL注入、XSS等常见Web安全威胁

### 3.6 可用性

- 系统可用性目标：99.9%
- 预计年计划停机时间 < 8小时
- 提供数据备份与恢复能力

---

## 4. 技术架构设计

### 4.1 整体架构

```
┌─────────────────────────────────────────────────────────┐
│                      前端 (Vue3)                         │
│              管理后台 / 数据可视化 / 用户端                │
└─────────────────────────┬───────────────────────────────┘
                          │ REST API / WebSocket
┌─────────────────────────┴───────────────────────────────┐
│                    网关层 (Spring Gateway)                │
│              鉴权 / 路由 / 限流 / 监控                      │
└─────────────────────────┬───────────────────────────────┘
                          │
┌─────────────────────────┴───────────────────────────────┐
│                    业务服务层                              │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐          │
│  │ Agent   │ │  监控   │ │  模型   │ │  权限   │  ...     │
│  │ 管理服务 │ │ 服务   │ │ 服务   │ │ 服务   │          │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘          │
└─────────────────────────┬───────────────────────────────┘
                          │
┌─────────────────────────┴───────────────────────────────┐
│                    数据访问层                              │
│       MySQL (主从)          │        Redis (集群)         │
└─────────────────────────────┴────────────────────────────┘
```

### 4.2 技术选型

| 层级 | 技术 | 说明 |
|------|------|------|
| 后端框架 | Spring Boot 3.x | JDK 21，响应式编程支持 |
| 微服务网关 | Spring Cloud Gateway | 统一入口，鉴权路由 |
| 服务注册 | Nacos | 服务发现与配置管理 |
| 消息队列 | Apache RocketMQ | 事件驱动，解耦服务 |
| 数据库 | MySQL 8.0 | 主从架构，事务支持 |
| 缓存 | Redis Cluster | 热数据缓存，session存储 |
| ORM | MyBatis-Plus | 简化数据访问层 |
| 任务调度 | XXL-JOB | 分布式定时任务 |
| 日志 | ELK (Elasticsearch+Logstash+Kibana) | 日志采集与分析 |
| 链路追踪 | SkyWalking | 分布式追踪 |
| 容器化 | Docker + Kubernetes | 容器编排与部署 |
| 前端框架 | Vue3 + Element Plus | 现代化UI组件库 |
| 工程化 | Vite | 前端构建工具 |

### 4.3 核心模块设计

#### 4.3.1 Agent服务

职责：Agent的注册、编辑、发布、下线、版本管理

关键接口：
- `POST /api/v1/agents` - 创建Agent
- `GET /api/v1/agents` - 查询Agent列表
- `GET /api/v1/agents/{id}` - 查询Agent详情
- `PUT /api/v1/agents/{id}` - 更新Agent
- `DELETE /api/v1/agents/{id}` - 删除Agent（软删除）
- `POST /api/v1/agents/{id}/publish` - 发布Agent
- `POST /api/v1/agents/{id}/offline` - 下线Agent
- `GET /api/v1/agents/{id}/versions` - 获取版本列表

#### 4.3.2 监控服务

职责：调用链追踪、指标采集、告警管理

关键接口：
- `GET /api/v1/monitor/realtime` - 实时状态
- `GET /api/v1/monitor/statistics` - 统计报表
- `GET /api/v1/monitor/traces/{traceId}` - 链路详情
- `GET /api/v1/monitor/logs` - 日志查询

#### 4.3.3 模型服务

职责：模型目录管理、使用量统计、额度控制

关键接口：
- `POST /api/v1/models` - 添加模型
- `GET /api/v1/models` - 查询模型列表
- `GET /api/v1/models/{id}/usage` - 模型使用量
- `POST /api/v1/models/{id}/quota` - 设置额度

#### 4.3.4 权限服务

职责：用户管理、角色管理、权限控制、操作审计

关键接口：
- `POST /api/v1/users` - 创建用户
- `GET /api/v1/users` - 查询用户列表
- `POST /api/v1/roles` - 创建角色
- `POST /api/v1/permissions/grant` - 权限授权
- `GET /api/v1/audit/logs` - 审计日志

---

## 5. API接口设计概要

### 5.1 接口规范

- 协议：HTTPS
- 数据格式：JSON
- 编码：UTF-8
- 认证：Bearer Token (JWT)
- 版本号：URL路径中指定（如 /api/v1/）

### 5.2 通用响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": { },
  "timestamp": "2026-04-13T10:00:00Z",
  "traceId": "abc123"
}
```

### 5.3 错误码规范

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未认证 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

### 5.4 核心API列表

#### Agent管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/agents | 创建Agent |
| GET | /api/v1/agents | 查询Agent列表（支持分页、筛选） |
| GET | /api/v1/agents/{id} | 查询Agent详情 |
| PUT | /api/v1/agents/{id} | 更新Agent |
| DELETE | /api/v1/agents/{id} | 删除Agent（软删除） |
| POST | /api/v1/agents/{id}/publish | 发布Agent |
| POST | /api/v1/agents/{id}/offline | 下线Agent |
| GET | /api/v1/agents/{id}/versions | 查询版本历史 |
| POST | /api/v1/agents/{id}/call | 调用Agent |

#### 监控统计

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/v1/monitor/realtime | 实时监控数据 |
| GET | /api/v1/monitor/statistics | 统计报表数据 |
| GET | /api/v1/monitor/traces/{traceId} | 链路追踪详情 |
| GET | /api/v1/monitor/logs | 日志查询 |

#### 模型管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/models | 添加模型 |
| GET | /api/v1/models | 查询模型列表 |
| PUT | /api/v1/models/{id} | 更新模型信息 |
| DELETE | /api/v1/models/{id} | 删除模型 |
| GET | /api/v1/models/{id}/usage | 模型使用量统计 |

#### 权限管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/users | 创建用户 |
| GET | /api/v1/users | 查询用户列表 |
| PUT | /api/v1/users/{id} | 更新用户信息 |
| DELETE | /api/v1/users/{id} | 删除用户 |
| POST | /api/v1/roles | 创建角色 |
| GET | /api/v1/roles | 查询角色列表 |
| PUT | /api/v1/roles/{id}/permissions | 更新角色权限 |
| POST | /api/v1/business-modules | 创建业务模块 |
| POST | /api/v1/business-modules/{id}/agent-auth | 授权Agent给业务模块 |
| GET | /api/v1/audit/logs | 查询审计日志 |

---

## 6. 数据库设计概要

### 6.1 ER图概要

主要实体关系：

```
用户 (sys_user) ──N:N── 角色 (sys_role)
角色 ──N:N── 权限 (sys_permission)
用户 ──1:N── 业务模块 (biz_module)
业务模块 ──N:N── Agent (ai_agent) [授权关系]
Agent ──1:N── Agent版本 (ai_agent_version)
Agent ──1:N── 模型关联 (ai_model_usage)
调用记录 (mon_call_record) ──N:1── Agent
调用记录 ──N:1── 业务模块
```

### 6.2 核心表结构

#### 6.2.1 sys_user (用户表)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| username | VARCHAR(50) | 用户名（唯一） |
| password | VARCHAR(255) | 密码（加密存储） |
| real_name | VARCHAR(50) | 真实姓名 |
| email | VARCHAR(100) | 邮箱 |
| phone | VARCHAR(20) | 手机号 |
| department | VARCHAR(100) | 部门 |
| status | TINYINT | 状态（0禁用 1启用） |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

#### 6.2.2 sys_role (角色表)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| role_code | VARCHAR(50) | 角色代码（唯一） |
| role_name | VARCHAR(50) | 角色名称 |
| description | VARCHAR(255) | 角色描述 |
| create_time | DATETIME | 创建时间 |

#### 6.2.3 ai_agent (Agent表)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| agent_code | VARCHAR(50) | Agent编码（唯一） |
| agent_name | VARCHAR(100) | Agent名称 |
| description | TEXT | 描述 |
| category | VARCHAR(50) | 分类 |
| api_url | VARCHAR(500) | 接口地址 |
| http_method | VARCHAR(10) | 调用方式 |
| request_schema | JSON | 请求参数模板 |
| response_schema | JSON | 响应格式说明 |
| status | TINYINT | 状态（0草稿 1上线 2下线） |
| owner_id | BIGINT | 负责人 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |
| is_deleted | TINYINT | 软删除标记 |

#### 6.2.4 ai_agent_version (Agent版本表)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| agent_id | BIGINT | Agent ID |
| version | VARCHAR(20) | 版本号 |
| changelog | TEXT | 变更说明 |
| config | JSON | 版本配置 |
| status | TINYINT | 状态（0未发布 1已发布） |
| publish_time | DATETIME | 发布时间 |
| create_time | DATETIME | 创建时间 |

#### 6.2.5 ai_model (模型表)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| model_code | VARCHAR(50) | 模型编码（唯一） |
| model_name | VARCHAR(100) | 模型名称 |
| provider | VARCHAR(50) | 厂商 |
| model_version | VARCHAR(50) | 模型版本 |
| endpoint | VARCHAR(500) | API地址 |
| price_per_1k_token | DECIMAL(10,6) | 价格 |
| status | TINYINT | 状态（0禁用 1启用） |
| create_time | DATETIME | 创建时间 |

#### 6.2.6 mon_call_record (调用记录表)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| trace_id | VARCHAR(50) | 链路ID |
| agent_id | BIGINT | Agent ID |
| agent_version | VARCHAR(20) | Agent版本 |
| biz_module_id | BIGINT | 业务模块ID |
| model_id | BIGINT | 模型ID |
| request_time | DATETIME | 请求时间 |
| response_time | DATETIME | 响应时间 |
| duration_ms | INT | 耗时（毫秒） |
| input_tokens | INT | 输入Token |
| output_tokens | INT | 输出Token |
| status_code | INT | 状态码 |
| success | TINYINT | 是否成功 |
| error_message | TEXT | 错误信息 |
| create_time | DATETIME | 创建时间 |

#### 6.2.7 biz_module (业务模块表)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| module_code | VARCHAR(50) | 模块编码（唯一） |
| module_name | VARCHAR(100) | 模块名称 |
| description | TEXT | 描述 |
| owner_id | BIGINT | 负责人 |
| status | TINYINT | 状态 |
| create_time | DATETIME | 创建时间 |

#### 6.2.8 biz_agent_auth (业务模块Agent授权表)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| biz_module_id | BIGINT | 业务模块ID |
| agent_id | BIGINT | Agent ID |
| qps_limit | INT | QPS限制 |
| daily_limit | INT | 日调用量上限 |
| status | TINYINT | 状态 |
| create_time | DATETIME | 创建时间 |

#### 6.2.9 sys_audit_log (审计日志表)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 操作人 |
| operation | VARCHAR(50) | 操作类型 |
| resource_type | VARCHAR(50) | 资源类型 |
| resource_id | BIGINT | 资源ID |
| before_value | JSON | 修改前内容 |
| after_value | JSON | 修改后内容 |
| ip_address | VARCHAR(50) | IP地址 |
| create_time | DATETIME | 操作时间 |

### 6.3 索引设计

| 表名 | 索引字段 | 类型 | 说明 |
|------|----------|------|------|
| ai_agent | agent_code | UNIQUE | Agent编码唯一索引 |
| ai_agent | status, category | INDEX | 状态+分类复合索引 |
| mon_call_record | trace_id | INDEX | 链路追踪索引 |
| mon_call_record | agent_id, create_time | INDEX | Agent调用统计索引 |
| mon_call_record | biz_module_id, create_time | INDEX | 业务模块统计索引 |
| sys_audit_log | user_id, create_time | INDEX | 审计查询索引 |

---

## 7. 附录

### 7.1 Agent分类参考

- 文本处理：文本生成、文本分类、情感分析、翻译
- 图像识别：图像分类、目标检测、OCR识别
- 问答系统：知识问答、FAQ问答
- 对话系统：多轮对话、任务型对话
- 语音处理：语音识别、语音合成

### 7.2 状态码说明

| 状态码 | 说明 |
|--------|------|
| 0 | 禁用/下线 |
| 1 | 启用/上线 |
| 2 | 草稿 |
| 3 | 审核中 |

### 7.3 版本规划

- MVP版本（V1.0）：完成Agent管理、基础监控、权限控制
- V1.1：增加模型使用统计、告警管理
- V2.0：微服务化改造、多租户支持