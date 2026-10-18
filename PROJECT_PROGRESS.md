# AI Platform 项目进度

## 排期增量（2026-10-18，Asia/Shanghai）

当前节点：**工作流 W1 / 受控画布 PoC 代码收口，目标环境及真实浏览器验收尚未关闭**。工作流 MVP 未完成；v6 M1/M2 已实现待真实验收，**M3 后端代码已合并 master、待真实环境验收**，M4/M5 未完成。W1–W6 与 v6 M1–M5 分开跟踪，不因画布可用而更新为全项目交付完成。2026-10-18 已修复完整回归中的 3 项失败测试，后端全量 452 项恢复为 0 失败、0 错误、3 跳过（Docker 不可用）；W1 的真实环境门禁仍未关闭。

已增量更新 `docs/AGENTSTUDIO-COMPARISON-WORKFLOW-ROADMAP-2026-10-17.md` 文末「2026-10-18 增量排期与未完成任务」，保留原分析与六周基线。新增任务 ID、角色责任、依赖、验收口径及未排期事项的评审日期。本增量排期优先于下方同日及历史快照中冲突的下一步日期与范围。

| 任务节点 | 当前状态 / 未完成项 | 本次建议窗口 |
| --- | --- | --- |
| W1 补证 | 当前代码完整回归已补（452/0/0/3）；新 Linux 双架构镜像/文件 API、真实 MySQL、登录/画布/Runtime 仍待；权限种子策略待核对 | 10/19–10/23，10/23 关闭评审 |
| W2–W3 内部预览 | DSL/变量/布局分离、明确条件分支、不可变发布快照、版本锁定/分权、AgentRun 适配和试运行 | 10/26–11/06 |
| runner 基础 | 独立 runner、强制执行模式、build/test/质量扫描接入、最小容器加固；未迁移的宿主部署/训练入口需生产阻断 | W1 冻结合同/入口清单，10/26–11/06 实现首版 |
| W4–W5 可靠执行 | 持久节点/事件、CAS/租约、恢复/幂等/取消、审批 WAITING、节点 Trace、A2A 租户会话键、限额与回收 | 11/09–11/20；11/06 复核 BE2 容量，不足则顺延后续目标 |
| W6 四节点 MVP | 真实数据库/Runtime/容器、双租户、浏览器/故障/UAT及灰度回滚 | 11/23–11/27，必需门禁不通过则保持受控预览或顺延 |
| v6 M3–M5 | M3-A/B/C/D 后端代码完成，待真实 MySQL/Runtime/模型/浏览器 UAT；M4 完整四区与 M5 多服务/Memory Center 回链未完成 | 新增候选 11/30–12/25；11/20 复核资源，11/27 根据工作流验收确认启动，未锁定人员 |

仍未锁定开发日期：PARALLEL/JOIN、独立受信部署 runner、训练迁移、撤销/布局/视图增强、导入导出/模板/SSE、Code/Browser/循环/子流程等扩展、强沙箱/云沙箱、Windows 文件兼容、旧分支升级（仅在确认已部署时）。详细文档为上述事项补了 10/23、11/06、11/20 或 11/27 评审节点；评审日期不等于开发承诺。真实 Redis 依既有约定暂缓，环境就绪且暂缓解除后补日期，不能以 mock 关闭生产门禁。

资源沿用 1 FE / 2 BE / 1 QA、产品/PM/运维兼职的假设；同一团队默认先工作流 MVP、再 v6，原 W7–W8 可选增强不与 v6 重复占用人员。本轮以 `f1aa167` 为基线；并行开发期间其他 CLI 已提交 `bb0b205`（前端文案清理），本地 `master` 继续领先 `origin/master`。测试修复与进度更新已提交 `781274a`，尚未推送。`docs/` 按仓库现有策略为本地忽略文档，本根进度保留排期摘要；本次不改 Git 忽略策略。旧台账与旧 PRD 的过期日期保留为历史，待 10/23 对账后再确认有效遗留项。

## v6 M3 开发进展（2026-10-18，Asia/Shanghai）

v6 M3 后端在隔离分支 `codex/v6-m3` 开发，基线 `781274a`；2026-10-18 已快进合并到 `master`（`eed086c`），合并前完成 PM/产品两轮评审。当前 M3 提交：

| 提交 | 内容 |
| --- | --- |
| `206e208` | 冻结 M3 DTO 契约（安全上下文、Coordinator、Private Ask、任务草稿）与错误码 |
| `46978d1` | M3-A 安全上下文/严格模型网关；M3-D 草稿生命周期、确认 Run、双指派 |
| `94239c9` | M3-B 共享讨论/Coordinator；M3-C Private Ask；权限与脱敏修复 |

已实现接口（均位于 `/api/v1/project-workspaces`）：

| 能力 | 接口 | 权限 |
| --- | --- | --- |
| 共享讨论 | `GET/POST /{projectKey}/discussions` | view |
| Coordinator | `POST /{projectKey}/discussion-coordinator` | execute |
| Private Ask | `POST /{projectKey}/private-ask` | view |
| 私聊转草稿 | `POST /{projectKey}/private-ask/task-drafts` | execute |
| 草稿列表/创建 | `GET/POST /{projectKey}/task-drafts` | view / execute |
| 草稿确认/取消 | `POST /{projectKey}/task-drafts/{id}/confirm`、`/cancel` | execute |

关键边界：

- Coordinator 与 Private Ask 只读，不创建 Run、不改流水线/审批/任务状态；模型调用在写事务外。
- 只有草稿 `confirm` 才通过现有 `AgentRunService.createOrReuse` 创建/复用 Run（`businessType=PROJECT_TASK_DRAFT`），并在同一事务内完成草稿 CAS、`TASK_DRAFT` 与 `AGENT_RUN` 指派。
- Private Ask 默认零持久化：不写讨论、草稿、记忆、项目事件或审计正文；只有用户显式提交摘要才落 `PRIVATE_ASK` 草稿。
- 讨论消息类型使用 `SUMMARY` / `RISK` / `TASK_SUGGESTION`，不写 `COORDINATOR`；`senderType` 由 `sender_user_id` 派生，不新增表列。
- 安全上下文只投影白名单字段；敏感值（路径、apiKey/password/token、Prompt/工具载荷、堆栈）整体过滤；Coordinator 摘要 JSON 超过 60KB 时拒绝，避免 TEXT 列溢出。
- 复用 M1/M2 四张表，无 DDL 变更；`sourceType` 统一使用 `PRIVATE_ASK`，不使用旧计划中的 `PRIVATE_ASK_CONVERSION`。

验证证据：M3 定向测试在主工作区复跑 **71 项，失败 0、错误 0、跳过 0**；合并前分支完整后端 `mvn -o -B test` 为 **521 项，失败 0、错误 0、跳过 4**（3 项 Docker 集成 + 1 项 MySQL 条件用例），`mvn -o -B -DskipTests package` 通过。合并后主工作区完整测试受并行 CLI 未提交的 Workflow 版本改动影响，为 527 项、1 失败、3 跳过；失败项 `WorkflowControllerContractTest` 与 M3 无关，需由对应工作流改动收口。

未完成/风险：

- 真实 MySQL 迁移/事务回滚/并发、真实 CHAT 模型、真实 Runtime、浏览器/UAT 尚未执行；H2+Mockito 不能替代生产放行证据。
- M3 已快进合并到 `master`，尚未推送；主工作区仍有其他 CLI 未提交的 `ErrorCode`/Workflow 版本改动，本次合并保留了这些改动，未提交、未覆盖。
- `docs/` 中旧计划仍写 `COORDINATOR`、`PRIVATE_ASK_CONVERSION`、`POST /{projectKey}/task-drafts`；实现以本节的 `SUMMARY/RISK/TASK_SUGGESTION`、`PRIVATE_ASK`、`/private-ask/task-drafts` 为准。
- `CoordinatorRequest.focus` 当前未转发模型；`ConfirmTaskDraftRequest.idempotencyKey` 只做长度校验，重放由草稿状态/CAS 与 `confirmedRunId` 保证；指定处理人时仍按既有指派权限要求 manage。
- M4 完整四区工作台与 M5 多服务/Memory Center 回链未开始。

## 当前交付状态（2026-10-18，Asia/Shanghai）

本轮完成 W1 / 工作流 PoC 审查的五项收口：PARALLEL 暂缓门禁、画布缩放落点、多选/键盘位置保存、文件路径检查后的替换竞态、新 run 首次写入。保留本轮前已存在的租户注册缓存、菜单权限与画布改动。2026-10-18 另修复完整回归中的 3 项失败测试，未改动生产逻辑。当前仍是受控 PoC，尚未完成生产安全放行。

| 收口项 | 已实现行为 | 验证证据 |
| --- | --- | --- |
| 节点开放范围 | 面板仅 START / AGENT / CONDITION / END；前端禁用旧 PARALLEL/JOIN 图保存和运行；后端创建、更新、部署、触发、直接启动统一拒绝，历史图仍可读 | 工作流定义/服务/执行/入口回归，以及前端旧图保留和门禁用例通过 |
| 画布位置 | 通过 Vue Flow 视口换算拖入坐标，保留负坐标；批量保存位置变化，覆盖多选拖动和键盘移动 | 新增 12 项画布回归通过，使用实际 Vue Flow API 与组件 setup；未运行真实浏览器拖拽 E2E |
| 文件边界与首次写入 | 初始化阶段安全创建根目录；逐段目录句柄 + openat/mkdirat/NOFOLLOW；读前检查已打开文件类型；写入通过临时文件原子替换，避免晚到的链接截断外部文件 | 新增 10 项底层回归及工具首次写入用例通过，包含根/父目录替换、叶子链接、外部哨兵文件、并发建目录、中文大内容/空内容、FIFO、Docker Java 委派路径 |

验证结果：2026-10-18 `mvn -o -B test` 全量 **452 项，失败 0、错误 0、跳过 3**；跳过项为 `DockerWorkspaceRealIntegrationTest`（Docker daemon 不可用），不计为通过。此前两批互不重复的定向回归共 **85 项，失败 0、错误 0、跳过 0**，继续作为 W1 历史证据。前端业务测试（含新增画布 12 项）和生产构建通过；后端编译通过，`mvn -o -B -DskipTests package` 离线打包成功并确认 JNA 及安全文件实现进入产物；Compose 拓扑/挂载静态校验通过。Redis 使用 mock，未连接真实 Redis；本轮没有新增真实 MySQL、Docker 或浏览器运行证据。

文件接口当前支持 Linux/macOS 的 x86_64/aarch64；其他平台或原生接口不可用时失败关闭。根目录的现存祖先必须由应用管理，工作区内符号链接（包括指向内部的链接）统一拒绝。该实现保护文件 API，不替代命令执行的容器隔离。新增 JNA 5.13.0；为避免未验证的 musl 兼容层，backend runtime 镜像切换为 `eclipse-temurin:21-jre-noble`，保留 UID/GID 1001 和非 root 用户，镜像体积会增加。当前 Docker daemon socket 不存在，**尚未构建/运行新镜像，也未验证 Linux 双架构原生调用、容器权限与健康检查**，不得据此标记容器安全验收完成。

### 2026-10-18 完整回归修复

| 修复项 | 根因 | 处理 |
| --- | --- | --- |
| `AgentToolExecutorTest.allowedToolCallExecutesAndMarksAuditSucceeded` | 测试中的 `RunTracer` Mock 未返回 `Span`；生产代码读取 `span.startTime()` 触发 NPE，异常分支把已执行的工具标为 FAILED | 按真实契约 stub `RunTracer.start()` 返回 `Span`；未改生产逻辑 |
| `AutomationBuildTestExecutionServiceTest.pathOutsideWorkspaceIsBlockedNotPlatformRoot` | 用例使用 Windows 路径 `C:\Windows\System32`；在 macOS/Linux 上该路径会被当作相对路径，解析到项目内而不是越界 | 改用项目根目录之外的跨平台真实路径，验证越界路径确实被拒绝 |
| `AutomationBuildTestExecutionServiceTest.parseFailureOnTestPathIsBlocked` | 同上，路径未越界导致测试命令仍被执行 | 同上 |

修复文件：`backend/src/test/java/com/aipal/service/AgentToolExecutorTest.java`、`backend/src/test/java/com/aipal/service/AutomationBuildTestExecutionServiceTest.java`。修复后全量 `mvn -o -B test` 为 **452 项，失败 0、错误 0、跳过 3**；3 个跳过均为 Docker 集成用例。测试改动与进度更新已提交 `781274a`，尚未推送。

下一步按既定路线图推进：先提交/评审本轮测试修复，并在 10/19–10/23 完成 W1 补证（新 Linux 镜像与文件 API、真实 MySQL、登录/画布/Runtime、权限种子策略）；随后 W2–W3（10/26–11/06）建设 DSL/schemaVersion、变量、布局分离、发布快照和 AgentRun 适配；W4–W5（11/09–11/20）建设持久节点状态、CAS/恢复/审批与独立 runner；W6（11/23–11/27）执行真实 MySQL、双租户、浏览器及重启/重复事件/UAT。PARALLEL/JOIN 必须等 JOIN 屏障和去重门禁通过才重新开放。独立 runner、质量扫描迁移、容器加固、配额/崩溃回收仍为待办。

## 当前交付状态（2026-10-17，Asia/Shanghai）

> 2026-10-17 增补：按 `docs/AGENTSTUDIO-COMPARISON-WORKFLOW-ROADMAP-2026-10-17.md` 启动工作流建设 W1。已完成：注册缓存/心跳键租户隔离、WorkspacePaths 符号链接边界、工作流菜单与 `workflow:manage` 权限种子、图协议/版本/执行状态设计（`docs/WORKFLOW-GRAPH-PROTOCOL-DESIGN-2026-10-17.md`）、runner 整改清单（`docs/RUNNER-REMEDIATION-CHECKLIST-2026-10-17.md`）。验证约定：Redis 相关用例先跳过；MySQL 用本地实例；SQL 脚本文件保持留存。下方台账为 2026-10-09 快照，历史结论不改写。

## 当前交付状态（2026-10-09，Asia/Shanghai）

当前主工作区在 `master` 上推进 v6 项目协作闭环。M1 权限与数据基础、M2 结构化指派和个人待办已实现，本地自动化测试与后端产物构建通过；生产放行仍为 Yellow，真实 MySQL、浏览器 / UAT 与多服务运行验收待执行。M3 / M4 尚未实施完整闭环，尚未达到 v6 完整交付或发布验收。以下状态覆盖后文旧版本记录，旧记录中的环境阻塞、计划日期与“完成”结论仅代表当时快照。

| 里程碑 | 当前主工作区状态 | 剩余放行条件 |
| --- | --- | --- |
| M1：协作表、实体、专属权限 | 已实现四张协作表、SQL / initializer、权限种子与迁移；有效租户用户和 ACTIVE 项目成员边界、H2 初始化 / 权限回归通过 | 真实 MySQL 初始化、权限 / 菜单迁移与目标环境验证 |
| M2：结构化指派、个人待办 | 已实现指派状态机、版本、事件、同事务审计、Team Run 指派和前端待办；H2 + 真实 MyBatis / Spring 事务自动化通过 | 真实 MySQL CAS / 并发 / 回滚、真实 Runtime 联调、浏览器与 UAT |
| M3：安全模型上下文、共享讨论、Coordinator、Private Ask、草稿确认 | 未实现服务闭环；讨论和草稿表仅为数据基础 | 按安全上下文、严格解析、幂等确认、零持久化隐私边界依次实施 |
| M4：完整四区协作工作台 | 已接入 M2 指派面板；完整讨论 / 私密问答 / 草稿 / 指派四区待实现 | M3 接口就绪后完成前端与交互验收 |
| M5：真实多服务联调与最终验收 | 待执行 | MySQL、Redis、Chroma、Agent 等目标环境联调、Memory Center 回链与登录冒烟 |

### 本轮已有证据

- 管理端新增指派 helper 测试 18 / 18 通过，既有 BusinessOperations / ModelCapabilities 测试通过，最新生产构建通过；失败重试保持业务 ID 的产品缺陷已关闭。
- Consumer Front 测试 5 项通过，生产构建通过。
- Compose 渲染配置校验通过；这不代表容器或依赖服务已经运行验收。
- Java 21 / Maven 当前可用。最终完整后端回归 290 项，失败 0、错误 0、跳过 0；其中新增协作 60 项通过，覆盖 H2 MySQL 模式、真实 MyBatis、数据库 CAS / 并发和 Spring 事务回滚。
- 验证分两步完成：`mvn -o verify` 的 290 项测试全部通过后，离线打包因插件缓存不足中止；补齐插件依赖后 `mvn -B -DskipTests verify` 构建成功，生成约 85 MB 的 `backend/target/ai-platform-1.0.0.jar`。第二步跳过已通过的测试，没有重复执行测试。
- 本轮续验关闭了旧测试对专属工作区权限的适配、Spring Boot 测试专用加密配置缺失，以及 RAG 标题 / 表格标题重复分块问题；新增正文与表格内容保留回归通过。
- Team Run 事务证据使用真实外层 service 事务与指派 / 事件 / 审计 Mapper，Runtime 依赖在测试中替换为加入同一事务的数据库插入；这不代表真实 Runtime 端到端已验收。
- 主线原无协作表，本轮完成新建 DDL 与重复初始化实现；权限迁移面向已有主线租户角色。若曾部署独立 worktree 的旧协作 Schema，仍需单独完整迁移，本轮未声称该旧版本升级已完成。
- 本轮未提交或发布。`.worktrees/v6-project-collaboration` 是独立分支资产，含未提交 M2 内容，按只读保留处理，不计入当前主工作区交付。

### 团队分工与下一步

前端、后端、测试、产品与项目经理按角色协作：前后端收口 M1 / M2 实现，测试补齐真实数据库事务与权限边界用例，产品统一验收协议，项目经理维护证据和放行台账。本轮按用户要求不使用其他 skill。

1. 在真实 MySQL 验证初始化 / 迁移、唯一约束、并发更新和审计回滚。
2. 使用真实 Runtime 执行浏览器登录、项目切换和 M1 / M2 UAT，补齐生产放行证据。
3. 继续 M3 安全上下文与讨论 / 草稿闭环，随后推进 M4、M5。

详细台账见 `docs/V6-COLLABORATION-STATUS.md`，验收条件见 `docs/V6-COLLABORATION-ACCEPTANCE.md`。

---

## 历史进度归档

## 项目概述
AI Platform v3.0 升级项目，包含 TTS Agent 开发、前端页面整体改造（科技美学）、UI/UX 设计、PRD 整理。

## 团队成员
| 角色 | 成员 | 任务 | 状态 |
|------|------|------|------|
| 产品经理 | product-manager | PRD整理 | 已完成 |
| 项目经理 | project-manager | 项目进度管理 | 进行中 |
| 后端开发 | backend-dev | TTS Agent | 已完成 |
| 前端开发 | frontend-dev | 页面改造 | 已完成 |
| UI设计 | ui-designer | 科技美学设计 | 已完成 |
| UX设计 | ux-designer | 用户体验设计 | 已完成 |
| 架构师 | architect | 架构评审 | 已完成 |

## 当前进度
### 2026-04-18
- [x] 项目启动，团队组建完成
- [x] 前端页面整体改造 - 已完成（科技美学风格，粒子动画，玻璃态卡片）
- [x] UI设计 - 已完成（输出至 marketDoc/UI_DESIGN_GUIDE.md）
- [x] PRD整理 - 已完成（输出至 PRD.md）
- [x] 架构评审 - 已完成（输出至 marketDoc/ARCHITECTURE_REVIEW.md）
- [x] UX设计 - 已完成（输出至 marketDoc/UX_DESIGN_GUIDE.md）
- [x] TTS Agent 开发 - 已完成（Edge TTS WebSocket集成，REST API，A2A协议支持）

## 风险与问题
暂无

## 下一步计划
1. M4 集成联调准备（2026-05-07）
2. M5 测试修复（2026-05-14）
3. M6 v3.0发布（2026-05-20）

## 关键里程碑
| 里程碑 | 计划日期 |
|--------|----------|
| M1 PRD评审 | 2026-04-18 |
| M2 TTS后端开发 | 2026-04-25 |
| M3 前端改造 | 2026-04-30 |
| M4 集成联调 | 2026-05-07 |
| M5 测试修复 | 2026-05-14 |
| M6 v3.0发布 | 2026-05-20 |

---

## v4.0 数据集测评平台开发状态 (2026-04-24)

### 2026-04-24 更新

**数据集测评平台开发完成**

#### 前端：已完成
- DatasetBenchmark.vue (5步引导界面: 数据集导入 -> 模拟数据生成 -> Agent选择 -> 测评标准 -> 结果可视化)
- components/benchmark/DatasetImport.vue
- components/benchmark/SimDataGenerator.vue
- components/benchmark/AgentSelector.vue
- components/benchmark/BenchmarkEditor.vue
- components/benchmark/ResultVisualization.vue

#### 后端：已完成
- BenchmarkController (11个API端点)
- DatasetController (8个API端点)
- EvaluationController
- DataGeneratorController
- DatasetService, DataGeneratorService, EvaluationService, EvaluationStatisticsService, CriteriaEngineService
- 完整的Entity, Mapper, DTO层

#### 数据库：已完成
- schema-evaluation.sql (ai_dataset, ai_evaluation, ai_evaluation_criteria)

#### 待处理
- [x] Spring AI 依赖问题修复 - 已完成 (2026-04-23)
- [ ] Git 提交
- [ ] 联调测试

### Git 状态摘要

**未提交修改 (modified):**
- backend/pom.xml
- backend/src/main/resources/application.yml
- backend/src/main/java/com/aipal/AiPlatformApplication.java
- backend/src/main/java/com/aipal/service/AgentRegistry.java
- backend/src/main/java/com/aipal/service/ChatModelService.java
- front/src/style.css
- front/src/views/Login.vue
- front/src/views/AgentGraph.vue
- front/src/views/AgentList.vue
- front/src/api/index.js
- front/src/router/index.js

**新增文件 (untracked):**
- TTS相关: 17个文件 (controller, service, entity, mapper, dto, SQL)
- 数据集测评: 20+ 个文件
- 前端: DatasetBenchmark.vue + 5个子组件

---

## v3.0 Patch 1: Agent 动态注册与监控增强 (2026-04-24)

### 团队成员
| 角色 | 成员 | 任务 | 状态 |
|------|------|------|------|
| 产品经理 | product-manager | PRD更新(Task#3) | ✅ 完成 |
| 架构师 | architect | 架构设计(Task#5) | ✅ 完成 |
| 后端开发 | backend-dev | Agent注册/心跳/编排/图谱 | ✅ 完成 |
| 前端开发 | frontend-dev | 图谱页面/编排页面 | ✅ 完成 |
| 测试工程师 | qa | 测试执行 | 🚀 进行中 |
| 项目经理 | project-lead | 进度管理 | 👤 进行中 |

### 里程碑
| 阶段 | 内容 | 状态 | 完成日期 |
|------|------|------|----------|
| M0 | 需求与设计 | ✅ | 2026-04-24 |
| M1 | Agent 动态注册（Push+Pull） | ✅ 今日完成 | - |
| M2 | 心跳与注册联动 | ✅ 今日完成 | - |
| M3 | 图谱数据采集增强 | ✅ 今日完成 | - |
| M4 | 编排配置与触发引擎 | ✅ 今日完成 | - |
| M5 | 单元测试与集成测试 | 🚀 进行中 | - |
| M6 | 产品验收(UAT) | ⏳ 待启动 | - |
| M7 | 发布上线 | ⏳ 待启动 | - |

### 产出文档
- [x] PRD.md 第17章 - v3.0 Patch 1 功能补充
- [x] docs/superpowers/specs/2026-04-24-agent-registry-design.md - 架构设计

### 前端产出（已完成）
- AgentGraph.vue - 图谱页面增强（实时调用链、节点状态、缩放控件、全屏模式）
- WorkflowEditor.vue - 编排编辑器（节点拖拽、触发配置）
- WorkflowList.vue - 执行历史列表
- API扩展：getWorkflows, createWorkflow, triggerWorkflow 等

### 后端产出（已完成）
- [x] AgentRegistryService - 注册/注销/探测
- [x] HeartbeatManagementService - 心跳管理
- [x] AgentEventService - 事件通知
- [x] WorkflowExecutionService - 编排执行
- [x] A2AMessageService 增强 - 图谱数据写入

### 待处理
- [x] Git 提交 - 已完成 (2026-04-25)
- [x] 前端问题修复 - 数据流断裂/ECharts API/空状态 (已完成)
- [x] 单元测试 - 静态代码审查完成 (Maven环境问题)
- [ ] 集成测试 - 等待 Redis 环境 (阻塞中)
- [ ] UAT 验收

---

## v3.0 Patch 1 开发进度更新 (2026-04-26)

### 今日完成

#### 前端修复 ✅
| 任务 | 修复内容 | 提交 |
|------|---------|------|
| Task #5 数据流断裂 | goToStep(step, data) 正确接收子组件数据，步骤验证防止跳跃 | 962c53d |
| Task #6 ECharts API错误 | 移除无效的 lineStyle2/edgeEffect，用 watch 替代 updateAxis | 94bd3ab |
| Task #7 空状态处理 | AgentGraph/DatasetBenchmark 加载中/失败/空数据状态 | fa043b3 |

#### 后端开发 ✅
| 任务 | 完成内容 |
|------|---------|
| Task #2 WorkflowExecutionService | 增强实现，支持 WorkflowStep 类型、A2A 消息触发 |
| Task #3 A2AMessageService 图谱增强 | persistA2ATask/updateA2ATaskStatus 自动写入 ai_a2a_task 表 |

#### 测试准备 ✅
- QA 完成静态代码审查（7个测试类，37个API端点）
- 集成测试用例清单准备完成（P0/P1/P2 优先级）

### 阻塞项
- **Redis 未安装** - 无法启动后端服务进行联调测试
- **Maven 环境问题** - 无法执行单元测试

### Git 提交记录
| 日期 | 提交 | 说明 |
|------|------|------|
| 2026-04-25 | 3986670 | feat: v3.0完善功能 - Agent注册、心跳、TTS、工作流 |
| 2026-04-26 | 962c53d | fix: DatasetBenchmark 数据流断裂 |
| 2026-04-26 | 94bd3ab | fix: AgentGraph ECharts API 错误 |
| 2026-04-26 | fa043b3 | fix: 空状态处理和错误提示 |

### 待处理任务
1. 安装 Redis 或提供连接信息
2. 完成集成测试（37个API端点）
3. UAT 验收
4. M6/M7 发布上线

---

## 自动化流水线 Docker / Jenkins 部署能力进度 (2026-05-12)

### 已完成
- 新增部署配置模块，支持 Docker 与 Jenkins 两种部署方式。
- 新增部署执行记录，用于保存流水线阶段、配置快照、命令日志、退出码、镜像/容器、Jenkins build、健康检查结果。
- 扩展自动化流水线创建参数：`autoDeployEnabled`、`deployProfileId`，默认关闭自动部署。
- 扩展流水线表字段：`auto_deploy_enabled`、`deploy_profile_id`、`deploy_profile_snapshot`，并提供启动时自动补列。
- 代码审核通过后，开启自动部署的流水线会自动执行：
  - `build_compile`
  - `test_execution`
  - `deployment_release`
  - `operations_monitoring`
- 未开启自动部署的流水线保持原有手动/模拟执行逻辑。
- Docker 支持 `docker build` + `docker run`，以及 `docker compose up -d`。
- Jenkins 支持 `buildWithParameters`、crumb 获取、队列轮询和 build 结果轮询。
- 前端新增“部署配置”页面，并在侧边栏加入入口。
- 流水线创建弹窗新增“自动部署”开关和部署配置下拉框。
- 流水线详情新增部署执行记录展示，包括日志、镜像、容器、Jenkins URL、健康检查信息。
- 补充 RAG 入库记录表的兼容补列，避免此前混合切分字段半接入导致旧库 `Unknown column`。

### 验证结果
- 前端：`npm.cmd run build` 已通过。
- 后端：当前机器未安装 Maven/JDK，`mvn.cmd test` 与 `javac` 无法执行。
- 已完成后端静态巡检，修正了部署执行器输出读取、命令超时、Jenkins 轮询 URL、中文字符串风险等问题。

### 当前工作树说明
- `git status` 当前主要显示未跟踪运行/数据目录与启动脚本：
  - `bge-m3-training/`
  - `chroma/`
  - `marketDoc/prd-templates/`
  - `start-all.ps1`
  - `start-services.ps1`
  - `start-services.sh`
- 这些文件/目录与本次自动部署功能不是同一类代码变更，暂未处理。

### 下一步
1. 在具备 Java/Maven 的环境执行 `mvn test`。
2. 启动后端，验证部署配置 CRUD 接口。
3. 创建一条关闭自动部署的流水线，确认现有流程不受影响。
4. 创建一条 Docker 自动部署流水线，验证 build/test/deploy/health 阶段和日志记录。
5. 创建一条 Jenkins 自动部署流水线，验证触发、轮询和结果记录。
6. 根据真实 Dockerfile/Jenkins Job 参数微调部署配置表单默认模板。

---

## 开发进度完整归档 (2026-05-13)

### 归档文件
- `docs/archive/2026-05-13-development-progress-archive.md`

### 归档结论
- 当前功能主体已经覆盖自动化流水线、Skill 管理、RAG 知识库、用户记忆、成本计费、模型训练、Docker/Jenkins 自动部署与首页概况增强。
- 前端生产构建已通过；后端完整测试仍受本机 Java/Maven 环境缺失影响，需在具备环境后执行 `mvn test`。
- 当前阶段建议进入环境联调与回归验证，重点验证 Redis、Chroma、RAG 混合切分、Docker/Jenkins 自动部署和流水线默认关闭自动部署的兼容行为。

---

## P0/P1 修复收口进度（2026-06-14）

### 已完成的代码修复

- 安全与租户：移除生产默认密钥和凭据提示，收紧 CORS 与公开接口，Agent 注册和心跳增加 HMAC 校验；租户上下文改为失败关闭，并补齐异步任务、缓存键和定时任务的租户传播。
- 工作流：补齐 11 个核心 REST 接口、定义校验、Agent/条件/并行执行、重试/超时/取消、定时与事件触发；前端编辑器仅暴露后端真实支持的节点和配置。
- 数据集与模拟生成：补齐真实导入、预览、托管存储、批量删除、SSRF 与路径保护；支持 JSON/JSONL/CSV/XML/XLS/XLSX/TXT、GBK 文本回退和 100-10000 条确定性规则生成。
- 测评：按真实样本调用 Agent，支持批量任务、进度、取消、重试、确定性评分、结果聚合和 JSON/CSV 文件导出；管理端五步流程已与后端契约对齐。
- 发布工程：Compose 增加数据库初始化、数据集持久卷和健康检查；CI 增加后端验证、管理端业务测试、双前端构建、Compose 静态校验和镜像发布门禁。

### 当前验证结果

- 管理端业务测试通过。
- 管理端与 Consumer Front 生产构建通过。
- Compose 静态校验与 Git diff 格式检查通过。
- 后端测试类已补齐，但本机未安装 JDK/Maven，尚未在本机执行；该项保留为 CI 发布门禁，不从流程中跳过。
- Docker 端到端运行依赖具备 Docker、MySQL、Redis 和 Agent 服务的环境，当前完成代码与静态产品验收，运行验收待目标环境执行。

详细证据与剩余环境门禁见 `docs/P0-P1-REMEDIATION-TRACKER.md`。
