# Agent Graph 图谱功能测试用例

## 1. 后端单元测试

### 1.1 MonitorController 测试 (AgentGraphControllerTest)
- **testGetAgentGraph_Success**: 测试成功获取图谱数据
- **testGetAgentGraph_EmptyGraph**: 测试空图谱场景
- **testGetAgentGraph_WithOnlineAgents**: 测试包含在线Agent的图谱
- **testGetAgentGraph_VerifyResponseStructure**: 验证响应结构

### 1.2 MonitorController 新API测试 (MonitorControllerNewApiTest)
- **testListCallRecords_Success**: 调用记录列表-成功
- **testListCallRecords_WithAgentIdFilter**: 调用记录-按AgentID筛选
- **testListCallRecords_WithTimeRange**: 调用记录-按时长筛选
- **testListCallRecords_Pagination**: 调用记录-分页
- **testGetExecutionChain_ByTaskId**: 执行链路-按TaskID查询
- **testGetExecutionChain_BySessionId**: 执行链路-按SessionID查询
- **testGetExecutionChain_EmptyChain**: 执行链路-空链路
- **testExportGraph_Success**: 图谱导出-成功
- **testExportGraph_ReturnsCorrectFormat**: 图谱导出-格式验证

### 1.3 MonitorService 测试 (AgentGraphServiceTest)
- **testGetAgentGraph_Basic**: 基本图谱生成
- **testGetAgentGraph_WithAgents**: 带Agent数据的图谱
- **testGetAgentGraph_NodeStructure**: 节点结构验证
- **testGetAgentGraph_EdgeStructure**: 边结构验证
- **testGetAgentGraph_WithHeartbeat**: 带心跳信息的图谱
- **testGetAgentGraph_A2ATaskAggregation**: A2A任务聚合验证
- **testGetAgentGraph_MultipleSourceTargetPairs**: 多对多边聚合
- **testGetAgentGraph_EdgeResponseTimeCalculation**: 边响应时间计算

### 1.4 ExecutionChainService 测试 (ExecutionChainServiceTest)
- **testGetExecutionChain_ByTaskId**: 按TaskID查询执行链路
- **testGetExecutionChain_BySessionId**: 按SessionID查询执行链路
- **testGetExecutionChain_EmptyChain**: 空执行链路
- **testGetExecutionChain_NodeStructure**: 链路节点结构验证
- **testGetExecutionChain_DurationCalculation**: 链路时长计算
- **testGetExecutionChain_MultipleTasks**: 多任务链路
- **testGetExecutionChain_RequiresTaskIdOrSessionId**: 缺少参数验证
- **testGetExecutionChain_RequiresNonEmptyTaskId**: 空TaskID验证

### 1.5 CallRecordService 测试 (CallRecordServiceTest)
- **testListCallRecords**: 分页查询调用记录
- **testListCallRecords_WithAgentIdFilter**: 按AgentID筛选
- **testListCallRecords_WithTimeRange**: 时间范围筛选
- **testListCallRecords_Pagination**: 分页验证
- **testGetByTraceId**: 按TraceID查询
- **testGetRecentRecords**: 获取最近记录
- **testGetTotalCallsByAgentId**: Agent调用总数
- **testSaveCallRecord**: 保存调用记录
- **testCountByAgentAndTimeRange**: 按时间和Agent统计
- **testGetSuccessRateByAgentAndTimeRange**: 成功率统计
- **testGetAvgDurationByAgentAndTimeRange**: 平均响应时间
- **testCountOnlineAgents**: 在线Agent数量
- **testGetCurrentQps**: 当前QPS
- **testGetAvgResponseTime**: 平均响应时间

## 2. 集成测试

### 2.1 AgentGraphIntegrationTest
- **testEndToEndGraphGeneration**: 端到端图谱生成
- **testGraphResponseStructure**: 响应结构完整性
- **testNodeFields**: 节点字段验证
- **testEdgeFields**: 边字段验证
- **testEmptyDatabaseScenario**: 空数据库场景
- **testA2ATaskEdgeGeneration**: A2A任务边生成验证

## 3. 前端测试

### 3.1 AgentGraph.test.js
- **API Integration**: API调用和数据结构验证
- **Status Filter Logic**: 状态过滤逻辑
- **Graph Data Processing**: 图谱数据处理
- **Status Display**: 状态显示文本
- **Time Formatting**: 时间格式化
- **Node Color Logic**: 节点颜色逻辑

## 4. 测试数据

### 4.1 数据来源
- **节点**: ai_agent 表 + ai_agent_heartbeat 表
- **边**: ai_a2a_task 表 (source_agent_id -> target_agent_id)

### 4.2 AgentGraphNode 结构
```json
{
  "id": 1,
  "name": "Agent-A",
  "type": "AI",
  "status": 1,
  "lastHeartbeat": "2024-01-01T12:00:00",
  "instanceCount": 2
}
```

### 4.3 AgentGraphEdge 结构
```json
{
  "source": 1,
  "target": 2,
  "callCount": 100,
  "avgResponseTime": 150.5,
  "lastCallTime": "2024-01-01T12:00:00"
}
```

### 4.4 ExecutionChainNode 结构
```json
{
  "taskId": "task-1",
  "sourceAgentId": 1,
  "sourceAgentName": "Agent-A",
  "targetAgentId": 2,
  "targetAgentName": "Agent-B",
  "status": "completed",
  "taskType": "sync",
  "startTime": "2024-01-01T12:00:00",
  "endTime": "2024-01-01T12:01:00",
  "durationMs": 60000
}
```

## 5. 测试覆盖场景

### 5.1 图谱功能
| 场景 | 输入 | 预期输出 |
|------|------|----------|
| 正常图谱 | 存在Agent和A2A任务 | 返回完整节点和边 |
| 空图谱 | 无任何数据 | 返回空数组 |
| 在线过滤 | statusFilter='online' | 仅显示在线节点 |
| 离线过滤 | statusFilter='offline' | 仅显示离线节点 |
| 节点点击 | 点击节点 | 显示节点详情面板 |
| 边点击 | 点击边 | 显示调用统计面板 |
| 自动刷新 | 10秒轮询 | 定期更新图谱数据 |
| A2A聚合 | 同source-target的多个任务 | 聚合为一条边 |

### 5.2 调用记录功能
| 场景 | 输入 | 预期输出 |
|------|------|----------|
| 正常列表 | 分页参数 | 返回调用记录列表 |
| 按Agent筛选 | agentId=1 | 仅返回该Agent的记录 |
| 时间范围 | startTime, endTime | 返回时间范围内的记录 |
| 分页 | pageNum=2, pageSize=10 | 返回第二页数据 |
| 按Trace查询 | traceId=xxx | 返回该Trace详情 |

### 5.3 执行链路功能
| 场景 | 输入 | 预期输出 |
|------|------|----------|
| 按TaskID查询 | taskId=xxx | 返回该任务的链路 |
| 按Session查询 | sessionId=xxx | 返回该Session的所有任务 |
| 空链路 | 不存在的ID | 返回空列表 |
| 多任务链路 | Session多个任务 | 按时间排序的链路 |
| 缺少参数 | 无taskId/sessionId | 抛出异常 |

### 5.4 导出功能
| 场景 | 输入 | 预期输出 |
|------|------|----------|
| 正常导出 | 无参数 | 返回图谱数据 |
| 导出格式 | GraphResponse格式 | 包含nodes和edges |

## 6. 运行测试

### 后端测试
```bash
cd backend
mvn test -Dtest=AgentGraphControllerTest
mvn test -Dtest=AgentGraphServiceTest
mvn test -Dtest=AgentGraphIntegrationTest
mvn test -Dtest=MonitorControllerNewApiTest
mvn test -Dtest=ExecutionChainServiceTest
mvn test -Dtest=CallRecordServiceTest
```

### 前端测试
```bash
cd front
npm install vitest @vue/testing-library jsdom --save-dev
npm test
```
