# Agent 调用关系图谱 - 产品设计文档

| 版本 | 日期       | 作者 | 备注 |
|------|------------|------|------|
| 1.0  | 2026-04-14 | PM  | Agent调用关系图谱PRD |

---

## 1. 产品概述

### 1.1 功能定位

Agent调用关系图谱是A2A（Agent to Agent）协作的可视化入口，以拓扑图形式展示Agent之间的调用关系、实时状态、执行链路，帮助管理员直观理解Agent协作网络，快速定位调用异常。

### 1.2 核心价值

- **可视化呈现**：将复杂的A2A调用关系以图形化方式呈现，降低理解成本
- **实时监控**：实时展示Agent在线状态、调用链路、健康状况
- **故障定位**：快速定位调用瓶颈、异常节点、死链问题
- **协作分析**：分析Agent调用频次、依赖关系，优化协作效率

### 1.3 页面入口

```
首页 / 导航菜单
└── A2A协作
    ├── 调用关系图谱 ← 当前页面
    ├── A2A任务管理
    └── 工作流编排
```

---

## 2. 页面布局设计

### 2.1 整体布局

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│  Header: A2A调用关系图谱                                          [刷新] [导出] │
├─────────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────────────────────────────────────────┐  │
│  │  Filter Panel   │  │                                                     │  │
│  │                 │  │              Graph Canvas                          │  │
│  │  ● 在线状态筛选  │  │                                                     │  │
│  │    ○ 全部        │  │    ┌─────┐         ┌─────┐                        │  │
│  │    ○ 在线        │  │    │Agent│─────────│Agent│                        │  │
│  │    ○ 离线        │  │    │  A  │         │  B  │                        │  │
│  │    ○ 告警        │  │    └─────┘         └─────┘                        │  │
│  │                 │  │         │               │                          │  │
│  │  ● 调用类型筛选  │  │         └───────┬───────┘                        │  │
│  │    □ 同步调用    │  │               ┌─────┐                             │  │
│  │    □ 异步调用    │  │               │Agent│                             │  │
│  │    □ 工作流      │  │               │  C  │                             │  │
│  │                 │  │               └─────┘                             │  │
│  │  ● 时间范围      │  │                                                     │  │
│  │    [近1小时▼]   │  │                                                     │  │
│  │                 │  │                                                     │  │
│  │  ● 统计概览      │  │                                                     │  │
│  │    总Agent: 12   │  │                                                     │  │
│  │    在线: 10      │  │                                                     │  │
│  │    离线: 1       │  │                                                     │  │
│  │    告警: 1       │  │                                                     │  │
│  │                 │  │                                                     │  │
│  └─────────────────┘  └─────────────────────────────────────────────────────┘  │
│  ┌─────────────────────────────────────────────────────────────────────────────┐│
│  │  Detail Panel (可折叠)                                                       ││
│  │  ┌───────────────────────────────────────────────────────────────────────┐ ││
│  │  │ [节点详情] [调用记录] [执行链路]                                         │ ││
│  │  └───────────────────────────────────────────────────────────────────────┘ ││
│  └─────────────────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 区域说明

| 区域 | 宽度 | 说明 |
|------|------|------|
| Filter Panel | 240px | 左侧筛选面板，支持状态、类型、时间筛选 |
| Graph Canvas | 自适应 | 主画布，SVG/Canvas渲染的拓扑图 |
| Detail Panel | 300px，可折叠 | 底部详情面板，展示选中节点的详细信息 |

---

## 3. 图谱交互设计

### 3.1 节点设计

#### 3.1.1 Agent节点

```
┌─────────────────────────┐
│  ┌───┐                  │
│  │ ● │ Agent-A          │  ← 状态指示（绿/灰/红）
│  └───┘ [在线]           │
│  ─────────────────────  │
│  类型: 对话型            │
│  模型: GPT-4o           │
│  负载: 45%              │
│  ─────────────────────  │
│  今日调用: 1,234        │
│  成功率: 99.2%          │
└─────────────────────────┘
```

**节点状态样式：**

| 状态 | 边框颜色 | 背景色 | 动画效果 |
|------|----------|--------|----------|
| 在线 | #52c41a | #f6ffed | 呼吸灯（1.5s周期） |
| 离线 | #d9d9d9 | #f5f5f5 | 无 |
| 告警 | #ff4d4f | #fff2f0 | 闪烁（0.5s周期） |

#### 3.1.2 边（调用关系）

```
实线（同步调用）: ────────────→  蓝色 #1890ff
虚线（异步调用）: - - - - - - -→  紫色 #722ed1
粗线（高频调用）: ══════════════→  蓝色（线宽3px）
```

**边的样式规则：**
- 根据调用频率动态调整线宽（1-5px）
- 调用成功：蓝色
- 调用失败：红色（持续显示5分钟后自动恢复）
- 悬停时显示调用量统计tooltip

### 3.2 交互行为

#### 3.2.1 节点操作

| 操作 | 触发 | 效果 |
|------|------|------|
| 单击选中 | click | 节点高亮（放大1.1倍），右侧详情面板显示该节点信息 |
| 双击 | dblclick | 打开节点详情弹窗（完整信息） |
| 右键菜单 | contextmenu | 快捷菜单：查看详情、查看调用记录、查看下游依赖 |
| 拖拽 | mousedown + move | 节点跟随鼠标移动，边的连接自动更新 |
| 框选 | Ctrl + 拖拽 | 多选节点，用于批量操作 |
| 缩放 | 滚轮 | 以鼠标位置为中心缩放（0.5x - 3x） |
| 平移 | 空格 + 拖拽 | 画布平移 |

#### 3.2.2 边操作

| 操作 | 触发 | 效果 |
|------|------|------|
| 悬停 | mouseover | 高亮整条边，显示tooltip（调用量、成功率、平均耗时） |
| 单击 | click | 选中边，详情面板显示该调用关系的详细记录 |
| 双击 | dblclick | 打开该调用关系的调用记录列表弹窗 |

### 3.3 布局算法

- **默认布局**：Dagre力导向算法，自动布局
- **手动调整**：支持拖拽节点自定义位置，自动保存
- **布局重置**：点击"重置布局"按钮恢复默认布局

### 3.4 动画效果

| 场景 | 动画效果 | 时长 |
|------|----------|------|
| 页面加载 | 节点从中心向外扩散，模拟"爆炸"效果 | 800ms |
| 数据刷新 | 节点状态变化时，边框颜色渐变过渡 | 300ms |
| 新增节点 | 新节点从透明渐变为不透明，轻微弹跳 | 400ms |
| 删除节点 | 节点缩小并渐隐，连接的边同步消失 | 300ms |
| 实时调用 | 调用时边上有光点流动效果（从起点到终点） | 根据调用耗时动态 |

---

## 4. 筛选与统计

### 4.1 筛选功能

| 筛选项 | 类型 | 选项 | 默认值 |
|--------|------|------|--------|
| 状态筛选 | 单选 | 全部 / 在线 / 离线 / 告警 | 全部 |
| 调用类型 | 多选 | 同步调用 / 异步调用 / 工作流 | 全部 |
| 时间范围 | 下拉 | 近15分钟 / 近1小时 / 近24小时 / 近7天 / 自定义 | 近1小时 |
| Agent名称 | 搜索 | 支持模糊搜索 | 空 |

**筛选联动：**
- 筛选条件变化时，图谱自动刷新
- 筛选条件显示在图谱上方标签栏，支持一键清除

### 4.2 统计概览

实时显示当前筛选条件下的统计数据：

| 指标 | 说明 | 刷新频率 |
|------|------|----------|
| Agent总数 | 符合筛选条件的Agent数量 | 5秒 |
| 在线数 | 状态为"在线"的Agent数 | 5秒 |
| 离线数 | 状态为"离线"的Agent数 | 5秒 |
| 告警数 | 状态为"告警"的Agent数 | 5秒 |
| 调用总数 | 筛选时间范围内的A2A调用次数 | 10秒 |
| 调用成功率 | 成功调用 / 总调用 | 10秒 |
| 平均耗时 | 所有调用的平均响应时间 | 10秒 |

---

## 5. 详情面板设计

### 5.1 Tab切换

详情面板支持3个Tab页签：

| Tab | 内容 |
|-----|------|
| 节点详情 | 选中Agent的详细信息 |
| 调用记录 | 选中Agent的A2A调用历史 |
| 执行链路 | 选中工作流/任务的执行路径 |

### 5.2 节点详情Tab

```
┌────────────────────────────────────────┐
│  Agent: Agent-A                        │
├────────────────────────────────────────┤
│  基本信息                               │
│  ├─ Agent ID: agent-001                │
│  ├─ 实例数: 3                           │
│  ├─ 类型: 对话型                         │
│  ├─ 绑定模型: GPT-4o                    │
│  └─ 版本: v1.2.0                        │
│                                        │
│  实时状态                               │
│  ├─ 总体状态: ● 在线                    │
│  ├─ 平均负载: 45%                       │
│  ├─ 队列长度: 12                        │
│  └─ 最后心跳: 2026-04-14 10:00:00      │
│                                        │
│  调用统计（今日）                        │
│  ├─ 发起调用: 1,234 次                  │
│  ├─ 接收调用: 567 次                    │
│  ├─ 成功率: 99.2%                       │
│  └─ 平均耗时: 230ms                      │
│                                        │
│  下游依赖                               │
│  ├─ → Agent-B (同步, 89次/时)           │
│  ├─ → Agent-C (异步, 45次/时)          │
│  └─ → Agent-D (工作流, 12次/时)        │
│                                        │
│  上游调用                               │
│  ├─ ← Agent-X (同步, 200次/时)         │
│  └─ ← Agent-Y (异步, 80次/时)          │
└────────────────────────────────────────┘
```

### 5.3 调用记录Tab

```
┌────────────────────────────────────────┐
│  调用记录          [导出] [筛选] [时间▼] │
├────────────────────────────────────────┤
│  ┌──────────────────────────────────┐  │
│  │ 10:00:23  Agent-A → Agent-B     │  │
│  │           同步调用 │ 成功 │ 230ms │  │
│  │           "查询会议时间"          │  │
│  └──────────────────────────────────┘  │
│  ┌──────────────────────────────────┐  │
│  │ 10:00:15  Agent-A → Agent-C     │  │
│  │           异步调用 │ 成功 │ --    │  │
│  │           "发送邮件通知"          │  │
│  └──────────────────────────────────┘  │
│  ┌──────────────────────────────────┐  │
│  │ 10:00:01  Agent-A → Agent-B     │  │
│  │           同步调用 │ 失败 │ 5000ms│  │
│  │           "查询会议室"            │  │
│  │           [超时]                  │  │
│  └──────────────────────────────────┘  │
│                                        │
│  ... more ...                          │
│                                        │
│  [上一页] [1] [2] [3] ... [下一页]     │
└────────────────────────────────────────┘
```

**记录字段：**
| 字段 | 说明 |
|------|------|
| 时间 | 调用发起时间，精确到秒 |
| 调用方向 | 源Agent → 目标Agent |
| 调用类型 | 同步 / 异步 / 工作流 |
| 状态 | 成功 / 失败 / 超时 / 取消 |
| 耗时 | 同步调用显示耗时，异步调用显示"--" |
| 描述 | 任务描述，截断显示 |
| 详情 | 失败时显示错误原因 |

### 5.4 执行链路Tab

仅在选中工作流节点或A2A任务时显示，展示完整的执行路径：

```
┌────────────────────────────────────────┐
│  工作流: 会议助手流程                    │
│  执行ID: wf-exec-001                   │
├────────────────────────────────────────┤
│  执行状态: ● 成功 (用时: 5.2s)          │
│                                        │
│  执行链路                               │
│  ┌──────────────────────────────────┐  │
│  │ ① Agent-Meeting                  │  │
│  │    输入: 会议主题、参与者          │  │
│  │    输出: 会议议程                  │  │
│  │    状态: ● 成功 (1.2s)           │  │
│  └──────────────────────────────────┘  │
│               │                         │
│               ▼                         │
│  ┌──────────────────────────────────┐  │
│  │ ② Agent-Calendar                 │  │
│  │    输入: 可用时间段               │  │
│  │    输出: 14:00-16:00 可用         │  │
│  │    状态: ● 成功 (0.8s)           │  │
│  └──────────────────────────────────┘  │
│               │                         │
│               ▼                         │
│  ┌──────────────────────────────────┐  │
│  │ ③ Agent-Mail [并行]              │  │
│  │    输入: 邀请邮件内容             │  │
│  │    输出: 已发送 5 封邮件          │  │
│  │    状态: ● 成功 (3.2s)           │  │
│  └──────────────────────────────────┘  │
└────────────────────────────────────────┘
```

---

## 6. API 接口设计

### 6.1 获取图谱数据

**接口用途：** 获取Agent拓扑图谱的节点和边数据

```
GET /api/v1/a2a/graph
```

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| status | string | 否 | Agent状态筛选：ONLINE/OFFLINE/ALERT |
| callType | string[] | 否 | 调用类型筛选：SYNC/ASYNC/WORKFLOW |
| timeRange | string | 否 | 时间范围：15m/1h/24h/7d |
| keyword | string | 否 | Agent名称模糊搜索 |

**请求示例：**
```
GET /api/v1/a2a/graph?status=ONLINE&callType=SYNC,ASYNC&timeRange=1h&keyword=agent
```

**响应参数：**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| code | int | 状态码：0=成功，非0=失败 |
| message | string | 响应消息 |
| data | object | 图谱数据 |

**data结构：**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| nodes | AgentNode[] | 节点列表 |
| edges | Edge[] | 边列表 |
| stats | GraphStats | 统计信息 |

**AgentNode结构：**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| agentId | string | Agent唯一标识 |
| agentName | string | Agent名称 |
| status | string | 状态：ONLINE/OFFLINE/ALERT |
| agentType | string | Agent类型：DIALOGUE/GENERATION/FUNCTION |
| modelName | string | 绑定的模型名称 |
| load | decimal | 当前负载 0-1 |
| instanceCount | int | 实例数量 |
| todayCallCount | int | 今日调用次数 |
| todaySuccessRate | decimal | 今日成功率 |
| position | Position | 画布坐标（可选） |

**Position结构：**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| x | decimal | X坐标 |
| y | decimal | Y坐标 |

**Edge结构：**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| edgeId | string | 边唯一标识 |
| sourceAgentId | string | 源Agent ID |
| targetAgentId | string | 目标Agent ID |
| callType | string | 调用类型：SYNC/ASYNC/WORKFLOW |
| callCount | int | 调用次数 |
| successRate | decimal | 成功率 |
| avgDuration | int | 平均耗时（毫秒） |
| status | string | 状态：NORMAL/ERROR |

**GraphStats结构：**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| totalAgents | int | Agent总数 |
| onlineAgents | int | 在线数 |
| offlineAgents | int | 离线数 |
| alertAgents | int | 告警数 |
| totalCalls | int | 调用总数 |
| successRate | decimal | 成功率 |
| avgDuration | int | 平均耗时（毫秒） |

**响应示例：**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "nodes": [
      {
        "agentId": "agent-001",
        "agentName": "Agent-A",
        "status": "ONLINE",
        "agentType": "DIALOGUE",
        "modelName": "GPT-4o",
        "load": 0.45,
        "instanceCount": 2,
        "todayCallCount": 1234,
        "todaySuccessRate": 0.992,
        "position": { "x": 100, "y": 200 }
      }
    ],
    "edges": [
      {
        "edgeId": "edge-001",
        "sourceAgentId": "agent-001",
        "targetAgentId": "agent-002",
        "callType": "SYNC",
        "callCount": 567,
        "successRate": 0.998,
        "avgDuration": 230,
        "status": "NORMAL"
      }
    ],
    "stats": {
      "totalAgents": 12,
      "onlineAgents": 10,
      "offlineAgents": 1,
      "alertAgents": 1,
      "totalCalls": 5678,
      "successRate": 0.985,
      "avgDuration": 350
    }
  }
}
```

### 6.2 获取Agent详情

**接口用途：** 获取单个Agent的详细信息

```
GET /api/v1/a2a/graph/agents/{agentId}
```

**路径参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| agentId | string | 是 | Agent唯一标识 |

**响应参数：**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| code | int | 状态码 |
| message | string | 响应消息 |
| data | AgentDetail | Agent详情 |

**AgentDetail结构：**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| agentId | string | Agent ID |
| agentName | string | Agent名称 |
| description | string | Agent描述 |
| status | string | 状态 |
| agentType | string | 类型 |
| modelName | string | 绑定的模型 |
| version | string | 版本号 |
| capabilities | string[] | 能力列表 |
| instances | Instance[] | 实例列表 |
| upstreamAgents | AgentRef[] | 上游调用Agent |
| downstreamAgents | AgentRef[] | 下游依赖Agent |
| todayStats | AgentStats | 今日统计 |

**Instance结构：**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| instanceId | string | 实例ID |
| status | string | 状态 |
| load | decimal | 负载 |
| queueSize | int | 队列长度 |
| lastHeartbeat | datetime | 最后心跳时间 |
| version | string | 实例版本 |

**AgentRef结构：**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| agentId | string | Agent ID |
| agentName | string | Agent名称 |
| callType | string | 调用类型 |
| callCountPerHour | int | 每小时调用次数 |

**AgentStats结构：**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| totalCalls | int | 今日总调用 |
| successCalls | int | 成功调用 |
| failedCalls | int | 失败调用 |
| avgDuration | int | 平均耗时 |
| peakQps | decimal | 峰值QPS |

### 6.3 获取调用记录

**接口用途：** 获取Agent的A2A调用历史记录

```
GET /api/v1/a2a/graph/agents/{agentId}/calls
```

**路径参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| agentId | string | 是 | Agent唯一标识 |

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| page | int | 否 | 页码，默认1 |
| pageSize | int | 否 | 每页条数，默认20 |
| callType | string | 否 | 调用类型：SYNC/ASYNC/WORKFLOW |
| status | string | 否 | 状态：SUCCESS/FAILED/TIMEOUT/CANCELLED |
| startTime | datetime | 否 | 开始时间 |
| endTime | datetime | 否 | 结束时间 |

**响应参数：**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| code | int | 状态码 |
| message | string | 响应消息 |
| data | PageResult | 分页结果 |

**PageResult结构：**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| records | CallRecord[] | 记录列表 |
| total | int | 总条数 |
| page | int | 当前页 |
| pageSize | int | 每页条数 |

**CallRecord结构：**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| taskId | string | 任务ID |
| sourceAgentId | string | 源Agent ID |
| sourceAgentName | string | 源Agent名称 |
| targetAgentId | string | 目标Agent ID |
| targetAgentName | string | 目标Agent名称 |
| callType | string | 调用类型 |
| taskType | string | 任务类型 |
| taskDescription | string | 任务描述 |
| status | string | 状态 |
| duration | int | 耗时（毫秒） |
| errorMessage | string | 错误信息 |
| createTime | datetime | 创建时间 |

### 6.4 获取执行链路

**接口用途：** 获取工作流/任务的完整执行链路

```
GET /api/v1/a2a/graph/executions/{executionId}
```

**路径参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| executionId | string | 是 | 执行ID |

**响应参数：**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| code | int | 状态码 |
| message | string | 响应消息 |
| data | ExecutionDetail | 执行详情 |

**ExecutionDetail结构：**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| executionId | string | 执行ID |
| workflowId | string | 工作流ID |
| workflowName | string | 工作流名称 |
| triggerType | string | 触发类型 |
| status | string | 执行状态 |
| startTime | datetime | 开始时间 |
| endTime | datetime | 结束时间 |
| totalDuration | int | 总耗时（毫秒） |
| nodes | NodeExecution[] | 节点执行列表 |

**NodeExecution结构：**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| nodeId | string | 节点ID |
| nodeName | string | 节点名称 |
| nodeType | string | 节点类型 |
| agentId | string | Agent ID |
| agentName | string | Agent名称 |
| status | string | 状态 |
| duration | int | 耗时 |
| input | object | 输入参数 |
| output | object | 输出结果 |
| errorMessage | string | 错误信息 |
| parallelIndex | int | 并行节点索引 |

### 6.5 WebSocket实时推送

**接口用途：** 订阅Agent状态变更和调用事件

**连接地址：**
```
ws://{host}/ws/a2a/graph
```

**订阅主题：**
```
/topic/graph/status    # Agent状态变更
/topic/graph/call      # 实时调用事件
/topic/graph/stats     # 统计信息更新
```

**消息格式：**

**状态变更消息：**
```json
{
  "type": "AGENT_STATUS_CHANGE",
  "payload": {
    "agentId": "agent-001",
    "status": "OFFLINE",
    "previousStatus": "ONLINE",
    "timestamp": "2026-04-14T10:00:00Z"
  }
}
```

**实时调用消息：**
```json
{
  "type": "A2A_CALL_EVENT",
  "payload": {
    "taskId": "task-001",
    "sourceAgentId": "agent-001",
    "targetAgentId": "agent-002",
    "callType": "SYNC",
    "status": "SUCCESS",
    "duration": 230,
    "timestamp": "2026-04-14T10:00:00Z"
  }
}
```

**订阅示例：**
```javascript
// 前端订阅示例
const socket = new WebSocket('ws://localhost:8080/ws/a2a/graph');
socket.onopen = () => {
  socket.send(JSON.stringify({
    action: 'SUBSCRIBE',
    topics: ['/topic/graph/status', '/topic/graph/call']
  }));
};
socket.onmessage = (event) => {
  const message = JSON.parse(event.data);
  // 处理消息
};
```

### 6.6 导出图谱数据

**接口用途：** 导出图谱数据为JSON/Excel

```
GET /api/v1/a2a/graph/export
```

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| format | string | 否 | 导出格式：json/excel，默认json |
| includeHistory | boolean | 否 | 是否包含历史调用记录 |

**响应：**
- Content-Type: application/octet-stream
- 文件名: a2a-graph-{timestamp}.{format}

---

## 7. 错误处理

### 7.1 前端错误处理

| 错误场景 | 用户提示 | 处理方式 |
|----------|----------|----------|
| 网络断开 | "网络连接已断开，正在重连..." | 自动重连（3次），失败后显示重连按钮 |
| 接口超时 | "加载超时，请稍后刷新" | 显示刷新按钮 |
| 数据加载失败 | "数据加载失败，请重试" | 显示重试按钮 |
| WebSocket断开 | "实时连接已断开" | 自动重连，显示状态指示 |

### 7.2 后端错误码

| 错误码 | 说明 | 处理建议 |
|--------|------|----------|
| 10001 | Agent不存在 | 检查agentId是否正确 |
| 10002 | 调用关系不存在 | 检查edgeId是否正确 |
| 10003 | 无调用权限 | 检查A2A授权配置 |
| 10004 | 执行记录不存在 | 检查executionId是否正确 |
| 10005 | 参数校验失败 | 检查请求参数格式 |
| 20001 | 图谱数据加载失败 | 服务内部错误，重试 |
| 20002 | 统计计算失败 | 服务内部错误，重试 |

---

## 8. 性能要求

### 8.1 实时性要求

| 指标 | 要求 | 说明 |
|------|------|------|
| 状态更新延迟 | < 1秒 | Agent状态变化到前端显示的延迟 |
| 边刷新频率 | 5秒 | 图谱边的调用量统计刷新间隔 |
| 统计刷新频率 | 10秒 | 概览统计刷新间隔 |
| 页面初始化 | < 3秒 | 图谱首次加载时间 |
| 筛选响应 | < 500ms | 筛选条件变更到图谱刷新的时间 |

### 8.2 并发与容量

| 指标 | 要求 | 说明 |
|------|------|------|
| 最大节点数 | 500 | 单页面可展示的最大Agent数 |
| 最大边数 | 2000 | 单页面可展示的最大调用关系 |
| 最大记录数 | 10000 | 调用记录列表最大条数 |
| 并发连接数 | 1000 | WebSocket并发订阅数 |
| 数据保留时间 | 7天 | 图谱历史数据保留天数 |

### 8.3 前端性能优化

| 优化策略 | 说明 |
|----------|------|
| 虚拟化渲染 | 节点/边数量超过100时启用虚拟化，只渲染可视区域 |
| 节流处理 | 拖拽、缩放事件节流（16ms） |
| 数据缓存 | 前端缓存最近一次图谱数据，筛选时先展示缓存 |
| 分页加载 | 调用记录列表采用分页，每页20条 |
| 图片导出 | 大图谱导出为PNG时使用后台渲染 |

---

## 9. 边界情况

### 9.1 空状态

| 场景 | 界面显示 |
|------|----------|
| 无Agent数据 | 空状态插图 + "暂无Agent数据，请先注册Agent" |
| 筛选结果为空 | 空状态插图 + "未找到符合条件的Agent" |
| 无调用记录 | 空状态插图 + "暂无调用记录" |
| Agent无下游依赖 | 下游依赖区域显示"暂无下游依赖" |
| Agent无上游调用 | 上游调用区域显示"暂无上游调用" |

### 9.2 异常状态

| 场景 | 界面显示 |
|------|----------|
| Agent离线 | 节点变灰，显示"离线"标签 |
| 调用失败 | 边变红，悬停显示错误原因 |
| 批量离线 | 离线节点使用虚线框高亮分组 |
| 循环调用检测 | 循环路径使用红色标记，显示警告提示 |

### 9.3 数据量边界

| 场景 | 处理方式 |
|------|----------|
| 节点数 > 100 | 启用分级展示：只显示核心Agent，折叠次要Agent |
| 边数 > 500 | 默认隐藏低频边（<10次/天），可通过筛选恢复 |
| 调用记录 > 10000 | 提示数据量较大，建议缩小时间范围 |

---

## 10. 前端技术方案

### 10.1 技术选型

| 技术 | 用途 | 版本 |
|------|------|------|
| Vue 3 | 框架 | 3.4+ |
| Element Plus | UI组件库 | 2.5+ |
| D3.js / G6 | 图谱渲染 | D3 v7 / G6 5.x |
| WebSocket | 实时通信 | 原生 |
| Pinia | 状态管理 | 2.1+ |
| Vite | 构建工具 | 5.0+ |

### 10.2 组件结构

```
src/
├── views/
│   └── a2a/
│       └── AgentGraph/
│           ├── AgentGraph.vue          # 主页面
│           ├── components/
│           │   ├── GraphCanvas.vue     # 图谱画布
│           │   ├── FilterPanel.vue    # 筛选面板
│           │   ├── DetailPanel.vue     # 详情面板
│           │   ├── NodeDetail.vue      # 节点详情
│           │   ├── CallHistory.vue     # 调用记录
│           │   └── ExecutionChain.vue  # 执行链路
│           └── composables/
│               ├── useGraphData.ts     # 图谱数据
│               ├── useWebSocket.ts     # WebSocket
│               └── useGraphLayout.ts   # 布局算法
```

---

## 11. 附录

### 11.1 术语表

| 术语 | 说明 |
|------|------|
| A2A | Agent to Agent，Agent间通信协议 |
| 节点 | 图谱中的Agent实体 |
| 边 | 图谱中Agent间的调用关系 |
| 上游 | 调用当前Agent的其他Agent |
| 下游 | 当前Agent调用的其他Agent |
| 调用链路 | 一次工作流执行中，Agent的执行顺序 |

### 11.2 参考文档

- [A2A通信协议设计](./architecture-v2.md)
- [A2A需求文档](./requirements-v2.md)
- [SpringAI技术方案](./technical-design-springai.md)
