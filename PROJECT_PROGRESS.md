# AI Platform v3.0 项目进度

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
