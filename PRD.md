# AI Platform v3.0 产品需求文档 (PRD)

**版本**: v3.0
**日期**: 2026-04-18
**状态**: 初稿

---

## 1. 产品概述

### 1.1 产品愿景
AI Platform v3.0 在 v2.0 的 Agent 管理和 A2A 通信协议基础上，新增三个垂直领域的专业 Agent：图像识别 Agent、市场营销 Agent 和 TTS Agent。同时对前端界面进行科技美学改造，提升用户体验和视觉表现力。

### 1.2 新增 Agent 列表

| Agent 名称 | Agent 类型 | 核心能力 |
|------------|------------|----------|
| 图像识别 Agent | 视觉理解型 | 多格式图像识别、文档内容提取 |
| 市场营销 Agent | 数据分析型 | 销售查询、趋势分析、统计排名 |
| TTS Agent | 语音合成型 | 文本转语音、多音色选择、流式输出 |

---

## 2. TTS Agent (Text-to-Speech Agent)

### 2.1 Agent 基本信息

| 属性 | 值 |
|------|-----|
| Agent ID | `tts-agent` |
| Agent 名称 | TTS Agent |
| Agent 类型 | 语音合成型 (Speech Synthesis) |
| 版本 | 1.0.0 |
| 能力描述 | 将文本转换为自然语音，支持多种音色选择和流式输出 |
| 所属领域 | Speech / Audio Generation |

### 2.2 功能描述

#### 2.2.1 核心功能
- **文本转语音**：将输入的文本内容转换为 WAV/MP3 格式音频
- **多音色支持**：支持中文、英文等多种音色选择
- **流式输出**：支持 SSE 流式传输，实时返回音频数据
- **语速调节**：支持语速参数调整（0.5x - 2.0x）
- **音量调节**：支持音量参数调整（0-100%）

#### 2.2.2 支持的音色

| 音色 ID | 音色名称 | 语言 | 适用场景 |
|---------|----------|------|----------|
| zh-CN-female-1 | 晓晓 | 中文-女声 | 通用场景 |
| zh-CN-male-1 | 云飞 | 中文-男声 | 新闻播报 |
| en-US-female-1 | 晓秋 | 英文-女声 | 英文教学 |
| en-US-male-1 | 云杰 | 英文-男声 | 商务英文 |

### 2.3 用户场景

#### 2.3.1 场景1：Agent 语音播报
**场景描述**：当用户与 Agent 对话时，Agent 可以选择以语音方式回应。

**典型对话**：
```
用户: 请介绍一下市场营销 Agent 的功能
Agent: [文字+语音] "市场营销 Agent 主要提供三大功能：销售数据查询、同比环比分析、统计排名..."
```

#### 2.3.2 场景2：有声内容生成
**场景描述**：将文档、文章等内容转换为语音，方便用户 listening 学习。

**调用流程**：
1. 用户上传文档或输入文本
2. TTS Agent 转换为语音
3. 返回音频流或音频文件

#### 2.3.3 场景3：语音通知
**场景描述**：系统向用户发送语音通知，如任务完成提醒。

### 2.4 业务流程

```
用户/Agent
    │
    ▼
[文本输入] ──► [参数配置：音色/语速/音量]
    │
    ▼
[TTS Agent 处理]
    │
    ├──► [流式输出] ──► [实时播放]
    │
    └──► [完整合成] ──► [音频文件下载]
```

### 2.5 接口需求

#### 2.5.1 REST API 接口

**POST /api/tts/synthesize**

**请求参数**：

| 参数名 | 类型 | 必填 | 说明 | 示例 |
|--------|------|------|------|------|
| text | String | 是 | 要转换的文本（最长5000字） | `"你好，这是测试"` |
| voiceId | String | 否 | 音色ID | `"zh-CN-female-1"` |
| speed | Float | 否 | 语速（0.5-2.0） | `1.0` |
| volume | Integer | 否 | 音量（0-100） | `80` |
| outputFormat | String | 否 | 输出格式：mp3/wav | `"mp3"` |

**请求示例**：
```json
{
  "text": "欢迎使用 AI Platform v3.0，TTS Agent 可以将文本转换为自然语音。",
  "voiceId": "zh-CN-female-1",
  "speed": 1.0,
  "volume": 80,
  "outputFormat": "mp3"
}
```

**响应示例**：
```json
{
  "success": true,
  "taskId": "uuid",
  "data": {
    "audioUrl": "/api/tts/audio/uuid.mp3",
    "duration": 5.2,
    "format": "mp3",
    "voiceId": "zh-CN-female-1"
  },
  "timestamp": "2026-04-18T10:00:00Z"
}
```

**GET /api/tts/audio/{taskId}**

获取合成的音频文件。

**POST /api/tts/stream** (SSE 流式接口)

**请求参数**：
```json
{
  "text": "这是流式测试文本",
  "voiceId": "zh-CN-female-1",
  "speed": 1.0
}
```

**响应**：SSE 流，每个 chunk 为音频数据片段

### 2.6 验收标准

- [ ] 支持中文、英文文本转语音
- [ ] 支持至少 4 种音色选择
- [ ] 支持流式输出（SSE）
- [ ] 支持语速、语速调节
- [ ] 合成延迟 < 3 秒（500字以内）
- [ ] 正确注册到平台并发送心跳
- [ ] 响应 A2A 消息并返回标准格式结果

---

## 3. 前端页面改造需求

### 3.1 登录页面改造

#### 3.1.1 设计目标
打造科技感十足的登录页面，体现 AI 平台的未来感和专业性。

#### 3.1.2 设计要求

**视觉风格**：
- 深色背景（#0a0e27 或类似深蓝渐变）配合霓虹蓝/紫色光效
- 动态粒子或网格背景动画
- 玻璃拟态（Glassmorphism）卡片效果
- 渐变发光按钮

**布局要求**：
- 居中登录卡片，尺寸建议 400x480px
- Logo 和平台名称置于顶部
- 用户名/密码输入框使用圆角透明样式
- 登录按钮使用渐变色 + 发光效果
- 底部版权信息

**动效要求**：
- 页面加载时 Logo 淡入 + 缩放动画
- 输入框 focus 时边框发光效果
- 按钮 hover 时光晕扩散
- 背景粒子持续漂浮动画

#### 3.1.3 验收标准
- [ ] 深色科技风格主题
- [ ] 动态背景效果
- [ ] 玻璃拟态卡片
- [ ] 响应式适配（支持移动端）

### 3.2 仪表盘优化

#### 3.2.1 设计目标
将仪表盘打造为数据驾驶舱，提供直观的全局状态概览。

#### 3.2.2 设计要求

**布局调整**：
- 顶部：快捷操作栏（搜索、新建任务、通知）
- 左侧：导航菜单（收起/展开）
- 主区域：
  - 第一行：统计卡片（Agent 数量、运行任务、在线状态）
  - 第二行：Agent 状态图谱（缩略图）+ 实时动态
  - 第三行：最近任务列表 + 系统公告

**卡片设计**：
- 使用深色半透明背景
- 边框使用 1px 渐变线条
- 数字使用霓虹发光效果
- 图标使用 Element Plus Icons（Cpu、Connection、Monitor 等）

**动效要求**：
- 数字滚动计数动画
- 卡片 hover 时微微上浮 + 光晕
- 状态变化时脉冲动画

#### 3.2.3 验收标准
- [ ] 数据驾驶舱风格
- [ ] 统计卡片动效
- [ ] 实时状态展示
- [ ] 响应式布局

### 3.3 图谱页面增强

#### 3.3.1 设计目标
强化 Agent 调用关系图谱的展示效果，提供更丰富的交互体验。

#### 3.3.2 设计要求

**视觉效果**：
- 节点样式：圆角矩形 + 渐变边框 + 状态指示灯
- 连线样式：带箭头的流动线条，颜色表示调用关系
- 背景：深色网格 + 星云效果
- 缩略图预览：右下角全局缩略图

**交互增强**：
- 节点拖拽布局调整
- 双击节点：展开 Agent 详情
- 右键菜单：快捷操作
- 缩放滑块 + 滑轮缩放
- 全屏模式切换

**信息展示**：
- 节点显示：Agent 名称、状态图标、类型标签
- 悬停显示：Agent ID、在线时长、调用次数
- 实时连线高亮：正在调用的 Agent 之间显示流动动画

#### 3.3.3 验收标准
- [ ] 节点样式美观，区分度高
- [ ] 连线流动动画
- [ ] 交互流畅（拖拽、缩放）
- [ ] 状态实时更新

### 3.4 科技美学风格要求

#### 3.4.1 色彩体系

| 用途 | 颜色 | 说明 |
|------|------|------|
| 主背景 | #0a0e27 | 深蓝黑 |
| 次背景 | #1a1f4e | 深紫蓝 |
| 卡片背景 | rgba(26, 31, 78, 0.8) | 半透明深色 |
| 主色调 | #00d4ff | 霓虹蓝 |
| 强调色 | #7c3aed | 霓虹紫 |
| 成功色 | #10b981 | 翠绿 |
| 警告色 | #f59e0b | 琥珀 |
| 错误色 | #ef4444 | 红色 |
| 文字主色 | #e2e8f0 | 浅灰白 |
| 文字次色 | #94a3b8 | 中灰 |

#### 3.4.2 字体规范
- 主字体：`"PingFang SC", "Microsoft YaHei", sans-serif`
- 数字字体：`"DIN Alternate", "Roboto Mono", monospace`
- 标题字重：600-700
- 正文字重：400-500

#### 3.4.3 动效规范
- 过渡时长：200-300ms
- 缓动函数：`cubic-bezier(0.4, 0, 0.2, 1)`
- 悬停效果：transform + box-shadow
- 加载动画：骨架屏 + 渐变闪烁

#### 3.4.4 玻璃拟态规范
```css
background: rgba(26, 31, 78, 0.6);
backdrop-filter: blur(12px);
border: 1px solid rgba(255, 255, 255, 0.1);
border-radius: 16px;
```

---

## 4. 用户体验优化点

### 4.1 性能优化
- 路由懒加载，首屏加载 < 2 秒
- 列表虚拟滚动，大数据量不卡顿
- 图片/资源懒加载
- 请求合并与缓存

### 4.2 交互优化
- 操作反馈：按钮点击涟漪效果
- 表单验证：实时校验 + 友好提示
- 错误处理：全局错误拦截 + Toast 提示
- 撤销/重做：支持常见操作的撤销

### 4.3 辅助功能
- 键盘导航支持
- 颜色对比度符合 WCAG 2.1 AA 标准
- Focus 状态清晰可见
- 加载状态骨架屏

### 4.4 多语言支持
- 国际化框架：vue-i18n
- 默认中文，支持切换英文

---

## 5. 非功能性需求

### 5.1 性能需求

| 指标 | 要求 |
|------|------|
| 首屏加载时间 | < 2 秒 |
| API 响应时间 | < 500ms |
| 图谱渲染帧率 | ≥ 30 FPS |
| TTS 首包延迟 | < 1 秒 |
| 并发用户数 | ≥ 100 |

### 5.2 安全需求
- JWT Token 认证，有效期 24 小时
- 敏感信息（密码）前端不存储
- HTTPS 全站加密
- SQL 注入防护（参数化查询）
- XSS 防护（内容转义）
- CORS 配置明确域名白名单

### 5.3 兼容性需求

**浏览器支持**：
| 浏览器 | 最低版本 |
|--------|----------|
| Chrome | 90+ |
| Firefox | 88+ |
| Safari | 14+ |
| Edge | 90+ |

**移动端适配**：
- 响应式断点：768px（平板）/ 1024px（桌面）
- 主要功能支持移动端访问

### 5.4 可用性需求
- 系统可用性：99.5%
- 计划内维护窗口：每周日凌晨 2:00-6:00
- 故障恢复时间（MTTR）：< 30 分钟

### 5.5 监控需求
- 前端性能监控（Web Vitals）
- 错误日志收集
- API 调用链路追踪
- Agent 状态监控

---

## 6. 平台集成要求（补充）

### 6.1 TTS Agent 注册配置

```yaml
agent:
  id: tts-agent
  name: TTS Agent
  type: SPEECH_SYNTHESIS
  capabilities:
    - text_to_speech
    - voice_stream
  endpoint: http://localhost:8083/agent/tts
  maxConcurrentTasks: 20
  timeout: 30000
```

### 6.2 数据库表结构（TTS 任务表）

```sql
CREATE TABLE ai_tts_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL UNIQUE,
    text_content TEXT NOT NULL,
    voice_id VARCHAR(32),
    speed FLOAT DEFAULT 1.0,
    volume INT DEFAULT 100,
    output_format VARCHAR(8) DEFAULT 'mp3',
    status VARCHAR(16) DEFAULT 'PENDING',
    audio_url VARCHAR(256),
    duration FLOAT,
    error_message VARCHAR(512),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_task_id (task_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 7. 验收标准汇总

### 7.1 TTS Agent
- [ ] 支持中文、英文文本转语音
- [ ] 支持至少 4 种音色选择
- [ ] 支持流式输出（SSE）
- [ ] 支持语速、音量调节
- [ ] 合成延迟 < 3 秒（500字以内）
- [ ] 正确注册到平台并发送心跳

### 7.2 前端页面改造
- [ ] 登录页：科技风格 + 动态背景 + 玻璃拟态
- [ ] 仪表盘：数据驾驶舱 + 统计卡片动效
- [ ] 图谱页：节点美观 + 连线动画 + 流畅交互
- [ ] 全站：科技美学色彩体系 + 统一动效规范

### 7.3 非功能性
- [ ] 首屏加载 < 2 秒
- [ ] 浏览器兼容性符合要求
- [ ] JWT 认证安全有效
- [ ] 响应式适配移动端

---

## 8. 里程碑计划

| 阶段 | 内容 | 目标日期 |
|------|------|----------|
| M1 | PRD 评审 | 2026-04-18 |
| M2 | TTS Agent 后端开发 | 2026-04-25 |
| M3 | 前端页面改造开发 | 2026-04-30 |
| M4 | 平台集成与联调 | 2026-05-07 |
| M5 | 测试与 Bug 修复 | 2026-05-14 |
| M6 | v3.0 发布 | 2026-05-20 |

---

---

## 9. AI Platform v4.0 产品需求文档 (PRD)

**版本**: v4.0
**日期**: 2026-04-23
**状态**: 初稿

---

### 9.1 产品愿景

AI Platform v4.0 在 v3.0 垂直领域 Agent 基础上，进化为核心平台能力：实现多 Agent 智能协作、可视化工作流编排、高级运营监控以及开放的 Agent 商店生态。目标是让平台从"Agent 管理工具"升级为"Agent 协作平台"。

---

### 9.2 新增功能模块总览

| 模块 | 核心能力 | 优先级 |
|------|----------|--------|
| 多 Agent 协作 | Agent 间任务传递、共享上下文、协同推理 | P0 |
| 工作流编排 | 可视化流程设计器、节点拖拽、条件分支、循环 | P0 |
| 高级监控面板 | 全局运营大盘、实时Metrics、告警中心 | P1 |
| Agent 商店 | 社区共享、自定义 Agent 上架、评分评论 | P2 |

---

## 10. 多 Agent 协作系统

### 10.1 功能概述

多 Agent 协作（Multi-Agent Collaboration）允许平台上的多个 Agent 像团队成员一样协作完成任务，通过 A2A 协议进行任务传递、状态同步和结果汇总。

### 10.2 协作模式

#### 10.2.1 串行协作
**场景**：流水线式处理，每个 Agent 处理完成后传递给下一个。

```
用户请求 → Agent A (处理) → Agent B (处理) → Agent C (处理) → 最终结果
```

**示例**：用户上传一张包含表格的发票图片
1. 图像识别 Agent 提取文字
2. 数据结构化 Agent 解析为 JSON
3. TTS Agent 播报结果

#### 10.2.2 并行协作
**场景**：同一任务分解给多个 Agent 并行处理，结果汇总。

```
用户请求 → [Agent A] ─┐
                ├─► 结果汇总
         [Agent B] ─┘
```

**示例**：用户询问"帮我分析这个月的销售情况"
1. 市场营销 Agent 分析销售数据
2. 图像识别 Agent 生成可视化图表
3. TTS Agent 播报关键结论

#### 10.2.3 条件路由协作
**场景**：根据中间结果动态选择下一个 Agent。

```
用户请求 → Agent A → [条件判断] → Agent B / Agent C → 结果
```

### 10.3 协作上下文管理

| 功能 | 说明 |
|------|------|
| 共享 Session | 协作过程中的上下文数据共享 |
| 消息总线 | 基于 Redis Stream 的事件通知 |
| 结果传递 | 支持结构化数据、文件引用、状态传递 |
| 协作日志 | 完整记录 Agent 间交互链路 |

### 10.4 接口需求

#### 10.4.1 POST /api/collaboration/start

创建协作会话。

**请求参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| collaborationType | String | 是 | 类型：SERIAL/PARALLEL/CONDITIONAL |
| agents | List<AgentInvoke> | 是 | 参与的 Agent 列表及配置 |
| context | Object | 否 | 初始上下文数据 |

**请求示例**：
```json
{
  "collaborationType": "SERIAL",
  "agents": [
    {"agentId": "image-agent", "action": "recognize", "params": {"format": "json"}},
    {"agentId": "tts-agent", "action": "synthesize", "params": {"voiceId": "zh-CN-female-1"}}
  ],
  "context": {"taskId": "task-001"}
}
```

**响应示例**：
```json
{
  "success": true,
  "collaborationId": "col-uuid",
  "status": "RUNNING",
  "createdAt": "2026-04-23T10:00:00Z"
}
```

#### 10.4.2 GET /api/collaboration/{collaborationId}/status

获取协作状态。

**响应示例**：
```json
{
  "collaborationId": "col-uuid",
  "status": "RUNNING",
  "currentStep": 2,
  "totalSteps": 3,
  "results": [
    {"step": 1, "agentId": "image-agent", "status": "COMPLETED", "output": {...}},
    {"step": 2, "agentId": "tts-agent", "status": "RUNNING", "output": null}
  ]
}
```

#### 10.4.3 GET /api/collaboration/{collaborationId}/result

获取协作最终结果。

### 10.5 验收标准

- [ ] 支持串行、并行、条件路由三种协作模式
- [ ] 协作上下文在 Agent 间正确传递
- [ ] 协作状态可实时查询
- [ ] 支持协作取消和超时处理
- [ ] 协作日志完整记录

---

## 11. 工作流编排系统

### 11.1 功能概述

工作流编排（Workflow Orchestration）提供可视化流程设计器，允许用户通过拖拽方式设计复杂的 Agent 协作流程，支持条件分支、循环、并行执行等高级逻辑。

### 11.2 核心概念

| 概念 | 说明 |
|------|------|
| Workflow | 工作流定义，包含节点和连线 |
| Node | 工作流节点，代表一个 Agent 或操作 |
| Edge | 连接线，代表数据流向和执行顺序 |
| Trigger | 触发器，定义工作流启动条件 |
| Variable | 流程变量，存储中间状态 |

### 11.3 节点类型

| 节点类型 | 说明 |
|----------|------|
| Agent Node | 调用指定 Agent |
| Condition Node | 条件分支节点 |
| Loop Node | 循环执行节点 |
| Parallel Node | 并行执行分支 |
| Merge Node | 并行结果合并 |
| Code Node | 自定义脚本处理 |
| Start/End Node | 工作流开始/结束 |

### 11.4 可视化设计器

**功能要求**：
- 拖拽式节点添加
- 连线式数据流向定义
- 双击节点配置参数
- 右键菜单：删除、复制、粘贴
- 缩略图全局预览
- 撤销/重做支持
- 模板市场（预设工作流）

**节点配置**：
- Agent 节点：选择 Agent、配置调用参数
- 条件节点：配置条件表达式（支持 SpEL）
- 循环节点：配置循环次数或终止条件

### 11.5 接口需求

#### 11.5.1 REST API

| 接口 | 方法 | 说明 |
|------|------|------|
| /api/workflow | POST | 创建工作流 |
| /api/workflow | GET | 查询工作流列表 |
| /api/workflow/{id} | GET | 获取工作流详情 |
| /api/workflow/{id} | PUT | 更新工作流 |
| /api/workflow/{id} | DELETE | 删除工作流 |
| /api/workflow/{id}/deploy | POST | 部署工作流 |
| /api/workflow/{id}/run | POST | 手动执行工作流 |

#### 11.5.2 工作流定义结构

```json
{
  "workflowId": "wf-001",
  "name": "发票处理工作流",
  "description": "自动处理发票图片并播报结果",
  "version": 1,
  "nodes": [
    {"id": "start", "type": "START", "position": {"x": 100, "y": 200}},
    {"id": "node1", "type": "AGENT", "agentId": "image-agent", "position": {"x": 300, "y": 200}},
    {"id": "node2", "type": "AGENT", "agentId": "tts-agent", "position": {"x": 500, "y": 200}},
    {"id": "end", "type": "END", "position": {"x": 700, "y": 200}}
  ],
  "edges": [
    {"from": "start", "to": "node1"},
    {"from": "node1", "to": "node2"},
    {"from": "node2", "to": "end"}
  ],
  "variables": [
    {"name": "imageData", "type": "STRING"},
    {"name": "result", "type": "OBJECT"}
  ]
}
```

### 11.6 数据库表结构

```sql
CREATE TABLE ai_workflow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    definition JSON NOT NULL,
    version INT DEFAULT 1,
    status VARCHAR(16) DEFAULT 'DRAFT',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_workflow_id (workflow_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ai_workflow_instance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    instance_id VARCHAR(64) NOT NULL UNIQUE,
    workflow_id VARCHAR(64) NOT NULL,
    status VARCHAR(16) DEFAULT 'RUNNING',
    current_node VARCHAR(64),
    variables JSON,
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP,
    error_message VARCHAR(512),
    INDEX idx_instance_id (instance_id),
    INDEX idx_workflow_id (workflow_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 11.7 验收标准

- [ ] 可视化拖拽设计器，支持节点增删改
- [ ] 支持条件分支和并行执行
- [ ] 工作流版本管理和部署
- [ ] 手动触发和定时触发
- [ ] 执行状态实时追踪
- [ ] 异常中断和恢复机制

---

## 12. 高级监控面板

### 12.1 功能概述

高级监控面板提供全局运营视图，实时展示 Agent 运行状态、性能 Metrics、系统健康度，支持告警规则配置和通知推送。

### 12.2 监控大盘

#### 12.2.1 全局状态视图
- 平台总览：Agent 数量、在线率、正在运行任务数、今日调用量
- 实时大屏：LED 风格数字展示，关键指标一目了然
- 趋势图：24 小时调用量曲线、响应时间分布

#### 12.2.2 Agent 监控
- 单个 Agent 详情：CPU/内存/响应时间/错误率
- 调用链路：请求 → Agent → 依赖 → 结果 全链路追踪
- 历史趋势：支持选择时间范围查看历史数据

#### 12.2.3 任务监控
- 任务列表：实时展示正在执行的任务
- 任务详情：执行步骤、时间消耗、中间结果
- 失败重试：失败任务自动重试及手动重试

### 12.3 告警中心

| 功能 | 说明 |
|------|------|
| 告警规则 | 配置阈值规则（错误率、响应时间、可用性） |
| 告警级别 | P0（紧急）/ P1（重要）/ P2（提示） |
| 通知渠道 | 站内通知、邮件、钉钉/企业微信 webhook |
| 告警历史 | 记录告警触发、确认、恢复全流程 |

#### 12.3.1 预置告警规则

| 规则名称 | 条件 | 级别 |
|----------|------|------|
| Agent 离线 | 连续 3 个心跳周期无响应 | P0 |
| 高错误率 | 5 分钟内错误率 > 10% | P1 |
| 响应超时 | 平均响应时间 > 30s | P1 |
| 任务堆积 | 等待队列 > 100 | P2 |

### 12.4 接口需求

#### 12.4.1 监控数据接口

| 接口 | 方法 | 说明 |
|------|------|------|
| /api/monitor/overview | GET | 获取平台总览数据 |
| /api/monitor/agent/{agentId}/metrics | GET | 获取 Agent Metrics |
| /api/monitor/tasks | GET | 获取任务列表 |
| /api/monitor/alerts | GET | 获取告警列表 |
| /api/monitor/alerts/rules | POST | 创建告警规则 |

#### 12.4.2 监控数据格式

```json
{
  "overview": {
    "totalAgents": 12,
    "onlineAgents": 10,
    "runningTasks": 45,
    "todayCalls": 12580,
    "avgResponseTime": 1.2,
    "errorRate": 0.8
  },
  "agentMetrics": {
    "agentId": "image-agent",
    "status": "ONLINE",
    "uptime": 86400,
    "todayCalls": 1520,
    "avgResponseTime": 0.8,
    "errorRate": 0.2,
    "cpuUsage": 35.5,
    "memoryUsage": 62.3
  }
}
```

### 12.5 验收标准

- [ ] 监控大盘实时数据展示
- [ ] 支持自定义时间范围查询
- [ ] Agent 级别的 Metrics 采集和展示
- [ ] 告警规则配置和触发
- [ ] 告警通知发送（站内 + webhook）
- [ ] 告警历史查询

---

## 13. Agent 商店

### 13.1 功能概述

Agent 商店（Agent Store）提供自定义 Agent 的发布、发现、评价功能，构建平台生态。用户可以上传自开发的 Agent 供其他用户使用。

### 13.2 功能模块

#### 13.2.1 Agent 发布
- 填写 Agent 信息：名称、描述、分类、图标
- 上传 Agent 包：包含 Agent 代码和配置文件
- 配置接口：定义暴露的 API
- 设置定价：免费 / 积分 / 订阅

#### 13.2.2 Agent 发现
- 分类浏览：按功能分类查找
- 搜索过滤：名称、标签、评分
- 排行榜：按调用量、评分排序
- 详情页：功能介绍、使用文档、接口说明

#### 13.2.3 评价系统
- 五星评分 + 文字评论
- 使用统计：调用次数、用户数
- 版本管理：支持多版本共存

### 13.3 接口需求

#### 13.3.1 REST API

| 接口 | 方法 | 说明 |
|------|------|------|
| /api/store/agents | GET | 获取商店 Agent 列表 |
| /api/store/agents/{id} | GET | 获取 Agent 详情 |
| /api/store/agents | POST | 发布 Agent |
| /api/store/agents/{id}/install | POST | 安装 Agent |
| /api/store/agents/{id}/review | POST | 提交评价 |

#### 13.3.2 Agent 详情结构

```json
{
  "agentId": "custom-agent-001",
  "name": "简历筛选 Agent",
  "description": "自动筛选简历中的关键信息",
  "category": "HR",
  "author": "user-001",
  "version": "1.0.0",
  "downloads": 1250,
  "rating": 4.5,
  "reviewCount": 32,
  "price": 0,
  "capabilities": ["resume_parse", "keyinfo_extract"],
  "apiSpec": {...},
  "createdAt": "2026-04-10",
  "updatedAt": "2026-04-20"
}
```

### 13.4 验收标准

- [ ] Agent 信息发布和编辑
- [ ] 商店列表浏览和搜索
- [ ] Agent 安装到本地平台
- [ ] 五星评价和评论
- [ ] 下载量统计和排行榜

---

## 14. v4.0 验收标准汇总

### 14.1 多 Agent 协作
- [ ] 支持串行、并行、条件路由三种模式
- [ ] 协作上下文正确传递
- [ ] 协作状态可查询
- [ ] 支持协作取消和超时

### 14.2 工作流编排
- [ ] 可视化设计器（拖拽、连線）
- [ ] 条件分支和并行执行
- [ ] 版本管理和部署
- [ ] 执行状态追踪

### 14.3 高级监控面板
- [ ] 运营大盘实时展示
- [ ] Agent Metrics 采集
- [ ] 告警规则和通知
- [ ] 告警历史记录

### 14.4 Agent 商店
- [ ] Agent 发布和编辑
- [ ] 商店浏览和搜索
- [ ] Agent 安装
- [ ] 评价系统

---

## 15. v4.0 里程碑计划

| 阶段 | 内容 | 目标日期 |
|------|------|----------|
| M1 | v4.0 PRD 评审 | 2026-04-25 |
| M2 | 多 Agent 协作开发 | 2026-05-10 |
| M3 | 工作流编排开发 | 2026-05-20 |
| M4 | 高级监控面板开发 | 2026-05-30 |
| M5 | Agent 商店开发 | 2026-06-10 |
| M6 | 集成测试与优化 | 2026-06-20 |
| M7 | v4.0 发布 | 2026-06-30 |

## 16. 数据集测评平台

### 16.1 功能概述

数据集测评平台（Datasets Evaluation Platform）提供数据集导入、模拟数据生成、Agent 测评、测评标准管理和结果可视化的一站式解决方案。用户可以快速评估 Agent 在特定数据集上的表现，为 Agent 选型和优化提供数据支撑。

### 16.2 产品愿景

帮助平台用户科学评估 Agent 能力，通过标准化测评流程和可视化结果，量化 Agent 在不同场景下的表现差异，辅助决策优化。

### 16.3 功能模块总览

| 模块 | 核心能力 | 优先级 |
|------|----------|--------|
| 数据集管理 | 支持多格式导入、数据预览、数据集 CRUD | P0 |
| 模拟数据生成 | 基于模板生成测试数据、支持自定义规则 | P1 |
| Agent 测评 | 批量执行、多 Agent 对比、异步处理 | P0 |
| 测评标准 | 预设标准库、自定义标准、权重配置 | P1 |
| 结果展示 | 仪表盘、可视化图表、报告导出 | P0 |

---

### 16.4 数据集管理

#### 16.4.1 数据集导入

**支持格式**：

| 格式 | 说明 | 最大文件 |
|------|------|----------|
| CSV | 逗号分隔，UTF-8 编码 | 100MB |
| JSON | 单行或多行 JSONL | 50MB |
| Excel | .xlsx/.xls，首行表头 | 50MB |
| TXT | 每行一条记录 | 50MB |
| XML | 解析为结构化数据 | 50MB |

**导入方式**：
- 本地上传：拖拽或点击选择文件
- URL 导入：输入文件 URL 自动下载
- API 导入：POST /api/datasets/import 批量导入

**数据验证**：
- 格式校验：文件扩展名和内容格式匹配
- 编码检测：自动检测 UTF-8/GBK 编码
- 字段校验：必填字段非空、数据类型正确

**数据预览**：
- 表格形式展示前 100 条
- 支持列筛选和排序
- 字段类型自动识别（文本/数值/日期）

#### 16.4.2 数据集列表

| 功能 | 说明 |
|------|------|
| 列表展示 | 名称、数据量、格式、创建时间、状态 |
| 搜索 | 按名称模糊搜索 |
| 批量操作 | 批量删除、批量导出 |
| 详情 | 点击查看数据集详情和样本数据 |

#### 16.4.3 接口需求

**POST /api/datasets**
创建数据集。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | String | 是 | 数据集名称 |
| description | String | 否 | 数据集描述 |
| format | String | 是 | 格式：CSV/JSON/JSONL/EXCEL/TXT/XML |
| sourceType | String | 是 | 来源：UPLOAD/URL/API |
| fileUrl | String | 否 | 当 sourceType=URL 时必填 |

**GET /api/datasets**
获取数据集列表。

**GET /api/datasets/{id}**
获取数据集详情。

**DELETE /api/datasets/{id}**
删除数据集。

**GET /api/datasets/{id}/preview**
获取数据集预览（前 100 条）。

---

### 16.5 模拟数据生成

#### 16.5.1 生成模板

| 模板类型 | 说明 | 适用场景 |
|----------|------|----------|
| 结构化数据 | 模拟数据库表数据 | 通用测试 |
| 对话数据 | 用户-Agent 对话格式 | 对话类 Agent 测试 |
| 图像标注 | 图像路径+标注信息 | 图像识别 Agent |
| 销售数据 | 模拟销售记录 | 市场营销 Agent |
| 语音文本 | 文本+音频对应 | TTS Agent |

#### 16.5.2 生成配置

**基础参数**：
| 参数 | 说明 | 示例 |
|------|------|------|
| 数据量 | 生成记录数量 | 1000 |
| 字段数 | 每条记录字段数 | 10 |
| 语言 | 中文/英文/混合 | zh-CN |

**字段规则**：
| 字段类型 | 生成方式 | 示例 |
|----------|----------|------|
| 姓名 | 随机姓名库 | 张三 |
| 邮箱 | 格式化生成 | user001@example.com |
| 手机号 | 符合格式的随机 | 13812345678 |
| 日期 | 范围随机 | 2026-01-01 ~ 2026-04-23 |
| 金额 | 范围随机 | 10.00 ~ 10000.00 |
| 枚举 | 指定选项 | 状态：["进行中","已完成"] |
| 文本 | Lorem Ipsum | 随机段落 |

#### 16.5.3 接口需求

**POST /api/datasets/generate**

```json
{
  "name": "模拟销售数据集",
  "template": "SALES_DATA",
  "count": 500,
  "locale": "zh-CN",
  "fields": [
    {"name": "productName", "type": "TEXT", "minLength": 5, "maxLength": 20},
    {"name": "price", "type": "DECIMAL", "min": 10, "max": 10000},
    {"name": "quantity", "type": "INTEGER", "min": 1, "max": 100},
    {"name": "saleDate", "type": "DATE", "start": "2026-01-01", "end": "2026-04-23"}
  ]
}
```

**响应**：
```json
{
  "success": true,
  "datasetId": "ds-generated-001",
  "recordCount": 500,
  "createdAt": "2026-04-23T10:00:00Z"
}
```

---

### 16.6 Agent 测评

#### 16.6.1 测评流程

```
数据集 → 样本选择 → Agent 配置 → 批量执行 → 结果收集 → 评分汇总
```

#### 16.6.2 Agent 选择

平台现有 Agent 可供测评：

| Agent ID | Agent 名称 | 适用数据类型 |
|---------|-----------|-------------|
| image-agent | 图像识别 Agent | 图像路径+标注 |
| marketing-agent | 市场营销 Agent | 销售数据、统计查询 |
| tts-agent | TTS Agent | 文本转语音 |
| chat-agent | 通用对话 Agent | 对话数据 |

#### 16.6.3 测评配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| sampleSize | 采样数量（0=全量） | 100 |
| timeout | 单次调用超时（秒） | 30 |
| retryCount | 失败重试次数 | 2 |
| parallelTasks | 并行任务数 | 10 |

#### 16.6.4 测评维度

| 维度 | 说明 | 适用 Agent |
|------|------|-----------|
| 准确性 | 输出与预期匹配度 | 全部 |
| 响应时间 | 平均响应延迟 | 全部 |
| 错误率 | 失败请求占比 | 全部 |
| 格式正确率 | 输出格式符合度 | 全部 |
| 完整性 | 输出字段完整度 | 全部 |

#### 16.6.5 接口需求

**POST /api/evaluation/run**

```json
{
  "datasetId": "ds-001",
  "agentId": "marketing-agent",
  "config": {
    "sampleSize": 100,
    "timeout": 30,
    "retryCount": 2,
    "parallelTasks": 10
  },
  "criteria": ["accuracy", "response_time", "error_rate"]
}
```

**响应**：
```json
{
  "success": true,
  "evaluationId": "eval-001",
  "status": "RUNNING",
  "totalSamples": 100,
  "estimatedDuration": 60
}
```

**GET /api/evaluation/{evaluationId}/status**

```json
{
  "evaluationId": "eval-001",
  "status": "RUNNING",
  "completedSamples": 45,
  "totalSamples": 100,
  "currentAgent": "marketing-agent",
  "progressPercent": 45
}
```

**GET /api/evaluation/{evaluationId}/result**

```json
{
  "evaluationId": "eval-001",
  "status": "COMPLETED",
  "summary": {
    "accuracy": 0.92,
    "responseTime": 1.2,
    "errorRate": 0.02,
    "totalSamples": 100
  },
  "details": [
    {"sampleId": 1, "input": {...}, "expected": {...}, "actual": {...}, "score": 0.95},
    {"sampleId": 2, "input": {...}, "expected": {...}, "actual": {...}, "score": 0.88}
  ],
  "createdAt": "2026-04-23T10:00:00Z",
  "completedAt": "2026-04-23T10:02:30Z"
}
```

---

### 16.7 测评标准管理

#### 16.7.1 预设标准库

| 标准名称 | 维度 | 计算方式 | 适用场景 |
|---------|------|----------|----------|
| 准确率 | accuracy | 正确数 / 总数 | 分类、识别类任务 |
| 精确率 | precision | TP / (TP+FP) | 检索、匹配类任务 |
| 召回率 | recall | TP / (TP+FN) | 检索、匹配类任务 |
| F1 分数 | f1 | 2*precision*recall/(precision+recall) | 综合评估 |
| 平均响应时间 | avg_response_time | 总时间 / 请求数 | 性能评估 |
| 超时率 | timeout_rate | 超时数 / 总数 | 稳定性评估 |

#### 16.7.2 自定义标准

用户可创建自定义评分标准：

**标准配置**：
| 配置项 | 说明 |
|--------|------|
| 标准名称 | 唯一标识名称 |
| 计算方式 | 公式或脚本 |
| 权重 | 在综合评分中的占比 |
| 阈值 | 合格/良好/优秀的分界值 |

**自定义公式示例**：
```
score = accuracy * 0.6 + (1 - error_rate) * 0.4
```

#### 16.7.3 接口需求

**GET /api/evaluation/criteria**
获取预设标准列表。

**POST /api/evaluation/criteria**
创建自定义标准。

**PUT /api/evaluation/criteria/{id}**
更新自定义标准。

**DELETE /api/evaluation/criteria/{id}**
删除自定义标准。

---

### 16.8 测评结果展示

#### 16.8.1 仪表盘总览

**核心指标卡片**：
- 综合评分：0-100 分
- 测评次数：累计已完成测评数
- 数据集数量：已导入数据集数
- Agent 数量：已测评 Agent 数

**趋势图表**：
- 测评评分趋势：折线图，展示历史评分变化
- Agent 对比：柱状图，多个 Agent 同一维度对比
- 数据量统计：饼图，数据集类型分布

#### 16.8.2 详细报告

**报告内容**：
| 章节 | 内容 |
|------|------|
| 执行概览 | 数据集信息、Agent 信息、测评时间 |
| 评分总览 | 各维度得分、综合评分 |
| 样本明细 | 每条样本的输入、期望输出、实际输出、评分 |
| 错误分析 | 失败样本列表、错误类型统计 |
| 改进建议 | 基于评测结果的可优化点 |

#### 16.8.3 可视化图表

| 图表类型 | 说明 |
|----------|------|
| 雷达图 | 多维度综合能力展示 |
| 柱状图 | Agent 间对比 |
| 折线图 | 时间趋势 |
| 热力图 | 错误分布分析 |

#### 16.8.4 结果导出

支持导出格式：

| 格式 | 内容 |
|------|------|
| PDF | 完整报告（包含图表） |
| Excel | 数据明细（样本级别） |
| JSON | 原始结果数据 |

**导出接口**：
- GET /api/evaluation/{id}/export?format=PDF
- GET /api/evaluation/{id}/export?format=EXCEL
- GET /api/evaluation/{id}/export?format=JSON

---

### 16.9 数据库表结构

```sql
-- 数据集表
CREATE TABLE ai_dataset (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    format VARCHAR(16) NOT NULL,
    record_count INT DEFAULT 0,
    file_url VARCHAR(256),
    source_type VARCHAR(16) DEFAULT 'UPLOAD',
    status VARCHAR(16) DEFAULT 'READY',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_dataset_id (dataset_id),
    INDEX idx_name (name),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 测评任务表
CREATE TABLE ai_evaluation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    evaluation_id VARCHAR(64) NOT NULL UNIQUE,
    dataset_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    config JSON,
    status VARCHAR(16) DEFAULT 'PENDING',
    total_samples INT DEFAULT 0,
    completed_samples INT DEFAULT 0,
    results JSON,
    summary JSON,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_evaluation_id (evaluation_id),
    INDEX idx_status (status),
    INDEX idx_dataset_id (dataset_id),
    INDEX idx_agent_id (agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 测评标准表
CREATE TABLE ai_evaluation_criteria (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    criteria_id VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(256),
    formula TEXT,
    weight FLOAT DEFAULT 1.0,
    thresholds JSON,
    is_preset BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_criteria_id (criteria_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

### 16.10 验收标准

#### 16.10.1 数据集管理
- [ ] 支持 CSV、JSON、JSONL、Excel、TXT、XML 格式导入
- [ ] 支持本地上传和 URL 导入
- [ ] 数据导入自动校验格式和编码
- [ ] 数据预览展示前 100 条记录
- [ ] 支持数据集搜索、删除、批量操作

#### 16.10.2 模拟数据生成
- [ ] 支持结构化数据、对话数据、销售数据等模板
- [ ] 支持自定义字段规则配置
- [ ] 支持生成 100-10000 条数据

#### 16.10.3 Agent 测评
- [ ] 支持选择平台已有 Agent 进行测评
- [ ] 支持采样数量和并发配置
- [ ] 测评进度实时展示
- [ ] 支持测评取消和重试

#### 16.10.4 测评标准
- [ ] 预设准确率、精确率、召回率、F1 等标准
- [ ] 支持创建自定义评分标准
- [ ] 支持标准权重配置

#### 16.10.5 结果展示
- [ ] 仪表盘展示核心指标和趋势图
- [ ] 详细报告包含样本级别明细
- [ ] 支持雷达图、柱状图、折线图可视化
- [ ] 支持 PDF、Excel、JSON 格式导出

---

### 16.11 里程碑计划

| 阶段 | 内容 | 目标日期 |
|------|------|----------|
| M1 | 数据集管理模块开发 | 2026-05-01 |
| M2 | 模拟数据生成模块开发 | 2026-05-07 |
| M3 | Agent 测评核心功能开发 | 2026-05-15 |
| M4 | 测评标准管理开发 | 2026-05-20 |
| M5 | 结果展示与导出功能 | 2026-05-25 |
| M6 | 集成测试与优化 | 2026-06-01 |
| M7 | 数据集测评平台发布 | 2026-06-05 |

---

**文档作者**: 产品经理
**评审人**: 待定
**版本历史**:
- v1.0 (2026-04-17): 初稿创建，包含图像识别 Agent 和市场营销 Agent
- v1.1 (2026-04-18): 新增 TTS Agent 功能需求和前端页面改造需求
- v2.0 (2026-04-23): 新增多 Agent 协作、工作流编排、高级监控面板、Agent 商店
- v2.1 (2026-04-23): 新增数据集测评平台功能需求

---

## 17. v3.0 Patch 1 功能补充

**版本**: v3.0 Patch 1
**日期**: 2026-04-24
**状态**: 初稿

---

### 17.1 产品愿景

v3.0 Patch 1 在 v3.0 基础上，针对 Agent 管理和图谱展示进行功能增强：实现 Agent 动态注册与注销机制、完善心跳监控联动、丰富图谱交互功能，以及支持手动工作流编排。目标是提升平台的自动化管理和用户体验。

---

### 17.2 Agent 动态注册机制

#### 17.2.1 功能概述

Agent 动态注册机制允许 Agent 在启动时自动注册到平台，支持 Push（主动注册）和 Pull（平台探测）两种模式，实现 Agent 的灵活接入和自动化管理。

#### 17.2.2 Push 模式（主动注册）

**场景**：Agent 启动时主动向平台发送注册请求。

**注册流程**：
```
Agent 启动 → 读取配置 → 发送注册请求 → 平台响应 → 心跳初始化
```

**注册信息规范**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| agentId | String | 是 | Agent 唯一标识 |
| agentName | String | 是 | Agent 显示名称 |
| agentType | String | 是 | Agent 类型：VISION/MARKETING/TTS/CHAT |
| capabilities | List<String> | 是 | 能力列表 |
| endpoint | String | 是 | Agent 服务地址 |
| port | Integer | 否 | 端口号 |
| instanceId | String | 否 | 实例 ID（支持多实例） |
| metadata | Object | 否 | 扩展元数据 |

#### 17.2.3 Pull 模式（平台探测）

**场景**：平台主动探测可用 Agent，适用于服务发现。

**探测机制**：
- 平台维护一个服务地址列表
- 定期发送探测请求（默认 60 秒间隔）
- 探测成功则标记为在线，失败则标记为离线

#### 17.2.4 接口需求

**POST /api/agent/register** - Agent 注册

请求参数：
```json
{
  "agentId": "image-agent",
  "agentName": "图像识别 Agent",
  "agentType": "VISION",
  "capabilities": ["image_recognize", "ocr"],
  "endpoint": "http://192.168.1.100:8081",
  "port": 8081,
  "instanceId": "instance-001",
  "metadata": {
    "version": "1.0.0",
    "tags": ["官方", "内置"]
  }
}
```

响应：
```json
{
  "success": true,
  "agentId": "image-agent",
  "registeredAt": "2026-04-24T10:00:00Z",
  "heartbeatInterval": 30,
  "heartbeatTimeout": 90
}
```

**DELETE /api/agent/{agentId}/unregister** - Agent 注销

**GET /api/agent/{agentId}/info** - 获取 Agent 信息

#### 17.2.5 验收标准

- [ ] Agent 支持 Push 模式主动注册
- [ ] 平台支持 Pull 模式探测 Agent
- [ ] 注册信息格式符合规范
- [ ] 注册响应包含心跳配置
- [ ] 支持多实例注册（不同 instanceId）

---

### 17.3 心跳与注册联动

#### 17.3.1 功能概述

心跳机制与注册联动，实现 Agent 状态的实时监控、自动注销和状态变更通知。

#### 17.3.2 自动监控开启

**机制**：
- Agent 注册成功后，自动开启心跳监控
- Agent 启动时自动发送心跳（默认间隔 30 秒）
- 平台记录 Agent 最后活跃时间

**心跳配置**：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| heartbeatInterval | 30s | 心跳发送间隔 |
| heartbeatTimeout | 90s | 心跳超时时间 |
| maxMissedHeartbeats | 3 | 最大连续超时次数 |

#### 17.3.3 离线自动注销

**触发条件**：
- 连续 3 个心跳周期（90 秒）未收到心跳
- Agent 主动发送注销请求
- 平台探测失败

**处理流程**：
```
检测超时 → 标记为 UNREACHABLE → 等待恢复（30s） → 自动注销 → 通知订阅方
```

#### 17.3.4 状态变更通知

**通知机制**：
- Agent 状态变更时，发布事件到 Redis Stream
- 订阅方（包括图谱前端）可实时接收状态更新

**状态类型**：

| 状态 | 说明 |
|------|------|
| ONLINE | 在线，正常服务 |
| UNREACHABLE | 心跳超时，暂时不可达 |
| OFFLINE | 已离线/已注销 |
| STARTING | 启动中 |

#### 17.3.5 接口需求

**POST /api/agent/heartbeat** - Agent 心跳

```json
{
  "agentId": "image-agent",
  "instanceId": "instance-001",
  "status": "ONLINE",
  "load": 0.65,
  "uptime": 86400
}
```

**GET /api/agent/status** - 获取所有 Agent 状态列表

```json
{
  "agents": [
    {
      "agentId": "image-agent",
      "status": "ONLINE",
      "lastHeartbeat": "2026-04-24T10:00:00Z",
      "uptime": 86400
    }
  ],
  "total": 5,
  "online": 4
}
```

#### 17.3.6 验收标准

- [ ] Agent 注册后自动开启心跳监控
- [ ] 心跳超时自动标记为 UNREACHABLE
- [ ] 连续超时后自动注销
- [ ] 状态变更通知推送到 Redis Stream
- [ ] 前端可实时感知 Agent 状态变化

---

### 17.4 Agent 图谱增强

#### 17.4.1 功能概述

Agent 图谱在 v3.0 基础上增强实时性和交互功能，支持调用链实时展示和节点状态实时更新。

#### 17.4.2 实时调用链展示

**功能说明**：
- 实时展示 Agent 之间的调用关系
- 调用发生时，连线上显示流动动画
- 显示调用方向和调用次数统计

**视觉规范**：

| 元素 | 样式 |
|------|------|
| 调用连线 | 带箭头虚线，颜色根据调用状态变化 |
| 流动动画 | 调用时显示粒子流动效果 |
| 调用次数 | 连线中间显示数字标签 |
| 调用失败 | 红色连线 + 脉冲效果 |

#### 17.4.3 节点状态实时更新

**实时更新机制**：
- WebSocket 长连接推送状态变化
- 节点状态变化时，边框颜色闪烁变化
- 状态变更记录显示在节点详情中

**节点状态样式**：

| 状态 | 边框颜色 | 动画效果 |
|------|----------|----------|
| ONLINE | #10b981 (绿色) | 呼吸灯效果 |
| UNREACHABLE | #f59e0b (橙色) | 慢速脉冲 |
| OFFLINE | #6b7280 (灰色) | 无 |
| STARTING | #3b82f6 (蓝色) | 旋转动画 |

#### 17.4.4 交互功能定义

**节点交互**：

| 操作 | 行为 |
|------|------|
| 单击 | 选中节点，高亮显示详情面板 |
| 双击 | 展开 Agent 详情弹窗（能力、配置、统计） |
| 右键 | 显示快捷菜单（查看详情、触发调用、复制配置） |
| 拖拽 | 调整节点在图谱中的位置 |

**图谱交互**：

| 操作 | 行为 |
|------|------|
| 缩放 | 鼠标滚轮缩放，支持滑块精确缩放 |
| 平移 | 鼠标拖拽空白区域移动视图 |
| 全屏 | 全屏模式展示图谱 |
| 截图 | 导出当前图谱为 PNG 图片 |

#### 17.4.5 接口需求

**GET /api/graph/agent-relations** - 获取 Agent 调用关系

```json
{
  "nodes": [
    {"id": "image-agent", "name": "图像识别 Agent", "type": "VISION", "status": "ONLINE"},
    {"id": "tts-agent", "name": "TTS Agent", "type": "TTS", "status": "ONLINE"}
  ],
  "edges": [
    {"from": "image-agent", "to": "tts-agent", "callCount": 156, "lastCall": "2026-04-24T10:00:00Z"}
  ]
}
```

**WebSocket /ws/graph/updates** - 图谱实时更新

推送消息：
```json
{
  "type": "AGENT_STATUS_CHANGE",
  "agentId": "image-agent",
  "oldStatus": "ONLINE",
  "newStatus": "UNREACHABLE",
  "timestamp": "2026-04-24T10:05:00Z"
}
```

#### 17.4.6 验收标准

- [ ] 实时展示 Agent 调用链
- [ ] 调用连线显示流动动画
- [ ] 节点状态实时更新（< 1 秒延迟）
- [ ] 支持节点点击、拖拽、详情查看
- [ ] 支持图谱缩放、平移、全屏
- [ ] WebSocket 推送状态变更

---

### 17.5 手动编排功能

#### 17.5.1 功能概述

手动编排功能允许用户通过简单配置，将多个 Agent 按需组合执行，支持手动触发、定时触发和事件触发三种方式。

#### 17.5.2 编排配置方式

**配置界面**：
- 左侧：Agent 列表（可拖拽）
- 中间：编排画布（节点连线）
- 右侧：节点配置面板

**编排模板**：

| 模板名称 | 说明 |
|----------|------|
| 图像处理流程 | 图像识别 → 文字提取 → TTS 播报 |
| 营销分析流程 | 数据查询 → 趋势分析 → 生成报告 |
| 批量处理流程 | 数据加载 → 批量 Agent 调用 → 结果汇总 |

#### 17.5.3 三种触发方式

**手动触发**：
- 用户在界面上点击"执行"按钮
- 支持参数输入和配置调整
- 实时显示执行进度

**定时触发**：
- 配置 Cron 表达式（支持秒级）
- 支持一次性定时和循环定时
- 定时任务可启用/暂停

**事件触发**：
- 监听 Redis Stream 事件
- 满足条件时自动执行
- 支持条件表达式配置

#### 17.5.4 执行监控

**执行详情**：
- 当前执行节点高亮显示
- 输入/输出参数展示
- 执行时间统计
- 异常信息展示

**执行历史**：
- 记录每次执行的输入、输出、耗时
- 支持重试和取消操作
- 历史记录可导出

#### 17.5.5 接口需求

**POST /api/orchestration** - 创建编排任务

```json
{
  "name": "图像处理流程",
  "description": "图像识别后语音播报",
  "agents": [
    {"agentId": "image-agent", "order": 1, "params": {"format": "json"}},
    {"agentId": "tts-agent", "order": 2, "params": {"voiceId": "zh-CN-female-1"}}
  ],
  "trigger": {
    "type": "MANUAL"
  }
}
```

**POST /api/orchestration/{id}/run** - 手动执行

**POST /api/orchestration/{id}/schedule** - 配置定时执行

```json
{
  "cron": "0 0 9 * * ?",
  "enabled": true,
  "params": {}
}
```

**GET /api/orchestration/{id}/executions** - 获取执行历史

**DELETE /api/orchestration/{id}/cancel** - 取消执行

#### 17.5.6 数据库表结构

```sql
CREATE TABLE ai_orchestration (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    orchestration_id VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    agents JSON NOT NULL,
    trigger_config JSON,
    status VARCHAR(16) DEFAULT 'DRAFT',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_orchestration_id (orchestration_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ai_orchestration_execution (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    execution_id VARCHAR(64) NOT NULL UNIQUE,
    orchestration_id VARCHAR(64) NOT NULL,
    status VARCHAR(16) DEFAULT 'RUNNING',
    current_step INT DEFAULT 0,
    input_params JSON,
    output_result JSON,
    error_message VARCHAR(512),
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP,
    INDEX idx_execution_id (execution_id),
    INDEX idx_orchestration_id (orchestration_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

#### 17.5.7 验收标准

- [ ] 可视化编排界面，支持 Agent 拖拽
- [ ] 支持手动触发执行
- [ ] 支持 Cron 定时触发
- [ ] 支持事件触发（监听 Redis Stream）
- [ ] 执行进度实时展示
- [ ] 执行历史记录和重试
- [ ] 支持执行取消

---

### 17.6 v3.0 Patch 1 验收标准汇总

#### 17.6.1 Agent 动态注册
- [ ] Push 模式主动注册
- [ ] Pull 模式平台探测
- [ ] 注册信息规范完整
- [ ] 多实例注册支持

#### 17.6.2 心跳与注册联动
- [ ] 自动开启心跳监控
- [ ] 超时自动标记 UNREACHABLE
- [ ] 离线自动注销
- [ ] 状态变更通知推送

#### 17.6.3 Agent 图谱增强
- [ ] 实时调用链展示
- [ ] 调用连线流动动画
- [ ] 节点状态实时更新
- [ ] 节点交互功能完整
- [ ] WebSocket 实时推送

#### 17.6.4 手动编排
- [ ] 可视化编排配置
- [ ] 手动/定时/事件三种触发
- [ ] 执行进度监控
- [ ] 执行历史记录

---

### 17.7 v3.0 Patch 1 里程碑计划

| 阶段 | 内容 | 目标日期 |
|------|------|----------|
| M1 | Agent 动态注册机制开发 | 2026-04-28 |
| M2 | 心跳监控与联动开发 | 2026-04-30 |
| M3 | 图谱增强功能开发 | 2026-05-05 |
| M4 | 手动编排功能开发 | 2026-05-10 |
| M5 | 集成测试与优化 | 2026-05-15 |
| M6 | v3.0 Patch 1 发布 | 2026-05-20 |

---

**文档作者**: 产品经理
**评审人**: 待定
**版本历史**:
- v3.0 Patch 1 (2026-04-24): 新增 Agent 动态注册、心跳联动、图谱增强、手动编排功能
