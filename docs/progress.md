# AI中台项目进度跟踪

## 团队成员

| 角色 | 成员 | 职责 |
|------|------|------|
| 项目经理 | project-manager | 把控项目进度，协调各方资源 |
| 产品经理 | product-manager | 需求分析，文档编写，验收 |
| 架构师 | architect | 系统架构设计，CodeReview |
| 后端开发 | backend-dev | 后端功能开发，MCP工具研究 |
| 前端开发 | frontend-dev | 前端页面开发 |
| UI设计 | ui-designer | 页面UI/UX优化设计 |
| 运维 | devops | 部署文档，Git仓库管理 |

## 任务列表

| 任务 | 负责人 | 状态 | 开始时间 | 完成时间 | 备注 |
|------|--------|------|----------|----------|------|
| #1 产品经理：编写AI中台需求文档 | product-manager | completed | 2026-04-13 | 2026-04-13 | ✅ 已完成 |
| #2 架构师：设计数据库架构并输出SQL | architect | completed | 2026-04-13 | 2026-04-13 | ✅ 13张表 |
| #3 后端开发：实现核心功能 | backend-dev | completed | 2026-04-13 | 2026-04-14 | ✅ 已完成 |
| #4 前端开发：实现Vue3管理界面 | frontend-dev | completed | 2026-04-13 | 2026-04-14 | ✅ 已完成 |
| #5 测试：前端功能测试 | - | pending | - | - | 待开始 |
| #6 运维：编写部署文档 | devops | completed | 2026-04-13 | 2026-04-13 | ✅ 已完成 |
| #7 测试：后端接口测试 | - | pending | - | - | 待开始 |
| #8 项目经理：把控项目进度 | project-manager | completed | 2026-04-13 | - | ✅ 已完成 |
| #9 产品经理：验收并编写项目文档 | product-manager | pending | - | - | 待开始 |
| #10 前端：页面样式和功能完善 | - | completed | 2026-04-14 | 2026-04-14 | ✅ 已完成 |
| #11 后端：联调问题修复 | - | completed | 2026-04-14 | 2026-04-14 | ✅ 已完成 |
| #12 UI设计：前端页面整体优化 | ui-designer | completed | 2026-04-14 | 2026-04-14 | ✅ 已完成 |
| #13 后端：研究MCP工具 | backend-dev | in_progress | 2026-04-14 | - | 🔄 进行中 |
| #14 架构师：代码审查 | architect | pending | - | - | 待开始 |
| #15 提交代码到Git仓库 | devops | pending | - | - | CodeReview通过后 |

## v2.0 开发任务

| 任务 | 负责人 | 状态 | 开始时间 | 备注 |
|------|--------|------|----------|------|
| v2.0-1 产品经理：编写v2.0需求文档 | product-manager | in_progress | 2026-04-14 | SpringAI、Agent在线监控、A2A |
| v2.0-2 架构师：系统架构设计 | architect | in_progress | 2026-04-14 | 在线监控、A2A协议设计 |
| v2.0-3 后端开发：SpringAI接入与A2A实现 | backend-dev | pending | 2026-04-14 | 等待架构设计 |
| v2.0-4 UI设计：v2.0界面设计 | ui-designer | pending | 2026-04-14 | 等待功能稳定 |
| v2.0-5 项目经理：制定v2.0开发计划 | project-manager | in_progress | 2026-04-14 | 排期协调 |

## 已完成文件

### 后端 (backend/)
- sql/init.sql - 数据库脚本（无外键约束）
- pom.xml - Maven配置
- src/main/resources/application.yml - 应用配置（密码已修改）
- 实体类: AiAgent, AiAgentVersion, AiModel, MonCallRecord, SysUser, SysRole, SysPermission, BizModule, BizAgentAuth, SysAuditLog, MonApiMetrics
- Mapper: AiAgentMapper, AiAgentVersionMapper, AiModelMapper, MonCallRecordMapper, SysUserMapper, SysRoleMapper, SysAuditLogMapper, BizAgentAuthMapper
- Service: AgentService, AgentVersionService, ModelService, CallRecordService, UserService, BizModuleService, BizAgentAuthService, MonitorService, StatisticsService, AuditLogService, JwtService
- Controller: AgentController, MonitorController, ModelController, AuthController, UserController, BizModuleController, AgentAuthController, StatisticsController
- 配置类: WebConfig, RedisConfig, JwtConfig, SecurityConfig, InterceptorConfig, WebMvcConfig
- 通用类: Result, ApiResponse, PageRequest, PageResponse, PasswordEncoder, TraceContext, BizException, GlobalExceptionHandler
- DTO: LoginRequest, LoginResponse, AgentCreateRequest, AgentUpdateRequest, AgentCallRequest, AgentAuthRequest

### 前端 (front/)
- package.json - NPM配置
- vite.config.js - Vite配置
- index.html - 入口HTML
- src/main.js - Vue入口
- src/App.vue - 根组件
- src/router/index.js - 路由配置（已修复children路径）
- src/api/index.js - API调用封装
- src/views/Login.vue - 登录页（已修复token处理）
- src/views/Home.vue - 首页（含导航菜单）
- src/views/AgentList.vue - Agent管理页
- src/views/Monitor.vue - 监控页
- src/views/ModelList.vue - 模型管理页

### 文档 (docs/)
- requirements.md - 需求文档
- deployment-ubuntu.md - Ubuntu部署文档
- development-standard.md - **新增** 开发规范文档

## 开发日志

### 2026-04-13

- 项目启动
- Task #1 ✅ 产品经理完成需求文档
- Task #2 ✅ 架构师完成数据库设计（13张表）
- Task #3 🔄 后端开发进行中
- Task #4 🔄 前端开发进行中
- Task #6 ✅ 运维完成部署文档
- Task #8 ✅ 项目经理完成进度跟踪

### 2026-04-14

- **前端清理**：删除无用目录（frontend/、views下的空子目录、components、stores等）
- **路由修复**：修复router/index.js中children路径（从/agents改为agents）
- **API修复**：修复Login.vue中使用真实token替代mock-token
- **后端修复**：
  - 修改application.yml数据库密码为jl19951106
  - LoginResponse添加userId字段
  - 删除init.sql中的所有外键约束
  - 修复admin用户密码（SHA256加密）
  - 删除重复的monitor包
  - 删除重复的agent、biz、model、system包（合并到主包结构）
  - 删除重复的实体：Agent.java、User.java、Model.java、CallRecord.java
  - 删除重复的Mapper：AgentMapper.java、UserMapper.java、ModelMapper.java、CallRecordMapper.java
  - 修复WebConfig类名与文件名不一致的问题
  - 修复CallRecordService和MonitorController引用已删除的CallRecord类 → 改为MonCallRecord
  - 修复ModelService引用已删除的Model类 → 改为AiModel
  - 修复ModelController引用已删除的Model类 → 改为AiModel
  - 修复AgentAuthRequest缺少status字段的问题
- **新增文档**：创建development-standard.md开发规范文档（含代码审查规范）
- **UI设计**：完成前端页面整体优化，采用"Neural Command"未来科技风格
  - 全局样式：深空黑底 + 霓虹青色/品红高亮
  - 登录页：动态粒子背景 + 玻璃态卡片 + 发光效果
  - 首页：侧边栏导航 + 用户信息展示 + 实时时间
  - Agent管理：卡片式表格 + 状态徽章 + 动画效果
  - 监控页：统计卡片 + 响应时间色彩编码
  - 模型管理：网格布局卡片 + 模型信息展示

### 2026-04-14 (续)

- **登录导航问题修复**：
  - 调查登录成功但页面不跳转的问题
  - 添加后端CORS配置到SecurityConfig.java（解决跨域请求可能被阻止的问题）
  - 检查Vite代理配置，确保API请求正确转发到后端
  - 检查JWT token生成和验证流程，确认无误

## v2.0 开发计划

### 里程碑规划

| 阶段 | 目标 | 时间 | 负责人 |
|------|------|------|--------|
| M1 | 需求分析和架构设计 | 2026-04-14 ~ 2026-04-15 | product-manager, architect |
| M2 | 核心功能开发（SpringAI接入 + A2A协议） | 2026-04-16 ~ 2026-04-22 | backend-dev |
| M3 | 前端界面开发 | 2026-04-20 ~ 2026-04-24 | ui-designer, frontend-dev |
| M4 | 联调测试和优化 | 2026-04-25 ~ 2026-04-28 | backend-dev, frontend-dev |
| M5 | 部署上线 | 2026-04-29 ~ 2026-04-30 | devops |

### v2.0 任务列表

| 任务 | 负责人 | 状态 | 开始时间 | 完成时间 | 优先级 | 备注 |
|------|--------|------|----------|----------|--------|------|
| #16 产品经理：编写v2.0需求文档 | product-manager | ✅ completed | 2026-04-14 | 2026-04-14 | P0 | docs/requirements-v2.md |
| #17 架构师：v2.0架构设计 | architect | in_progress | 2026-04-14 | - | P0 | 进行中 |
| #18 架构师：SpringAI集成方案设计 | architect | pending | 2026-04-15 | - | P1 | AI模型接入方案 |
| #19 架构师：A2A协议设计方案 | architect | pending | 2026-04-15 | - | P1 | Agent间通信协议 |
| #20 后端开发：SpringAI接入实现 | backend-dev | ✅ completed | 2026-04-14 | 2026-04-14 | P0 | ChatModelService、AgentRegistry |
| #21 后端开发：A2A协议实现 | backend-dev | ✅ completed | 2026-04-14 | 2026-04-14 | P0 | A2AController、A2AMessageService |
| #22 后端开发：MCP工具集成 | backend-dev | pending | 2026-04-20 | - | P1 | 外部工具扩展 |
| #23 UI设计：v2.0界面设计 | ui-designer | pending | 2026-04-20 | - | P2 | 等待功能稳定 |
| #24 前端开发：v2.0页面开发 | frontend-dev | pending | 2026-04-22 | - | P1 | 等待UI设计完成 |
| #25 测试：v2.0功能测试 | - | pending | 2026-04-25 | - | P1 | |
| #26 运维：v2.0部署文档更新 | devops | pending | 2026-04-28 | - | P2 | |

### 任务依赖关系

```
#16 (需求文档) ──┬── #17 (架构设计) ──┬── #18 (SpringAI方案)
                │                    └── #19 (A2A方案)
                │                         │
                │                         ▼
                │                    #20 (SpringAI实现)
                │                         │
                │                         ▼
                │                    #21 (A2A实现)
                │                         │
                ▼                         ▼
           #23 (UI设计) ──────────── #24 (前端开发)
                                        │
                                        ▼
                                   #25 (功能测试)
                                        │
                                        ▼
                                   #26 (部署文档)

#22 (MCP工具) 可并行进行，不在其他关键路径上
```

### 开发优先级说明

- **P0**: 核心功能，必须按时完成
- **P1**: 重要功能，影响主要流程
- **P2**: 优化功能，可延后

## 阻塞问题

暂无阻塞问题。

## 风险提示

- 前后端已联调，但需要启动MySQL和Redis才能完整运行
- 前端页面依赖Element Plus组件库，需要npm install安装依赖
- UI设计已完成，等待架构师CodeReview后提交Git
- 登录成功后页面导航问题正在修复中（已添加CORS配置）
- v2.0需求文档和架构设计是后续开发的基础，需优先完成

## v2.0 开发日志

### 2026-04-14 v2.0启动

**新增功能需求**：
- SpringAI接入：将AI能力接入SpringAI框架
- Agent在线监控：实时监控Agent是否在线，支持心跳检测
- A2A调用：Agent to Agent通信，支持多个Agent协作

### 2026-04-14 (下午)

**里程碑规划**：
- M1: 需求分析和架构设计 (04-14 ~ 04-15)
- M2: 核心功能开发 (04-16 ~ 04-22)
- M3: 前端界面开发 (04-20 ~ 04-24)
- M4: 联调测试和优化 (04-25 ~ 04-28)
- M5: 部署上线 (04-29 ~ 04-30)

**已完成**：
- ✅ product-manager: v2.0需求文档 (docs/requirements-v2.md)
- ✅ architect: v2.0架构设计 (docs/architecture-v2.md)
- ✅ backend-dev: SpringAI接入实现 (ChatModelService, AgentRegistry)
- ✅ backend-dev: A2A协议实现 (A2AController, A2AMessageService)
- ✅ backend-dev: 心跳检测服务 (HeartbeatService, HeartbeatController)
- ✅ backend-dev: 技术设计文档 (docs/technical-design-springai.md)
- ✅ backend-dev: schema-v2.sql (ai_agent_heartbeat表)
- ✅ project-manager: v2.0开发计划制定

**待开始**：
- ⏳ ui-designer: v2.0界面设计 (Agent状态监控、A2A工作流)

**CodeReview修复状态：**
- ✅ backend-dev 已修复全部12个问题
- ✅ architect 复查通过
- ⚠️ CORS配置建议部署前修正为具体域名

**团队分工**：
- product-manager：编写v2.0需求文档 (requirements-v2.md)
- architect：设计系统架构 (architecture-v2.md)
- backend-dev：研究SpringAI并实现A2A
- project-manager：制定开发计划
- ui-designer：设计v2.0界面（待功能稳定）
