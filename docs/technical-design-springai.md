# SpringAI 接入与 A2A 实现技术方案

## 1. SpringAI 概述与选型理由

### 1.1 什么是 SpringAI

SpringAI 是 Spring Framework 官方推出的 AI 集成框架，提供了统一抽象的 AI 模型调用接口，支持多种 AI 供应商（OpenAI、Azure OpenAI、Anthropic、HuggingFace、Ollama 等）。

### 1.2 选型理由

| 特性 | SpringAI | 直接调用 SDK |
|------|----------|--------------|
| 供应商抽象 | 统一接口，切换成本低 | 每个供应商独立 SDK |
| Tool/Agent 支持 | 内置 ChatAgent、ToolCall | 需自行实现 |
| Spring 生态集成 | 与 Spring Boot 深度整合 | 需手动适配 |
| 社区活跃度 | VMware 官方维护 | 依赖各供应商 |
| 版本（2024） | 1.0.0+ GA | - |

**结论**：选用 SpringAI `1.0.0-M4` 版本（Spring Boot 3.2.x 兼容版本），兼顾供应商抽象和 Agent 能力。

### 1.3 SpringAI 核心概念

```
Model (ChatModel)     -- AI 模型抽象，如 OpenAIChatModel
Prompt                -- 提示词模板
Agent (ReActAgent)    -- 基于 ReAct 推理的 Agent，支持 Tool 调用
Tool                  -- 工具能力（类似 Function Calling）
ChatClient            -- 统一客户端，支持 streaming
```

## 2. 与现有系统集成的架构设计

### 2.1 现有系统分析

```
AiModel 表：
  - id, modelCode, modelName, provider, modelVersion
  - endpoint, apiVersion, pricePer1kToken, status

AiAgent 表：
  - id, agentCode, agentName, description, category
  - apiUrl, httpMethod, requestSchema, responseSchema
  - status (1=上线, 2=下线), ownerId
```

### 2.2 集成架构

```
┌─────────────────────────────────────────────────────────┐
│                    Application Layer                     │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐  │
│  │ AgentController│ │A2AController │  │ ModelController  │  │
│  └──────┬──────┘  └──────┬──────┘  └────────┬────────┘  │
│         │                │                    │           │
│  ┌──────▼────────────────▼────────────────────▼────────┐ │
│  │              SpringAI Integration Layer               │ │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐   │ │
│  │  │ChatModelSvc │  │ AgentRegistry│  │ A2AMessageSvc│   │ │
│  │  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘   │ │
│  │         │                │                │           │ │
│  │  ┌──────▼────────────────▼────────────────▼────────┐ │ │
│  │  │              SpringAI Core                        │ │ │
│  │  │   ChatModel  │  ReActAgent  │  Tool  │  ChatClient│ │ │
│  │  └───────────────────────────────────────────────────┘ │ │
│  └───────────────────────────────────────────────────────┘ │
│                          │                                 │
│  ┌───────────────────────▼───────────────────────────────┐ │
│  │              AiModelProvider 动态加载                   │ │
│  │  OpenAI │ Azure │ Anthropic │ Ollama │ 自定义         │ │
│  └───────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### 2.3 关键设计

#### ChatModelService（模型服务）
- 从 `AiModel` 表动态加载模型配置
- 根据 `provider` 字段创建对应的 `ChatModel` Bean
- 使用 `Map<modelCode, ChatModel>` 缓存已加载的模型
- 支持模型热更新（配置变更后刷新）

#### AgentRegistry（Agent 注册表）
- 管理所有注册到系统的 Agent
- 每个 Agent 关联一个 SpringAI `ChatAgent`
- Agent 的能力（Tools）从 `ai_agent_version.config` 字段解析

## 3. Agent 在线状态检测方案（心跳机制）

### 3.1 设计思路

采用 **Redis + 定时任务** 的混合方案：

```
┌──────────────┐    定时 ping    ┌──────────────┐
│  Agent Registry │ ──────────────>│  Redis Heartbeat│
│  (Spring Bean)  │ <────────────── │  (Hash: agentId│
└──────────────┘   超时检测回调    │   -> lastPing) │
         │                              └──────────────┘
         │         ┌──────────────┐            │
         └────────>│  Agent Status │<───────────┘
                   │  Update (DB)   │
                   └──────────────┘
```

### 3.2 心跳协议

| 字段 | 说明 |
|------|------|
| agentId | Agent 唯一标识 |
| lastHeartbeat | 最后心跳时间 |
| healthScore | 健康评分 (0-100) |
| capabilities | 当前能力列表 |

**心跳间隔**：30 秒
**超时阈值**：90 秒（3 次未心跳则标记离线）

### 3.3 实现方案

#### 1) 新增字段（扩展 ai_agent 表或新建表）

```sql
-- 可选：新建心跳记录表
CREATE TABLE ai_agent_heartbeat (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    agent_id BIGINT NOT NULL,
    last_heartbeat DATETIME,
    health_score INT DEFAULT 100,
    endpoint VARCHAR(512),
    status TINYINT DEFAULT 1 COMMENT '1=在线 2=离线',
    INDEX idx_agent_id (agent_id),
    INDEX idx_status (status)
);
```

#### 2) HeartbeatService

```java
public interface HeartbeatService {
    // Agent 主动上报心跳
    void recordHeartbeat(Long agentId, HeartbeatRequest request);
    // 检测超时 Agent
    void detectOfflineAgents();
    // 获取 Agent 在线状态
    boolean isAgentOnline(Long agentId);
}
```

#### 3) 定时任务

```java
@Scheduled(fixedRate = 30000) // 30秒执行一次
public void detectOfflineAgents() {
    // 扫描 Redis 中超过 90 秒未更新的 Agent
    // 更新数据库状态
}
```

## 4. A2A（Agent to Agent）通信协议设计

### 4.1 A2A 协议概述

A2A 是 Agent 之间的通信协议，支持多 Agent 协作任务分解。

### 4.2 消息格式

```json
{
  "messageId": "msg_uuid",
  "sourceAgent": "agent_code_001",
  "targetAgent": "agent_code_002",
  "sessionId": "session_uuid",
  "action": "invoke|respond|delegate| broadcast",
  "payload": {
    "intent": "用户查询",
    "params": {},
    "context": {}
  },
  "timestamp": "2026-04-14T10:00:00Z",
  "correlationId": "org_msg_uuid"
}
```

### 4.3 Action 类型

| Action | 说明 |
|--------|------|
| invoke | 请求目标 Agent 执行任务 |
| respond | 返回执行结果 |
| delegate | 委托其他 Agent 继续处理 |
| broadcast | 向所有在线 Agent 广播 |

### 4.4 A2A 消息队列

使用 **Redis Stream** 作为消息中间件：

```
A2A:stream:{sessionId} -> XREADGROUP 消费组模式
```

### 4.5 A2A 核心流程

```
Agent A                          Agent B
   │                                │
   │──── A2A invoke ───────────────>│
   │     (Async via Redis Stream)   │
   │                                │──> ReActAgent 执行
   │                                │<── 执行结果
   │<─── A2A respond ───────────────│
```

### 4.6 关键接口

```java
public interface A2AMessageService {
    // 发送 A2A 消息
    String sendMessage(A2AMessage message);
    // 获取消息响应
    A2AMessage getResponse(String correlationId, long timeoutMs);
    // 注册 Agent 消息处理器
    void registerHandler(String agentCode, Function<A2AMessage, A2AMessage> handler);
}
```

## 5. 实现步骤

### Phase 1: SpringAI 基础接入（第 1-2 天）
1. 添加 SpringAI 依赖（`spring-ai-starter`）
2. 实现 `AiModelProvider` 动态加载模型配置
3. 实现 `ChatModelService` 统一调用接口
4. 实现 `ModelController` 管理接口

### Phase 2: Agent 在线状态检测（第 2-3 天）
1. 新建 `ai_agent_heartbeat` 表
2. 实现 `HeartbeatService` 心跳服务
3. 实现定时任务 `HeartbeatMonitor`
4. 开发心跳检测 API 接口

### Phase 3: A2A 协议实现（第 3-5 天）
1. 设计 A2A 消息 DTO
2. 实现 `A2AMessageService`（Redis Stream）
3. 实现 `AgentRegistry` Agent 注册表
4. 开发 A2A 测试用例

## 6. 难点分析

| 难点 | 解决方案 |
|------|----------|
| 模型供应商差异 | 通过 `ModelProvider` 接口抽象，统一 `ChatModelService` 调用 |
| 心跳性能 | 使用 Redis Hash 存储，Scheduled 任务批量处理 |
| A2A 消息顺序 | 使用 Redis Stream + Consumer Group 保证顺序 |
| 超时处理 | 使用 CompletableFuture + Timeout 实现 |
| 序列化问题 | 使用 Jackson + 自定义序列化器处理复杂对象 |

## 7. 依赖版本

```xml
<spring-ai.version>1.0.0-M4</spring-ai.version>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter</artifactId>
    <version>${spring-ai.version}</version>
</dependency>
```

支持的模型：
- `spring-ai-starter-model-openai`
- `spring-ai-starter-model-anthropic`
- `spring-ai-starter-model-azure-openai`
- `spring-ai-starter-model-huggingface`
