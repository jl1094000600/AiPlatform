# Agent Graph 图谱功能测试用例

## 1. 后端单元测试

### 1.1 MonitorController 测试 (AgentGraphControllerTest)
- **testGetAgentGraph_Success**: 测试成功获取图谱数据
- **testGetAgentGraph_EmptyGraph**: 测试空图谱场景
- **testGetAgentGraph_WithOnlineAgents**: 测试包含在线Agent的图谱
- **testGetAgentGraph_VerifyResponseStructure**: 验证响应结构

### 1.2 MonitorService 测试 (AgentGraphServiceTest)
- **testGetAgentGraph_Basic**: 基本图谱生成
- **testGetAgentGraph_WithAgents**: 带Agent数据的图谱
- **testGetAgentGraph_NodeStructure**: 节点结构验证
- **testGetAgentGraph_EdgeStructure**: 边结构验证
- **testGetAgentGraph_WithHeartbeat**: 带心跳信息的图谱
- **testGetAgentGraph_Aggregation**: 调用记录聚合验证

### 1.3 CallRecordService 测试 (CallRecordServiceTest)
- **testListCallRecords**: 分页查询调用记录
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

## 3. 前端测试

### 3.1 AgentGraph.test.js
- **API Integration**: API调用和数据结构验证
- **Status Filter Logic**: 状态过滤逻辑
- **Graph Data Processing**: 图谱数据处理
- **Status Display**: 状态显示文本
- **Time Formatting**: 时间格式化
- **Node Color Logic**: 节点颜色逻辑

## 4. 测试数据

### 4.1 AgentGraphNode 结构
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

### 4.2 AgentGraphEdge 结构
```json
{
  "source": 1,
  "target": 2,
  "callCount": 100,
  "avgResponseTime": 150.5,
  "lastCallTime": "2024-01-01T12:00:00"
}
```

## 5. 测试覆盖场景

| 场景 | 输入 | 预期输出 |
|------|------|----------|
| 正常图谱 | 存在Agent和调用记录 | 返回完整节点和边 |
| 空图谱 | 无任何数据 | 返回空数组 |
| 在线过滤 | statusFilter='online' | 仅显示在线节点 |
| 离线过滤 | statusFilter='offline' | 仅显示离线节点 |
| 节点点击 | 点击节点 | 显示节点详情面板 |
| 边点击 | 点击边 | 显示调用统计面板 |
| 自动刷新 | 10秒轮询 | 定期更新图谱数据 |

## 6. 运行测试

### 后端测试
```bash
cd backend
mvn test -Dtest=AgentGraphControllerTest
mvn test -Dtest=AgentGraphServiceTest
mvn test -Dtest=AgentGraphIntegrationTest
```

### 前端测试
```bash
cd front
npm install vitest @vue/testing-library jsdom --save-dev
npm test
```
