# AI Platform 开发进度归档

归档日期：2026-05-13  
归档范围：截至 2026-05-13 的当前工作区功能开发、验证状态、遗留风险与后续建议。

## 总体状态

AI Platform 已从基础 Agent 管理平台扩展为包含自动化流水线、Skill 管理、RAG 知识库、模型训练、成本计费、用户记忆和部署发布能力的一体化平台。当前前端构建可通过，后端完整测试受本机 Java/Maven 环境缺失影响尚未执行。

当前重点成果：
- 自动化流水线具备 PRD 生成、代码生成、Skill 上下文注入、代码产物查看、人工审核、Docker/Jenkins 自动部署配置与执行记录。
- RAG 知识库支持 Chroma v2 入库、集合/文档块读取展示，并已开始引入混合切分字段兼容。
- 成本计费已扩展到用户维度，并把平台内部流水线大模型调用纳入 token 统计。
- 用户记忆模块支持短时记忆、用户隔离、定期压缩与 MySQL 长期存储。
- 模型训练模块具备 BGE-M3 微调配置、数据准备脚本、Dry Run 和训练状态页面。

## 已完成模块

### 1. 自动化流水线

已完成能力：
- 创建自动化流水线，包含产品线、项目、需求、模板、生成范围、输出路径、Skill 选择。
- PRD 自动生成，生成后进入人工审核。
- PRD 审核通过后自动进入代码生成。
- 代码生成产物保存到 `marketDoc/generated-code`，前端支持文件树和代码预览。
- 支持 PRD/代码重新生成。
- 支持用户维度记录流水线发起者。
- 支持把流水线中的大模型调用 token 纳入计费统计。
- 支持把 PRD/代码生成过程写入用户短时记忆。

已完成部署能力：
- 新增部署配置管理，支持 `DOCKER` / `JENKINS`。
- 自动部署默认关闭；未开启时保持原有流程。
- 开启自动部署并选择部署配置后，代码审核通过会自动执行：
  - `build_compile`
  - `test_execution`
  - `deployment_release`
  - `operations_monitoring`
- Docker 支持 `docker build` + `docker run`，以及 `docker compose up -d`。
- Jenkins 支持 `buildWithParameters`、crumb 获取、队列轮询和 build 结果轮询。
- 部署执行记录保存命令日志、退出码、镜像名、容器名、Jenkins build、健康检查结果。
- 前端新增“部署配置”页面，流水线创建弹窗新增自动部署开关与部署配置下拉。
- 流水线详情可查看部署执行记录与日志。

主要接口：
- `GET/POST/PUT/DELETE /api/v1/automation/deploy-profiles`
- `GET /api/v1/automation/deploy-profiles/enabled`
- `GET /api/v1/automation/pipelines/{id}/deploy-runs`

数据库：
- `automation_deploy_profile`
- `automation_deploy_run`
- `automation_pipeline.auto_deploy_enabled`
- `automation_pipeline.deploy_profile_id`
- `automation_pipeline.deploy_profile_snapshot`

### 2. Skill 管理

已完成能力：
- 新增 Skill 管理页面和侧边栏入口。
- Skill 支持名称、编码、描述、状态、提示词内容、函数定义元数据。
- 函数定义支持 `name`、`description`、`parametersJson`、`returnSchema`、`javaSnippet`、`enabled`。
- 流水线创建时可选 Skill，非必选。
- 选择 Skill 后保存 Skill 快照，避免后续 Skill 修改影响既有流水线。
- PRD 生成和代码生成阶段注入 Skill 上下文。
- Java 只读取函数元数据用于 prompt 拼接，不动态编译、不执行用户填写的 Java 代码。
- 已修复 Skill API 权限访问问题。

主要接口：
- `GET /api/v1/skills`
- `GET /api/v1/skills/enabled`
- `GET /api/v1/skills/{id}`
- `POST /api/v1/skills`
- `PUT /api/v1/skills/{id}`
- `DELETE /api/v1/skills/{id}`

数据库：
- `ai_skill`
- `automation_pipeline.skill_id`
- `automation_pipeline.skill_snapshot`

### 3. RAG 知识库

已完成能力：
- 文本按固定长度切分并写入 Chroma v2。
- 支持选择 embedding 模型。
- 支持 Chroma 地址配置。
- 支持入库历史展示。
- 支持读取当前 Chroma 集合列表。
- 支持查看 Chroma 当前存储的文档块、metadata、预览内容。
- 修复/增强 Chroma v2 heartbeat 检测和 localhost/127.0.0.1/IPv6 兼容候选。
- 增加 RAG 相关旧库补列，降低 `Unknown column` 风险。

已规划并部分接入：
- `chunkMode`: `FIXED` / `HYBRID`
- `contentType`: `AUTO` / `DOCUMENT` / `CODE`
- `semanticModelId`
- `RagHybridChunker` 已有草稿实现，但主流程尚未完整接入和验证。

当前状态：
- 固定切分入库是主路径。
- 混合切分还需要完成主流程接入、前端控件、模型调用验证和回归测试。

### 4. 用户记忆管理

已完成能力：
- 流水线 PRD/代码生成阶段写入用户短时记忆。
- 使用用户 ID/用户名/userKey 做记忆隔离，避免跨用户记忆混淆。
- 支持 Redis 短时记忆存储。
- 支持压缩短时记忆后写入 MySQL 长期记忆。
- 支持手动压缩、查询压缩记忆、查看短时记忆、清理短时记忆。
- 前端新增用户记忆管理页面。

主要接口：
- `GET /api/v1/user-memories`
- `GET /api/v1/user-memories/short-term`
- `POST /api/v1/user-memories/compress`
- `DELETE /api/v1/user-memories/short-term`

数据库：
- `ai_user_memory`
- `automation_pipeline.initiator_user_id`
- `automation_pipeline.initiator_username`

待验证：
- Redis 可用环境下的 4 小时周期压缩。
- 多用户并发流水线下的记忆隔离。

### 5. 成本计费

已完成能力：
- 成本计费页支持调用次数、Token 消耗、估算成本、成本趋势。
- 计费统计扩展到用户维度。
- 平台内部自动化流水线产生的大模型 token 消耗纳入统计。
- `automation_generation_job` 增加 input/output/total token 字段。
- `mon_call_record` 增加 userId/username 字段。

待验证：
- 不同用户筛选的准确性。
- 流水线 PRD 生成和代码生成 token 入账完整性。
- 导出账单字段是否覆盖用户维度。

### 6. 首页概况与前端体验

已完成能力：
- 首页概况展示优化。
- 24 小时趋势区域缩小。
- 增加流水线概况。
- 增加模型训练概况。
- 使用 fontdesign 方向优化视觉表现。

待验证：
- 移动端和窄屏显示。
- 首页数据接口为空时的兜底展示。

### 7. 模型训练

已完成能力：
- 模型训练页面支持配置基础模型、训练数据、输出目录。
- 支持训练轮次、学习率、Query 长度、Passage 长度、组大小、设备、Dry Run、统一微调。
- BGE-M3 训练脚本目录已存在：`bge-m3-training/`。
- 支持训练数据准备、Dry Run、训练配置快照和指标写入。

当前说明：
- 学习率应使用 `1e-5`、`2e-5`、`5e-6` 这类量级。
- 截图中曾出现 `50000`，该值不合理，应避免。
- 当前样例训练数据很少，仅适合 Dry Run 或流程验证。

待验证：
- 真实 GPU/CUDA 环境训练。
- FlagEmbedding 依赖安装。
- 训练后模型目录可被 embedding 服务加载。

### 8. 服务启动与本地依赖

已完成/已有内容：
- `start-all.ps1`
- `start-services.ps1`
- `start-services.sh`
- Chroma 本地数据目录：`chroma/`
- Embedding 服务目录：`embedding-service/`

当前环境观察：
- Chroma 可通过脚本启动到 9000 端口。
- BGEM3 embedding 服务可通过脚本启动到 8500 端口。
- Redis 当前机器未在 PATH 中，需要手动安装或单独启动。
- Maven/JDK 当前不可用，`mvn.cmd` 和 `javac` 无法执行。

## 验证记录

已通过：
- 前端生产构建：`npm.cmd run build`

未完成：
- 后端：`mvn test`
- 原因：当前机器缺少 Maven/JDK。

需要人工/环境验证：
- Docker 自动部署端到端。
- Jenkins 自动部署端到端。
- Redis 短时记忆与周期压缩。
- Chroma 真实入库和集合文档读取。
- BGE-M3 真实训练。

## 当前工作树状态

截至归档时，`git status` 主要显示：
- 已修改：
  - `PROJECT_PROGRESS.md`
  - `docs/progress.md`
- 未跟踪：
  - `bge-m3-training/`
  - `chroma/`
  - `marketDoc/prd-templates/`
  - `start-all.ps1`
  - `start-services.ps1`
  - `start-services.sh`

说明：
- `chroma/` 更像本地运行数据，应考虑加入 `.gitignore` 或仅保留示例配置。
- `bge-m3-training/` 是训练脚本和样例数据，若要纳入版本管理，应先清理训练输出和大文件。
- `marketDoc/prd-templates/` 可作为模板资产纳入版本管理。
- 启动脚本可纳入版本管理，但需要确认本地路径和端口配置是否通用。

## 主要风险

1. 后端缺少 Maven/JDK 验证，存在编译期问题未被工具捕获的风险。
2. Redis 未安装会影响用户记忆短时存储和周期压缩。
3. Docker/Jenkins 依赖后端服务所在机器的 CLI、网络和权限，必须做环境级联调。
4. RAG 混合切分已部分接入字段和类，但主流程未完成，后续应作为独立任务收口。
5. Chroma 本地数据不应直接作为源码资产长期提交。
6. 前端部分历史文案曾出现编码问题，后续应统一 UTF-8 检查。

## 下一阶段建议

优先级 P0：
1. 安装 Java/Maven，执行 `mvn test`。
2. 启动 MySQL、Redis、后端、前端，做主流程冒烟测试。
3. 验证关闭自动部署的流水线不受影响。
4. 验证 Skill 选择后 PRD/代码生成上下文包含 Skill。
5. 验证 Chroma 当前集合和文档块读取。

优先级 P1：
1. Docker 自动部署端到端联调。
2. Jenkins 自动部署端到端联调。
3. 用户记忆多用户隔离测试。
4. 计费用户维度和流水线 token 统计回归。
5. RAG 混合切分完成主流程接入。

优先级 P2：
1. 模型训练参数提示优化，避免学习率误填。
2. 清理本地运行数据和 `.gitignore`。
3. 整理部署文档和启动脚本说明。
4. 增加自动化部署配置示例。

## 建议归档结论

当前阶段可归档为“功能主体完成，进入环境联调与回归验证阶段”。  
下一步不建议继续堆叠新功能，应优先补齐 Java/Maven/Redis/Docker/Jenkins 环境验证，并把 RAG 混合切分作为一个独立收口任务处理。
