# AI Platform v6 项目协作闭环设计

**日期：** 2026-07-28
**状态：** 已批准，可进入实施计划阶段
**范围：** 仅包含 v6 产品闭环

## 1. 目标

在不重建现有自动化流水线、Agent Runtime、Memory Center、治理体系和
多租户 RBAC 的前提下，完成 Project War Room 的产品协作闭环。

项目成员需要通过三种边界清晰的模式协作：

1. 由只读 AI 协调者参与的项目共享讨论。
2. 基于用户可见项目信息的临时私人问答。
3. 仅在用户明确确认后才允许改变项目状态的 Team Agent 执行。

本次交付同时将项目任务指派升级为结构化、可审计、受权限控制、可在个人待办中
查看的领域能力。

## 2. 第一性原理边界

- 读取项目信息和改变项目状态是两种不同能力，必须使用不同接口和权限。
- 模型回复只是一种建议，除非用户明确转为任务草案或确认执行，否则不能改变状态。
- 共享项目信息需要持久化和审计；私人问答默认不持久化。
- 指派是独立领域概念，不能继续作为自由文本藏在 Agent Run 输入或审批备注中。
- 每个服务边界都必须对项目权限执行失败关闭。
- 通过稳定接口复用现有 Runtime、流水线、记忆、模型、审计和租户组件。

## 3. 范围

### 3.1 本次包含

- 使用租户中已启用的默认 `CHAT` 模型实现 Discussion Coordinator。
- 项目共享讨论历史。
- Coordinator 以结构化方式返回总结、风险、待确认问题和任务草案建议。
- 使用同一默认 `CHAT` 模型和安全项目上下文实现 Private Ask。
- 用户主动将选中的 Private Ask 内容转为项目任务草案。
- 支持确认和取消生命周期的持久化任务草案。
- 为 Agent Run、审批和任务草案提供结构化指派。
- 记录指派、改派、接受、完成和取消事件。
- 提供项目内及跨项目的“我的待办”视图。
- 引入项目工作区专用权限。
- 拆分 War Room 前端组件并明确三种协作模式边界。
- 从 Memory Center 回跳相关 Project War Room。

### 3.2 本次不包含

- RAG 混合切分增强。
- 真实代码扫描工具接入。
- Skill 业务数据源连接器。
- Spring AI 2.x Chat Memory 迁移。
- Agent 商店。
- 视频和文档需求解析。
- 新建协作微服务。
- 自主多 Agent 循环。
- 默认启用 Agent Runtime Dispatcher。

## 4. 架构

实现继续位于现有 Spring Boot 应用和 Vue 管理端中，不拆分新服务。

### 4.1 后端组件

#### `ProjectDiscussionService`

- 保存并查询项目共享讨论消息。
- 使用安全项目上下文调用 Coordinator。
- 保存通过校验的 Coordinator 回复和建议任务草案。
- 不允许创建 Agent Run 或修改流水线。

#### `ProjectPrivateAskService`

- 为当前项目成员构建安全项目上下文。
- 调用默认 `CHAT` 模型。
- 返回回答、安全来源引用和能力限制。
- 不持久化请求、回答或当前页面会话。

#### `ProjectTaskDraftService`

- 从 Coordinator 输出、手动输入或显式 Private Ask 转换创建任务草案。
- 管理 `DRAFT`、`CONFIRMED`、`CANCELLED` 生命周期。
- 仅在用户确认任务草案时创建或复用 Team Agent Run。

#### `ProjectAssignmentService`

- 为 `AGENT_RUN`、`APPROVAL`、`TASK_DRAFT` 创建和更新指派。
- 校验有效项目成员身份。
- 执行指派管理权限。
- 使用乐观锁处理改派。
- 追加不可变的指派事件。

#### `ProjectWorkspaceContextService`

- 为 Discussion Coordinator 和 Private Ask 生成统一的业务安全上下文。
- 复用现有 War Room、成员、流水线、Runtime 和已批准记忆服务。
- 排除原始 Prompt、工具载荷、模型推理、密钥、绝对路径、堆栈和私有记忆正文。

#### `ProjectWarRoomService`

- 继续作为只读聚合边界。
- 增加最近共享讨论、任务草案和指派的安全视图。
- 不吸收写操作相关的协作逻辑。

### 4.2 前端组件

- `ProjectOverviewPanel.vue`
- `ProjectDiscussionPanel.vue`
- `ProjectPrivateAskPanel.vue`
- `ProjectAssignmentPanel.vue`
- `ProjectTaskDraftCard.vue`

`ProjectWarRoom.vue` 只负责项目选择、布局、路由状态和各面板数据加载协调。

## 5. 数据模型

### 5.1 `project_discussion_message`

- `id`
- `tenant_id`
- `project_id`
- `project_key`
- `sender_user_id`
- `sender_type`：`USER`、`COORDINATOR` 或 `SYSTEM`
- `message_type`：`TEXT`、`SUMMARY`、`RISK` 或 `TASK_SUGGESTION`
- `content`
- `model_code`
- `idempotency_key`
- `create_time`
- `is_deleted`

Coordinator 请求的幂等键在租户和项目范围内唯一。

### 5.2 `project_task_draft`

- `id`
- `tenant_id`
- `project_id`
- `project_key`
- `source_type`：`DISCUSSION`、`PRIVATE_ASK` 或 `MANUAL`
- `source_message_id`
- `title`
- `description`
- `business_type`
- `business_id`
- `suggested_agent_id`
- `status`：`DRAFT`、`CONFIRMED` 或 `CANCELLED`
- `confirmed_run_id`
- `created_by`
- `confirmed_by`
- `create_time`
- `update_time`
- `version`
- `is_deleted`

Private Ask 转换不填写 `source_message_id`，因为私人会话不持久化。

### 5.3 `project_assignment`

- `id`
- `tenant_id`
- `project_id`
- `project_key`
- `target_type`：`AGENT_RUN`、`APPROVAL` 或 `TASK_DRAFT`
- `target_id`
- `assignee_user_id`
- `assigned_by`
- `status`：`ASSIGNED`、`ACCEPTED`、`COMPLETED` 或 `CANCELLED`
- `due_time`
- `create_time`
- `update_time`
- `version`
- `is_deleted`

每个租户、目标类型和目标 ID 只允许存在一条有效指派。

### 5.4 `project_assignment_event`

- `id`
- `tenant_id`
- `project_id`
- `project_key`
- `assignment_id`
- `event_type`：`CREATED`、`REASSIGNED`、`ACCEPTED`、`COMPLETED` 或
  `CANCELLED`
- `from_user_id`
- `to_user_id`
- `actor_user_id`
- `reason`
- `create_time`

事件只允许追加，不允许修改。应用边界同时将事件写入现有审计日志。

## 6. 权限

新增三项权限：

- `project-workspace:view`
- `project-workspace:execute`
- `project-workspace:manage`

迁移规则：

- 系统管理员和项目管理员获得全部三项权限。
- 现有有效项目成员获得 `project-workspace:view`。
- 不再使用 `automation:list` 作为长期兼容回退权限。

权限执行规则：

- 查看 War Room、共享讨论和使用 Private Ask，需要
  `project-workspace:view` 以及有效项目成员身份。
- 确认任务草案或创建 Team Agent Run，需要
  `project-workspace:execute` 以及有效项目成员身份。
- 指派或改派需要 `project-workspace:manage`、项目负责人权限，或者目标审批项
  对应的现有审批权限。
- 处理人必须是相同租户、相同项目的有效成员。

## 7. API 设计

所有接口均位于 `/api/v1/project-workspaces` 下。

### 7.1 共享讨论

- `GET /{projectKey}/discussions`
- `POST /{projectKey}/discussions`
- `POST /{projectKey}/discussion-coordinator`

Coordinator 响应结构：

```json
{
  "summary": "string",
  "risks": ["string"],
  "questions": ["string"],
  "taskDrafts": [
    {
      "title": "string",
      "description": "string",
      "businessType": "string",
      "suggestedAgentId": 1
    }
  ]
}
```

Coordinator 最多读取最近 50 条共享讨论和 20 条项目事件。

### 7.2 Private Ask

- `POST /{projectKey}/private-ask`
- `POST /{projectKey}/task-drafts`

Private Ask 响应结构：

```json
{
  "answer": "string",
  "sources": [
    {
      "type": "PROJECT_SUMMARY",
      "reference": "string"
    }
  ],
  "limitations": ["string"]
}
```

将 Private Ask 转为任务草案时，客户端必须在独立请求中明确提交选中的标题和描述。

### 7.3 任务草案

- `GET /{projectKey}/task-drafts`
- `POST /{projectKey}/task-drafts`
- `POST /{projectKey}/task-drafts/{draftId}/confirm`
- `POST /{projectKey}/task-drafts/{draftId}/cancel`

确认草案时，通过现有 Runtime 创建或复用 Team Agent Run，并将返回的 Run ID
保存到草案中。

### 7.4 指派

- `GET /{projectKey}/assignments`
- `PUT /{projectKey}/assignments/{assignmentId}/assignee`
- `POST /{projectKey}/assignments/{assignmentId}/accept`
- `POST /{projectKey}/assignments/{assignmentId}/complete`
- `GET /my-assignments`

改派请求必须携带当前 `version`。版本过期时返回冲突响应和最新安全指派视图。

## 8. 模型上下文和安全

允许发送给模型的上下文：

- 项目名称、类型、负责人和业务安全状态。
- 业务阶段标签和公开项目时间线。
- 风险摘要和数量。
- 待审批数量和安全标题。
- 已批准的记忆摘要和引用。
- 当前项目的共享讨论。

禁止发送给模型的上下文：

- 原始 Prompt 和模型推理。
- Agent Run 原始输入或输出。
- 工具输入输出、租约数据和内部执行载荷。
- 密钥、API Key、凭据和加密密钥。
- 绝对文件路径和存储根目录。
- 内部异常堆栈。
- 任何用户之前的 Private Ask 内容。
- 未批准的原始记忆正文。

模型输出必须解析为文档规定的响应类型，并在返回或保存前检查必填字段、最大长度
和禁止出现的敏感模式。

## 9. 状态和数据流

```text
共享讨论
  → 安全项目上下文
  → Discussion Coordinator
  → 通过校验的回复和可选任务草案

Private Ask
  → 安全项目上下文和当前页面会话
  → 私人回答
  → 不持久化
  → 用户可显式转为任务草案

任务草案
  → 用户确认
  → 现有 AgentRunService.createOrReuse
  → 结构化指派
  → 可审计项目队列

Run / 审批 / 任务草案
  → 结构化指派
  → 不可变指派事件
  → 我的待办
```

## 10. 错误处理

- 未配置已启用的默认 `CHAT` 模型时，返回“模型未配置”业务错误。
- 模型调用超时或供应商失败时，不保存 Coordinator 回复和任务草案；用户原始共享
  消息仍可用于重试。
- 重复 Coordinator 请求通过幂等键返回之前的结果。
- 项目成员身份或权限变化后，下一次请求立即失败关闭。
- 拒绝跨项目和跨租户目标 ID。
- 过期版本的改派请求返回 HTTP 冲突和最新指派视图。
- 结构化模型输出无效时拒绝整个结果，不展示部分内容。
- 除非用户明确将选中文本作为新任务草案提交，否则 Private Ask 内容不能进入
  共享日志、项目事件、任务记录或正式记忆。

## 11. 用户体验

War Room 提供四个明确区域：

1. 项目总览
2. 共享讨论
3. Private Ask
4. 我的待办

交互规则：

- 用户共享消息、Coordinator 回复和系统事件使用不同视觉样式。
- Private Ask 明确提示“仅当前用户可见，页面会话结束后不保留”。
- 草案确认、指派和改派必须二次确认。
- 改派确认框显示原处理人、新处理人和影响对象。
- 权限不足、模型配置缺失和版本冲突均提供明确恢复操作。
- Private Ask 不出现在共享时间线、Memory Center 或项目讨论中。

## 12. 开发排期

目标开发周期为 6 个工作日。

| 里程碑 | 时长 | 交付物 |
| --- | ---: | --- |
| M0 设计与计划 | 0.5 天 | 已批准设计、实施计划、验收矩阵 |
| M1 权限与数据基础 | 1 天 | 三项权限、四张表、迁移和实体 |
| M2 结构化指派 | 1 天 | 指派、改派、审计、我的待办 |
| M3 讨论与 Private Ask | 1.5 天 | 共享讨论、Coordinator、临时私聊、草案转换 |
| M4 War Room 前端 | 1.5 天 | 四个产品区域和指派交互 |
| M5 集成收口 | 0.5 天 | API 契约、迁移入口、文档和最终审查 |

## 13. 监督和任务治理

### 13.1 项目经理

- 负责实施台账、依赖、阻塞、每日状态和接下来三个可验证承诺。
- 将每个里程碑标记为 Green、Yellow 或 Red。
- 不修改业务代码。

### 13.2 产品经理

- 在每个里程碑检查用户价值、文案、模式边界、隐私和验收条件。
- 拒绝任何混淆共享讨论、Private Ask 和 Team Agent 的实现。
- 不修改业务代码。

### 13.3 开发与审查

- 每个实施任务由一个新的开发代理负责。
- 每项任务先编写失败用例，再实现功能，并形成独立提交。
- 下一项任务开始前，由审查代理检查规格符合性和代码质量。
- 重要问题退回原开发代理修改。

里程碑门禁：

- M1：迁移幂等且可独立审查。
- M2：拒绝无权限、跨项目和跨租户的指派变更。
- M3：证明 Private Ask 不持久化，Coordinator 不改变项目状态。
- M4：三种协作模式在视觉和操作上边界清晰。
- M5：每项已批准需求都有对应实现和验收证据。

## 14. 验收标准

- 拥有查看权限的项目成员可以读取共享讨论，并对可访问项目使用 Private Ask。
- Discussion Coordinator 只能生成通过校验的总结、风险、问题和任务草案建议。
- Private Ask 内容不在数据库、项目事件、正式记忆或共享日志中留下记录。
- 用户可以主动将选中的 Private Ask 内容转为任务草案。
- 只有确认任务草案才会创建或复用 Team Agent Run。
- 指派必须持久化、校验成员身份、使用版本控制并可审计。
- 授权用户可以改派任务，未授权用户不能改派。
- 用户可以跨可访问项目查询自己的待办。
- War Room 使用项目工作区专用权限。
- 现有自动化流水线、Agent Run 控制台和 Memory Center 继续可用，并与
  Project War Room 保持一致跳转。
- Dispatcher 保持默认关闭。
