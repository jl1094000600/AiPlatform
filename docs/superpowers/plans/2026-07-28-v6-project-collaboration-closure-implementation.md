# v6 项目协作闭环实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标：** 在现有 Project War Room 上完成共享讨论、讨论协调员、私密问答、任务草稿和结构化指派闭环，同时落实专属权限、租户隔离、隐私边界与可追溯审计。

**架构：** 保留 `ProjectWarRoomService` 作为只读聚合器，新增项目协作领域的 DTO、实体、Mapper、服务和控制器。所有 AI 请求统一经 `ProjectWorkspaceContextService` 生成安全上下文，再由 `ProjectCollaborationModelService` 调用当前租户已启用的默认 `CHAT` 模型；私密问答只接受当前页面传入的历史，不落库。指派使用统一 `project_assignment` 聚合与不可变 `project_assignment_event` 事件表，并以乐观锁和幂等键阻止重复或并发覆盖。

**技术栈：** Java 21、Spring Boot 3.2、Spring AI 1.0.5、MyBatis-Plus、MySQL 8、JUnit 5/Mockito、Vue 3、Element Plus、Vite、Node 断言测试。

## 全局约束

- 所有新增表和查询必须包含 `tenant_id`，项目数据必须同时校验 `project_key` 和当前租户。
- 共享讨论最多向模型提供最近 50 条消息，项目事件最多 20 条。
- 模型上下文不得包含原始 prompt、Run 输入/输出、工具载荷、密钥、绝对路径、堆栈、私密问答历史或个人记忆正文。
- Coordinator 与 Private Ask 均为只读；只有用户显式确认任务草稿后才能创建 Run 和指派。
- 指派对象只允许 `AGENT_RUN`、`AUTOMATION_APPROVAL`、`TASK_DRAFT`，被指派人必须是当前项目的有效成员。
- 只有项目 OWNER/MANAGER、平台管理员或具备 `project-workspace:manage` 的用户可分派/改派；执行类操作要求 `project-workspace:execute`。
- `project-workspace:view` 不再长期回退到 `automation:list`；迁移时将新权限显式赋予既有角色。
- 每个开发任务必须独立提交，并通过规格符合性审查和代码质量审查后才能进入下一项。

---

## 任务 1：协作表、领域实体和专属权限

**文件：**

- 新建：`backend/sql/schema-project-collaboration.sql`
- 新建：`backend/src/main/java/com/aipal/config/ProjectCollaborationSchemaInitializer.java`
- 新建：`backend/src/main/java/com/aipal/entity/ProjectDiscussionMessage.java`
- 新建：`backend/src/main/java/com/aipal/entity/ProjectTaskDraft.java`
- 新建：`backend/src/main/java/com/aipal/entity/ProjectAssignment.java`
- 新建：`backend/src/main/java/com/aipal/entity/ProjectAssignmentEvent.java`
- 新建：`backend/src/main/java/com/aipal/mapper/ProjectDiscussionMessageMapper.java`
- 新建：`backend/src/main/java/com/aipal/mapper/ProjectTaskDraftMapper.java`
- 新建：`backend/src/main/java/com/aipal/mapper/ProjectAssignmentMapper.java`
- 新建：`backend/src/main/java/com/aipal/mapper/ProjectAssignmentEventMapper.java`
- 修改：`backend/src/main/java/com/aipal/config/TenantSecuritySchemaInitializer.java`
- 新建：`backend/src/test/java/com/aipal/config/ProjectCollaborationSchemaInitializerTest.java`
- 修改：`backend/src/test/java/com/aipal/config/TenantSecuritySchemaInitializerTest.java`

- [ ] **步骤 1：先写协作表初始化失败测试**

在 `ProjectCollaborationSchemaInitializerTest` 使用 H2 MySQL 模式创建 DataSource，执行 initializer 后断言四张表存在，并断言：

```java
assertTrue(columns("PROJECT_ASSIGNMENT").containsAll(
        Set.of("TENANT_ID", "PROJECT_KEY", "TARGET_TYPE", "TARGET_ID",
                "ASSIGNEE_USER_ID", "STATUS", "VERSION")));
assertTrue(indexes("PROJECT_ASSIGNMENT").contains("UK_PROJECT_ASSIGNMENT_TARGET"));
assertTrue(indexes("PROJECT_ASSIGNMENT_EVENT").contains("IDX_PROJECT_ASSIGNMENT_EVENT"));
```

再次执行 `initialize()`，断言幂等且表结构不变。

- [ ] **步骤 2：运行目标测试并确认失败**

执行：

```bash
cd backend
mvn -Dtest=ProjectCollaborationSchemaInitializerTest test
```

预期：因 initializer 和实体尚不存在而编译失败。

- [ ] **步骤 3：实现 SQL、initializer、实体和 Mapper**

四张表字段必须与设计文档一致：

```text
project_discussion_message:
  id, tenant_id, project_key, sender_user_id, sender_name,
  message_type(USER|COORDINATOR), content, idempotency_key,
  create_time, update_time, is_deleted

project_task_draft:
  id, tenant_id, project_key, source_type, source_id, title,
  description, suggested_agent_id, suggested_assignee_user_id,
  status(DRAFT|CONFIRMED|CANCELLED), confirmed_run_id,
  idempotency_key, version, created_by, create_time, update_time, is_deleted

project_assignment:
  id, tenant_id, project_key, target_type, target_id,
  assignee_user_id, status(ASSIGNED|ACCEPTED|COMPLETED|CANCELLED),
  version, created_by, create_time, update_time, is_deleted

project_assignment_event:
  id, tenant_id, assignment_id, event_type, from_assignee_user_id,
  to_assignee_user_id, from_status, to_status, actor_user_id,
  actor_name, reason, create_time
```

唯一索引：

```sql
UNIQUE KEY uk_project_discussion_idempotency (tenant_id, project_key, idempotency_key)
UNIQUE KEY uk_project_task_draft_idempotency (tenant_id, project_key, idempotency_key)
UNIQUE KEY uk_project_assignment_target (tenant_id, project_key, target_type, target_id)
```

initializer 采用项目现有 `@ConditionalOnProperty` 和 `CREATE TABLE IF NOT EXISTS` 风格；SQL 文件保持与 initializer 相同的 DDL，供人工部署使用。

- [ ] **步骤 4：写权限迁移失败测试**

在 `TenantSecuritySchemaInitializerTest` 断言：

```java
assertPermissionExists("project-workspace:view");
assertPermissionExists("project-workspace:execute");
assertPermissionExists("project-workspace:manage");
assertRoleHas("developer", "project-workspace:view", "project-workspace:execute");
assertRoleHas("reviewer", "project-workspace:view");
assertRoleHas("readonly", "project-workspace:view");
assertMenuPermission("project-workspaces", "project-workspace:view");
```

- [ ] **步骤 5：实现权限种子和角色迁移**

在 `TenantSecuritySchemaInitializer`：

- `platform_admin`、`tenant_admin`：三项权限；
- `developer`：`view`、`execute`；
- `reviewer`、`readonly`：仅 `view`；
- 新增 “项目工作台” 菜单，权限为 `project-workspace:view`；
- 将四张协作表加入 `tenantTables()`。

- [ ] **步骤 6：运行测试并提交**

执行：

```bash
cd backend
mvn -Dtest=ProjectCollaborationSchemaInitializerTest,TenantSecuritySchemaInitializerTest test
```

预期：全部通过。

提交：

```bash
git add backend/sql/schema-project-collaboration.sql backend/src/main/java/com/aipal/config/ProjectCollaborationSchemaInitializer.java backend/src/main/java/com/aipal/config/TenantSecuritySchemaInitializer.java backend/src/main/java/com/aipal/entity/ProjectDiscussionMessage.java backend/src/main/java/com/aipal/entity/ProjectTaskDraft.java backend/src/main/java/com/aipal/entity/ProjectAssignment.java backend/src/main/java/com/aipal/entity/ProjectAssignmentEvent.java backend/src/main/java/com/aipal/mapper/ProjectDiscussionMessageMapper.java backend/src/main/java/com/aipal/mapper/ProjectTaskDraftMapper.java backend/src/main/java/com/aipal/mapper/ProjectAssignmentMapper.java backend/src/main/java/com/aipal/mapper/ProjectAssignmentEventMapper.java backend/src/test/java/com/aipal/config/ProjectCollaborationSchemaInitializerTest.java backend/src/test/java/com/aipal/config/TenantSecuritySchemaInitializerTest.java
git commit -m "feat: add project collaboration schema and permissions"
```

---

## 任务 2：统一指派领域与“我的指派”

**文件：**

- 新建：`backend/src/main/java/com/aipal/dto/ProjectCollaborationDtos.java`
- 新建：`backend/src/main/java/com/aipal/service/ProjectAssignmentService.java`
- 新建：`backend/src/main/java/com/aipal/controller/ProjectCollaborationController.java`
- 修改：`backend/src/main/java/com/aipal/service/memory/MemoryProjectService.java`
- 修改：`backend/src/main/java/com/aipal/controller/ProjectWarRoomController.java`
- 修改：`backend/src/main/java/com/aipal/dto/ProjectWarRoomDtos.java`
- 修改：`backend/src/main/java/com/aipal/service/ProjectWarRoomService.java`
- 新建：`backend/src/test/java/com/aipal/service/ProjectAssignmentServiceTest.java`
- 新建：`backend/src/test/java/com/aipal/controller/ProjectCollaborationControllerPermissionTest.java`
- 修改：`backend/src/test/java/com/aipal/service/ProjectWarRoomServiceTest.java`

- [ ] **步骤 1：写成员、权限、乐观锁和事件测试**

`ProjectAssignmentServiceTest` 覆盖：

```java
service.assign("p-1", new AssignRequest("AGENT_RUN", 9L, 21L, "首轮分派"));
verify(eventMapper).insert(argThat(e -> "ASSIGNED".equals(e.getEventType())));
```

并分别断言：

- 非项目成员作为 assignee 返回 400；
- 普通 MEMBER 改派返回 403；
- `expectedVersion` 不匹配返回 409；
- 重复 target 返回现有指派，不新增第二条；
- assignee 本人可 `accept`、`complete`；
- `listMine()` 只查询当前租户和当前用户。

- [ ] **步骤 2：运行测试并确认失败**

```bash
cd backend
mvn -Dtest=ProjectAssignmentServiceTest test
```

预期：新服务不存在而失败。

- [ ] **步骤 3：补齐项目成员查询能力**

在 `MemoryProjectService` 增加：

```java
public ProjectMemberView requireActiveMember(String projectKey, Long userId)
```

该方法必须同时校验租户、项目、成员 `ACTIVE` 状态，并在不存在时抛出 `BizException(400, "被指派人不是当前项目的有效成员")`。

- [ ] **步骤 4：实现 DTO 与 `ProjectAssignmentService`**

`ProjectCollaborationDtos` 至少包含：

```java
record AssignRequest(String targetType, Long targetId, Long assigneeUserId, String reason) {}
record ReassignRequest(Long assigneeUserId, Integer expectedVersion, String reason) {}
record AssignmentActionRequest(Integer expectedVersion, String reason) {}
record AssignmentView(Long id, String projectKey, String targetType, Long targetId,
                      Long assigneeUserId, String assigneeName, String status,
                      Integer version, LocalDateTime updateTime) {}
```

`ProjectAssignmentService` 对外方法：

```java
AssignmentView assign(String projectKey, AssignRequest request);
AssignmentView reassign(String projectKey, Long assignmentId, ReassignRequest request);
AssignmentView accept(String projectKey, Long assignmentId, AssignmentActionRequest request);
AssignmentView complete(String projectKey, Long assignmentId, AssignmentActionRequest request);
List<AssignmentView> listProject(String projectKey);
List<AssignmentView> listMine();
```

更新使用 `version = expectedVersion` 条件并 `version = version + 1`；影响行数为 0 时返回 409。每次状态或 assignee 变化均插入 `project_assignment_event`。

- [ ] **步骤 5：增加指派 API 和专属权限**

`ProjectCollaborationController` 增加：

```text
GET  /api/v1/project-workspaces/{projectKey}/assignments
POST /api/v1/project-workspaces/{projectKey}/assignments
POST /api/v1/project-workspaces/{projectKey}/assignments/{id}/reassign
POST /api/v1/project-workspaces/{projectKey}/assignments/{id}/accept
POST /api/v1/project-workspaces/{projectKey}/assignments/{id}/complete
GET  /api/v1/project-workspaces/my-assignments
```

读取使用 `project-workspace:view`，创建/改派使用 `project-workspace:manage`，接受/完成使用 `project-workspace:execute`。

把 `ProjectWarRoomController` 的：

- 工作区列表、war-room、mentionable-users 改为 `project-workspace:view`；
- team-agent-runs 改为 `project-workspace:execute`。

- [ ] **步骤 6：Team Agent 创建后同步建立指派**

给 `TeamAgentRunRequest` 增加 `Long assigneeUserId` 和 `String assignmentReason`。`ProjectWarRoomService.createTeamAgentRun` 在 Run 创建成功且 assignee 非空时调用：

```java
projectAssignmentService.assign(projectKey,
        new AssignRequest("AGENT_RUN", run.getId(), request.assigneeUserId(), request.assignmentReason()));
```

事务内任一校验失败应整体回滚。

- [ ] **步骤 7：运行测试并提交**

```bash
cd backend
mvn -Dtest=ProjectAssignmentServiceTest,ProjectCollaborationControllerPermissionTest,ProjectWarRoomServiceTest test
```

提交：

```bash
git add backend/src/main/java/com/aipal/dto/ProjectCollaborationDtos.java backend/src/main/java/com/aipal/service/ProjectAssignmentService.java backend/src/main/java/com/aipal/controller/ProjectCollaborationController.java backend/src/main/java/com/aipal/service/memory/MemoryProjectService.java backend/src/main/java/com/aipal/controller/ProjectWarRoomController.java backend/src/main/java/com/aipal/dto/ProjectWarRoomDtos.java backend/src/main/java/com/aipal/service/ProjectWarRoomService.java backend/src/test/java/com/aipal/service/ProjectAssignmentServiceTest.java backend/src/test/java/com/aipal/controller/ProjectCollaborationControllerPermissionTest.java backend/src/test/java/com/aipal/service/ProjectWarRoomServiceTest.java
git commit -m "feat: add structured project assignments"
```

---

## 任务 3：模型安全上下文与结构化响应网关

**文件：**

- 新建：`backend/src/main/java/com/aipal/service/ProjectWorkspaceContextService.java`
- 新建：`backend/src/main/java/com/aipal/service/ProjectCollaborationModelService.java`
- 修改：`backend/src/main/java/com/aipal/dto/ProjectCollaborationDtos.java`
- 新建：`backend/src/test/java/com/aipal/service/ProjectWorkspaceContextServiceTest.java`
- 新建：`backend/src/test/java/com/aipal/service/ProjectCollaborationModelServiceTest.java`

- [ ] **步骤 1：写安全上下文泄漏测试**

构造包含绝对路径、prompt、`inputJson`、`resultJson`、工具 payload 和 stack trace 的 Run/事件数据，断言 `SafeProjectContext.toString()` 和序列化 JSON 都不包含：

```java
List.of("/Users/", "C:\\", "inputJson", "resultJson", "definitionSnapshot",
        "prompt", "payload", "apiKey", "stack trace")
```

同时断言讨论消息最多 50 条、项目事件最多 20 条。

- [ ] **步骤 2：写默认模型与非法 JSON 测试**

`ProjectCollaborationModelServiceTest` 断言：

- 使用 `modelService.getDefaultEnabledModel(ModelService.CAPABILITY_CHAT)`；
- 默认 CHAT 模型不存在时返回 503；
- Coordinator 返回非 JSON、未知字段或空 `summary` 时返回 502，不创建任何草稿；
- Private Ask 返回非 JSON 时返回 502；
- 合法响应映射为强类型 DTO。

- [ ] **步骤 3：运行测试并确认失败**

```bash
cd backend
mvn -Dtest=ProjectWorkspaceContextServiceTest,ProjectCollaborationModelServiceTest test
```

- [ ] **步骤 4：实现安全上下文**

`ProjectWorkspaceContextService` 对外只暴露：

```java
SafeProjectContext build(String projectKey);
```

`SafeProjectContext` 仅包含：

- 项目键、项目名、项目状态；
- War Room summary、流水线标题/状态/当前业务阶段；
- Run 的业务 ID、状态、Agent ID、时间；
- 风险卡片；
- 最近 50 条共享讨论；
- 最近 20 条指派事件；
-成员的 userId、展示名、项目角色。

禁止直接暴露数据库实体；所有自由文本经长度上限和路径/敏感键过滤。

- [ ] **步骤 5：实现模型网关**

对外方法：

```java
CoordinatorResult coordinate(SafeProjectContext context);
PrivateAskResult privateAsk(SafeProjectContext context, PrivateAskRequest request);
```

模型调用使用：

```java
AiModel model = modelService.getDefaultEnabledModel(ModelService.CAPABILITY_CHAT);
String content = chatModelService.getChatClient(model.getModelCode())
        .prompt()
        .system(systemPrompt)
        .user(objectMapper.writeValueAsString(safeInput))
        .call()
        .content();
```

使用 Jackson 严格解析为：

```java
record CoordinatorResult(String summary, List<String> risks,
                         List<String> questions, List<GeneratedTaskDraft> taskDrafts) {}
record PrivateAskResult(String answer, List<PrivateAskSource> sources,
                        List<String> limitations) {}
```

system prompt 明确 “只读、不得声称已执行、只返回 JSON”。任何缺字段、超限或非法 task draft 均整体失败，不部分接受。

- [ ] **步骤 6：运行测试并提交**

```bash
cd backend
mvn -Dtest=ProjectWorkspaceContextServiceTest,ProjectCollaborationModelServiceTest test
```

提交：

```bash
git add backend/src/main/java/com/aipal/service/ProjectWorkspaceContextService.java backend/src/main/java/com/aipal/service/ProjectCollaborationModelService.java backend/src/main/java/com/aipal/dto/ProjectCollaborationDtos.java backend/src/test/java/com/aipal/service/ProjectWorkspaceContextServiceTest.java backend/src/test/java/com/aipal/service/ProjectCollaborationModelServiceTest.java
git commit -m "feat: add safe project collaboration model gateway"
```

---

## 任务 4：共享讨论、Coordinator 与任务草稿确认闭环

**文件：**

- 新建：`backend/src/main/java/com/aipal/service/ProjectDiscussionService.java`
- 新建：`backend/src/main/java/com/aipal/service/ProjectTaskDraftService.java`
- 修改：`backend/src/main/java/com/aipal/controller/ProjectCollaborationController.java`
- 修改：`backend/src/main/java/com/aipal/dto/ProjectCollaborationDtos.java`
- 新建：`backend/src/test/java/com/aipal/service/ProjectDiscussionServiceTest.java`
- 新建：`backend/src/test/java/com/aipal/service/ProjectTaskDraftServiceTest.java`

- [ ] **步骤 1：写共享消息和 Coordinator 幂等测试**

覆盖：

- POST 用户消息持久化 `sender_user_id/sender_name`；
- 重复 `idempotencyKey` 只产生一条消息；
- Coordinator 先持久化一条 `message_type=COORDINATOR` 的摘要消息；
- 每个合法 `taskDraft` 持久化为 DRAFT；
- 模型调用失败时不写 Coordinator 消息和草稿；
- 普通 view 用户不能运行 Coordinator。

- [ ] **步骤 2：写草稿生命周期测试**

覆盖：

- 列表只返回当前租户、当前项目；
- 手工创建和 Coordinator 创建均为 DRAFT；
- cancel 只允许 DRAFT；
- confirm 用当前项目键创建 `AgentRunService.CreateRunCommand`；
- confirm 成功写 `confirmed_run_id`、状态 CONFIRMED，并按建议 assignee 建立 `TASK_DRAFT` 与 `AGENT_RUN` 两个可追溯指派；
- confirm 重试返回同一 Run；
- 无 agentId 时返回 400；
- 并发版本不匹配返回 409。

- [ ] **步骤 3：运行测试并确认失败**

```bash
cd backend
mvn -Dtest=ProjectDiscussionServiceTest,ProjectTaskDraftServiceTest test
```

- [ ] **步骤 4：实现共享讨论服务**

接口：

```java
List<DiscussionMessageView> list(String projectKey, int limit);
DiscussionMessageView post(String projectKey, PostDiscussionRequest request);
CoordinatorResponse coordinate(String projectKey, CoordinatorRequest request);
```

内容去首尾空格后长度限制 1–4000；列表 `limit` 限制为 1–50。Coordinator 的事务边界必须覆盖消息和草稿写入，但模型调用在写事务前完成。

- [ ] **步骤 5：实现任务草稿服务**

接口：

```java
List<TaskDraftView> list(String projectKey);
TaskDraftView create(String projectKey, CreateTaskDraftRequest request);
TaskDraftView cancel(String projectKey, Long draftId, VersionedActionRequest request);
ConfirmTaskDraftResult confirm(String projectKey, Long draftId, ConfirmTaskDraftRequest request);
```

`ConfirmTaskDraftRequest` 包含 `agentId`、可选 `assigneeUserId`、`expectedVersion`、`idempotencyKey`。Run 的 `businessType` 固定为 `PROJECT_TASK_DRAFT`，`businessId` 为 draft ID 字符串，input 只包含标题、描述和来源，不包含模型原始响应。

- [ ] **步骤 6：增加 API**

```text
GET  /{projectKey}/discussions                         view
POST /{projectKey}/discussions                         view
POST /{projectKey}/discussion-coordinator              execute
GET  /{projectKey}/task-drafts                         view
POST /{projectKey}/task-drafts                         execute
POST /{projectKey}/task-drafts/{id}/confirm            execute
POST /{projectKey}/task-drafts/{id}/cancel             execute
```

- [ ] **步骤 7：运行测试并提交**

```bash
cd backend
mvn -Dtest=ProjectDiscussionServiceTest,ProjectTaskDraftServiceTest,ProjectCollaborationControllerPermissionTest test
```

提交：

```bash
git add backend/src/main/java/com/aipal/service/ProjectDiscussionService.java backend/src/main/java/com/aipal/service/ProjectTaskDraftService.java backend/src/main/java/com/aipal/controller/ProjectCollaborationController.java backend/src/main/java/com/aipal/dto/ProjectCollaborationDtos.java backend/src/test/java/com/aipal/service/ProjectDiscussionServiceTest.java backend/src/test/java/com/aipal/service/ProjectTaskDraftServiceTest.java backend/src/test/java/com/aipal/controller/ProjectCollaborationControllerPermissionTest.java
git commit -m "feat: add project discussions and task draft workflow"
```

---

## 任务 5：Private Ask 非持久化问答与显式转草稿

**文件：**

- 新建：`backend/src/main/java/com/aipal/service/ProjectPrivateAskService.java`
- 修改：`backend/src/main/java/com/aipal/controller/ProjectCollaborationController.java`
- 修改：`backend/src/main/java/com/aipal/dto/ProjectCollaborationDtos.java`
- 新建：`backend/src/test/java/com/aipal/service/ProjectPrivateAskServiceTest.java`

- [ ] **步骤 1：先写非持久化边界测试**

覆盖：

- `ask` 调用模型网关但不调用 discussion、draft、memory 或 audit 内容持久化 Mapper；
- 只允许最多 20 个当前页面 turn，每条 1–4000 字；
- 请求历史中的 `PRIVATE` 内容不进入 `ProjectWorkspaceContextService`；
- 响应只含 `answer/sources/limitations`；
- 模型失败返回 502 且无任何写入。

- [ ] **步骤 2：写显式转草稿测试**

`convertToDraft` 只有用户显式提交选中的问答内容后才调用 `ProjectTaskDraftService.create`，sourceType 固定为 `PRIVATE_ASK_CONVERSION`，且只保存用户选择的摘要，不保存完整会话。

- [ ] **步骤 3：运行测试并确认失败**

```bash
cd backend
mvn -Dtest=ProjectPrivateAskServiceTest test
```

- [ ] **步骤 4：实现服务与 API**

接口：

```java
PrivateAskResult ask(String projectKey, PrivateAskRequest request);
TaskDraftView convertToDraft(String projectKey, ConvertPrivateAskRequest request);
```

API：

```text
POST /{projectKey}/private-ask                         view
POST /{projectKey}/private-ask/task-drafts             execute
```

`PrivateAskRequest` 由 `question` 和前端当前页面 `history` 组成；服务方法不得加 `@Transactional` 写事务。

- [ ] **步骤 5：运行测试并提交**

```bash
cd backend
mvn -Dtest=ProjectPrivateAskServiceTest,ProjectCollaborationControllerPermissionTest test
```

提交：

```bash
git add backend/src/main/java/com/aipal/service/ProjectPrivateAskService.java backend/src/main/java/com/aipal/controller/ProjectCollaborationController.java backend/src/main/java/com/aipal/dto/ProjectCollaborationDtos.java backend/src/test/java/com/aipal/service/ProjectPrivateAskServiceTest.java backend/src/test/java/com/aipal/controller/ProjectCollaborationControllerPermissionTest.java
git commit -m "feat: add private project ask flow"
```

---

## 任务 6：Project War Room 前端协作工作台

**文件：**

- 新建：`front/src/components/project/ProjectOverviewPanel.vue`
- 新建：`front/src/components/project/ProjectDiscussionPanel.vue`
- 新建：`front/src/components/project/ProjectPrivateAskPanel.vue`
- 新建：`front/src/components/project/ProjectAssignmentPanel.vue`
- 新建：`front/src/components/project/ProjectTaskDraftCard.vue`
- 修改：`front/src/views/ProjectWarRoom.vue`
- 修改：`front/src/api/index.js`
- 修改：`front/src/router/index.js`
- 修改：`front/src/views/Home.vue`
- 修改：`front/src/__tests__/BusinessOperations.test.js`

- [ ] **步骤 1：先写前端契约失败测试**

在 `BusinessOperations.test.js` 读取 API、路由和组件源码，断言：

```javascript
assert.ok(apiSource.includes('/discussions'))
assert.ok(apiSource.includes('/discussion-coordinator'))
assert.ok(apiSource.includes('/private-ask'))
assert.ok(apiSource.includes('/task-drafts'))
assert.ok(apiSource.includes('/assignments'))
assert.ok(apiSource.includes('/my-assignments'))
assert.ok(routerSource.includes("permission: 'project-workspace:view'"))
```

并断言 Private Ask 组件没有调用共享讨论或记忆写入 API，且存在“转为任务草稿”的显式按钮文本。

- [ ] **步骤 2：运行测试并确认失败**

```bash
cd front
npm run test:business
```

- [ ] **步骤 3：补齐 API 客户端**

新增：

```text
getProjectDiscussions / postProjectDiscussion / runProjectDiscussionCoordinator
askProjectPrivately / convertPrivateAskToTaskDraft
getProjectTaskDrafts / createProjectTaskDraft / confirmProjectTaskDraft / cancelProjectTaskDraft
getProjectAssignments / assignProjectTarget / reassignProjectAssignment
acceptProjectAssignment / completeProjectAssignment / getMyProjectAssignments
```

- [ ] **步骤 4：拆分并接入四个工作区**

`ProjectWarRoom.vue` 保留项目选择和数据编排，展示四个一级页签：

1. 项目概览：现有 summary、pipeline、queue、timeline、artifact、risk、memory；
2. 共享讨论：消息流、发送框、@成员、运行 Coordinator、渲染任务草稿卡；
3. 私密问答：仅页面内 history、来源/限制展示、清空会话、显式转草稿；
4. 我的指派：默认我的指派，可切换项目全部指派；支持接受、完成，以及有 manage 权限时改派。

任务草稿确认弹窗必须选择 Agent，并可选择项目成员作为 assignee；确认前明确提示“确认后将创建 Team Agent Run”。

- [ ] **步骤 5：切换路由和菜单权限**

将 `front/src/router/index.js` 和 `Home.vue` 中 `/project-workspaces` 的权限统一改为 `project-workspace:view`。按钮级：

- Coordinator、草稿确认、接受/完成：`project-workspace:execute`；
- 指派/改派：`project-workspace:manage`。

- [ ] **步骤 6：运行前端验证并提交**

```bash
cd front
npm run test:business
npm run build
```

提交：

```bash
git add front/src/components/project/ProjectOverviewPanel.vue front/src/components/project/ProjectDiscussionPanel.vue front/src/components/project/ProjectPrivateAskPanel.vue front/src/components/project/ProjectAssignmentPanel.vue front/src/components/project/ProjectTaskDraftCard.vue front/src/views/ProjectWarRoom.vue front/src/api/index.js front/src/router/index.js front/src/views/Home.vue front/src/__tests__/BusinessOperations.test.js
git commit -m "feat: add project collaboration workspace ui"
```

---

## 任务 7：记忆中心回链、全量验证和交付收口

**文件：**

- 修改：`front/src/views/UserMemoryManagement.vue`
- 修改：`front/src/__tests__/BusinessOperations.test.js`
- 新建：`backend/src/test/java/com/aipal/service/ProjectCollaborationIsolationTest.java`
- 新建：`docs/superpowers/specs/2026-07-28-v6-project-collaboration-acceptance.md`

- [ ] **步骤 1：写跨租户和隐私集成测试**

`ProjectCollaborationIsolationTest` 以两个租户、同名 projectKey 构造数据，断言讨论、草稿、指派均不会跨租户读取。再断言：

- Private Ask 后四张协作表行数不变；
- Coordinator 安全上下文不含私密问答；
- 非项目成员不能访问讨论、草稿或指派；
- 只读用户不能执行 Coordinator、确认草稿或改派。

- [ ] **步骤 2：增加记忆中心回链**

当 URL 含 `?projectKey=...` 时，`UserMemoryManagement.vue` 顶部展示“返回项目工作台”按钮并跳转：

```javascript
router.push(`/project-workspaces/${encodeURIComponent(route.query.projectKey)}`)
```

Project Overview 的记忆区域进入记忆中心时附带 `projectKey`；不得把 Private Ask 内容写入 URL 或记忆接口。

- [ ] **步骤 3：编写中文验收清单**

验收文档逐条记录：

- 权限矩阵；
- 数据留存矩阵；
- 共享讨论、Coordinator、Private Ask、任务确认、指派状态机；
- 模型不可用和非法响应时的降级表现；
- Mac/Windows 本地启动差异不受本次改动影响；
- 本周期明确不含 RAG、扫描器、Skill 数据源和 Chat Memory。

- [ ] **步骤 4：运行全量验证**

```bash
cd backend
mvn test
cd ../front
npm run test:business
npm run build
```

预期：全部通过，无编译警告升级为错误。

- [ ] **步骤 5：人工冒烟**

以管理员和只读成员各登录一次，验证：

1. 管理员可讨论、运行 Coordinator、确认草稿、指派和改派；
2. 只读成员只能查看与发送共享消息、发起 Private Ask；
3. 刷新页面后共享讨论仍在，Private Ask 清空；
4. 重复点击 Coordinator/确认/改派不会产生重复数据；
5. 模型关闭时页面显示可理解错误且没有半成品草稿；
6. “我的指派”只展示当前用户项目内事项。

- [ ] **步骤 6：提交收口**

```bash
git add front/src/views/UserMemoryManagement.vue front/src/__tests__/BusinessOperations.test.js backend/src/test/java/com/aipal/service/ProjectCollaborationIsolationTest.java docs/superpowers/specs/2026-07-28-v6-project-collaboration-acceptance.md
git commit -m "test: close v6 project collaboration acceptance"
```

---

## 监督与放行规则

每个任务按以下顺序推进：

1. 开发代理领取单一任务并按 TDD 完成；
2. 项目经理代理更新任务台账、排期、依赖和阻塞，不修改业务代码；
3. 产品经理代理对照设计文档检查用户价值、权限、隐私、留存与验收边界；
4. 规格审查通过后进行代码质量审查；
5. 两道审查均通过，主代理验证提交并放行下一任务；
6. 发现问题由原开发代理修复并重新审查，不能把修复混入下一任务。

### 排期台账

| 里程碑 | 任务 | 计划工期 | 前置依赖 | 放行标准 |
|---|---|---:|---|---|
| M1 | 任务 1：表、实体、权限 | 1 天 | 无 | schema 与权限测试通过 |
| M2 | 任务 2：统一指派 | 1 天 | M1 | 指派状态机、事件、权限通过 |
| M3-A | 任务 3：安全上下文与模型网关 | 0.75 天 | M1 | 泄漏测试与严格解析通过 |
| M3-B | 任务 4：讨论与任务草稿 | 0.75 天 | M2、M3-A | 幂等、确认 Run 闭环通过 |
| M3-C | 任务 5：Private Ask | 0.5 天 | M3-A、M3-B | 零持久化边界通过 |
| M4 | 任务 6：协作工作台 UI | 1.5 天 | M2、M3 | 前端契约测试与构建通过 |
| M5 | 任务 7：隔离、回链、验收 | 0.5 天 | M4 | 全量测试和人工冒烟通过 |

计划总工期：6 个开发日。测试执行包含在各任务内，不单列为进度；项目进度以开发任务完成和双重审查放行为准。
