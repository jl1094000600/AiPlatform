# Agent调用关系图谱 - 架构评审文档

| 版本 | 日期       | 作者   | 备注 |
|------|------------|--------|------|
| 2.0  | 2026-04-14 | architect | 完善评审意见 |

---

## 1. 评审概述

### 1.1 评审范围

本次评审针对 **Agent调用关系图谱** 功能的技术方案，包括：
- 后端图谱数据 API 设计
- 前端图谱可视化实现方案（ECharts vs D3.js）
- 数据聚合查询性能优化
- 实时性方案（WebSocket vs 轮询）

### 1.2 评审依据

| 文档/代码 | 说明 |
|-----------|------|
| AgentGraph.vue | 前端图谱组件实现 |
| MonitorController.java | 后端 API 控制器 |
| MonitorService.java | 后端图谱数据服务 |
| AgentGraphNode/Edge.java | 数据结构定义 |
| docs/architecture-v2.md | 架构设计文档 |

---

## 2. 实现情况总结

### 2.1 后端实现

**API 接口**：
```
GET /api/v1/monitor/agent-graph
```

**返回数据结构**：
```json
{
  "code": 200,
  "data": {
    "nodes": [
      {
        "id": 1,
        "name": "Agent名称",
        "type": "分类",
        "status": 1,
        "lastHeartbeat": "2026-04-14T10:00:00",
        "instanceCount": 2
      }
    ],
    "edges": [
      {
        "source": 1,
        "target": 2,
        "callCount": 100,
        "avgResponseTime": 150.5,
        "lastCallTime": "2026-04-14T09:30:00"
      }
    ]
  }
}
```

**数据来源**：
- 节点：ai_agent 表 + ai_agent_heartbeat 表
- 边：mon_call_record 表（按 agent_id 聚合调用统计）

### 2.2 前端实现

**技术选型**：ECharts graph（力导向布局）

**核心功能**：
| 功能 | 实现状态 |
|------|----------|
| 力导向布局 | 已实现 |
| 缩放/拖拽 | 已实现（roam: true, draggable: true） |
| 节点点击详情 | 已实现（Drawer 展示） |
| 边点击统计 | 已实现（Drawer 展示） |
| 状态筛选 | 已实现（all/online/offline） |
| 实时更新 | 10秒轮询 |

---

## 3. 评审意见

### 3.1 优点

1. **技术选型合理**：ECharts graph 满足需求，学习成本低，维护简单
2. **交互设计完善**：节点/边点击展示详情，体验良好
3. **代码结构清晰**：前后端分离，API 规范统一
4. **状态筛选功能**：支持按在线/离线状态过滤

### 3.2 问题与建议

#### 问题 1：边数据不准确（严重）

**现状**：
- `mon_call_record` 表缺少 `caller_id` 字段
- MonitorService 第 85 行：`edge.setSource(0L)` - source 始终为 0
- 边只能表示"某 Agent 被调用过"，而非"Agent A 调用了 Agent B"

**建议**：
```sql
-- 方案：扩展 mon_call_record 表
ALTER TABLE mon_call_record ADD COLUMN caller_agent_id BIGINT COMMENT '调用方Agent ID';
```

或使用 `ai_a2a_task` 表作为边数据来源（该表有 source_agent_id 和 target_agent_id）。

#### 问题 2：轮询而非 WebSocket（中等）

**现状**：
- 前端使用 10 秒轮询：`pollInterval = setInterval(loadGraphData, 10000)`
- 后端无 WebSocket 推送机制

**影响**：
- 实时性一般（最长 10 秒延迟）
- 服务端负载较高（无谓的轮询请求）

**建议**：
- 短期保持轮询（功能已可用）
- 长期建议接入 WebSocket 推送，可复用水印架构中的 WebSocket 实现

#### 问题 3：无后端缓存（中等）

**现状**：
- 每次请求直接查询数据库
- 节点/边数据全量加载

**建议**：
```java
// 方案：Redis 缓存
@Cacheable(value = "agentGraph", key = "'full'", cacheManager = "redisCacheManager")
public AgentGraphResponse getAgentGraph() { ... }

// 变更时主动清除缓存
@CacheEvict(value = "agentGraph", key = "'full'")
public void updateAgent(...) { ... }
```

#### 问题 4：深度遍历参数缺失（轻微）

**测试文件** `AgentGraphServiceTest.java` 显示存在 `depth` 参数（控制邻居节点深度），但 MonitorController 的 getAgentGraph 接口未暴露此参数。

**建议**：
```java
@GetMapping("/agent-graph")
public Result<AgentGraphResponse> getAgentGraph(
        @RequestParam(required = false) Long agentId,
        @RequestParam(defaultValue = "2") Integer depth) {
    return Result.success(monitorService.getAgentGraph(agentId, depth));
}
```

---

## 4. 性能优化建议

| 优化项 | 当前 | 建议 | 优先级 |
|--------|------|------|--------|
| 边数据来源 | mon_call_record（无source） | 改用 ai_a2a_task | P0 |
| 缓存 | 无 | Redis 缓存，TTL 30s | P1 |
| 实时更新 | 10秒轮询 | WebSocket 推送 | P2 |
| 数据库查询 | 全量查询 | 分页 + 索引优化 | P1 |

---

## 5. 数据库索引检查

```sql
-- ai_agent 已有索引
KEY idx_agent_id (agent_id)  -- MonitorService.getAgentGraph() 使用

-- ai_a2a_task（边数据来源）
KEY idx_source_agent (source_agent_id)
KEY idx_target_agent (target_agent_id)

-- mon_call_record
KEY idx_agent_id (agent_id)
KEY idx_create_time (create_time)
```

索引设计合理，支持当前查询。

---

## 6. 后续行动项

| 项 | 负责人 | 优先级 |
|----|--------|--------|
| 修复边数据 source=0 问题 | backend-dev | P0 |
| 增加 Redis 缓存 | backend-dev | P1 |
| 添加 depth 参数 | backend-dev | P2 |
| 接入 WebSocket（远期） | backend-dev | P2 |

---

## 7. 结论

**整体评价**：方案可行，实现完整，但存在边数据不准确的问题需要修复。

**通过条件**：
1. 修复边数据 source 字段（使用 ai_a2a_task 或扩展 mon_call_record）
2. 增加 Redis 缓存

**备注**：轮询方案可接受，但建议后续升级为 WebSocket 推送。

---

*文档版本：2.0*
*最后更新：2026-04-14*
