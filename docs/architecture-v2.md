# AI中台管理系统 v2.0 架构设计文档

| 版本 | 日期       | 作者   | 备注 |
|------|------------|--------|------|
| 2.0  | 2026-04-14 | architect | v2.0架构设计 |

---

## 1. 架构设计概述

### 1.1 设计目标

v2.0 在现有 Spring Boot 3.2 + Vue3 + MySQL + Redis 架构基础上，新增三大核心能力：

1. **SpringAI集成**：统一接入多种LLM模型，简化AI能力开发
2. **Agent在线监控**：实时感知Agent状态，支持心跳检测和告警
3. **A2A通信协议**：支持Agent间协作调度，实现复杂任务分解

### 1.2 整体架构图

```
┌────────────────────────────────────────────────────────────────────────────────┐
│                                   Frontend (Vue3)                               │
│    ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │
│    │ Agent管理    │  │ 状态监控看板  │  │  SpringAI配置 │  │  A2A工作流   │    │
│    └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘    │
└────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        │ HTTP/WebSocket
                                        ▼
┌────────────────────────────────────────────────────────────────────────────────┐
│                              API Gateway / Nginx                                │
└────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌────────────────────────────────────────────────────────────────────────────────┐
│                           Backend (Spring Boot 3.2)                            │
│  ┌──────────────────────────────────────────────────────────────────────────┐   │
│  │                            Controller Layer                               │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │   │
│  │  │AgentController│ │HeartbeatController│ │A2AController│ │SpringAIController│ │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘    │   │
│  └──────────────────────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────────────────────┐   │
│  │                           Service Layer                                   │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │   │
│  │  │ AgentService │ │HeartbeatService│ │A2AMessageSvc │ │ChatModelSvc  │    │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘    │   │
│  └──────────────────────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────────────────────┐   │
│  │                         SpringAI Integration                             │   │
│  │  ┌─────────────────────────────────────────────────────────────────┐     │   │
│  │  │  ChatClient  │  ReActAgent  │  ToolRegistry  │  PromptManager │     │   │
│  │  └─────────────────────────────────────────────────────────────────┘     │   │
│  └──────────────────────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────────────────────┐   │
│  │                           Infrastructure Layer                            │   │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐          │   │
│  │  │    Redis        │  │     MySQL       │  │  WebSocket      │          │   │
│  │  │  (Session/Cache │  │  (Persistence)  │  │  (Real-time)    │          │   │
│  │  │   /A2A Stream)  │  │                 │  │                 │          │   │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────┘          │   │
│  └──────────────────────────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Agent 在线状态监控架构

### 2.1 技术选型：心跳检测 + WebSocket推送

#### 方案对比

| 方案 | 优点 | 缺点 | 推荐场景 |
|------|------|------|----------|
| **轮询** | 实现简单 | 延迟高、资源浪费 | 低实时性要求 |
| **心跳检测 + WebSocket** | 实时性高、推送精准 | 实现复杂 | **生产环境推荐** |
| **Server-Sent Events** | 实现比WebSocket简单 | 单向通信 | 仅需服务端推送 |

**结论**：采用 **心跳检测 + WebSocket推送** 方案，兼顾实时性与精确性。

### 2.2 心跳检测架构

```
┌─────────────┐     Heartbeat      ┌──────────────────┐     Notify      ┌─────────────┐
│    Agent    │ ─────────────────> │  HeartbeatService │ ─────────────> │   Redis      │
│  (Client)   │    POST /beat      │  (Spring Bean)    │    Pub/Sub     │  (Pub/Sub)  │
└─────────────┘                    └──────────────────┘                └─────────────┘
       │                                   │                                  │
       │                                   │ Scheduled (30s)                   │
       │                                   ▼                                  ▼
       │                          ┌──────────────────┐              ┌──────────────────┐
       │                          │  Offline Detector │ ─────────> │  WebSocket       │
       │                          │  (Scheduled Task) │            │  Push to Frontend│
       │                          └──────────────────┘              └──────────────────┘
       │                                   │
       │                                   ▼
       │                          ┌──────────────────┐
       └───────────────────────── │   MySQL          │ <────────── Dashboard
                                  │ ai_agent_heartbeat│
                                  └──────────────────┘
```

### 2.3 心跳协议设计

#### 2.3.1 注册接口 (Register)

```http
POST /api/v1/agent-heartbeat/register
Content-Type: application/json

{
  "agentId": "agent-001",
  "instanceId": "ins-001",
  "version": "1.2.0",
  "load": 0.45,
  "queueSize": 10,
  "capabilities": ["text-generation", "image-understanding"],
  "metadata": {
    "gpuMemory": "8GB",
    "os": "Linux"
  }
}
```

#### 2.3.2 心跳上报接口 (Beat)

```http
POST /api/v1/agent-heartbeat/beat
Content-Type: application/json

{
  "agentId": "agent-001",
  "instanceId": "ins-001",
  "load": 0.55,
  "queueSize": 12,
  "timestamp": 1713000000000
}
```

#### 2.3.3 注销接口 (Unregister)

```http
POST /api/v1/agent-heartbeat/unregister
Content-Type: application/json

{
  "agentId": "agent-001",
  "instanceId": "ins-001",
  "reason": "graceful_shutdown"
}
```

### 2.4 心跳配置参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| heartbeat.interval | 30秒 | Agent心跳间隔 |
| heartbeat.timeout | 90秒 | 超时阈值（= 3 * interval） |
| heartbeat.retry | 3次 | 离线判定重试次数 |
| offline.detect.interval | 10秒 | 服务端离线检测间隔 |

### 2.5 WebSocket实时推送

```java
// 推送消息格式
{
  "type": "AGENT_STATUS_CHANGE",
  "payload": {
    "agentId": "agent-001",
    "instanceId": "ins-001",
    "status": "OFFLINE",  // ONLINE / OFFLINE / ALERT
    "lastHeartbeat": "2026-04-14T10:00:00Z",
    "load": 0.85
  },
  "timestamp": 1713000000000
}
```

### 2.6 核心服务实现

```java
@Service
@RequiredArgsConstructor
public class HeartbeatService {
    private final RedisTemplate<String, Object> redisTemplate;
    private final AgentHeartbeatMapper heartbeatMapper;
    private final SimpMessagingTemplate messagingTemplate;

    private static final String HEARTBEAT_KEY_PREFIX = "agent:heartbeat:";
    private static final long HEARTBEAT_EXPIRE_SECONDS = 120;

    /**
     * 记录心跳 - Redis存储 + 发布状态变更
     */
    public void recordHeartbeat(HeartbeatRequest request) {
        String key = HEARTBEAT_KEY_PREFIX + request.getAgentId() + ":" + request.getInstanceId();

        // 1. 写入Redis（带过期时间）
        redisTemplate.opsForHash().putAll(key, toMap(request));
        redisTemplate.expire(key, HEARTBEAT_EXPIRE_SECONDS, TimeUnit.SECONDS);

        // 2. 异步更新MySQL（批量写入，避免频繁IO）
        asyncUpdateDb(request);

        // 3. 发布状态变更（WebSocket推送）
        publishStatusChange(request);
    }

    /**
     * 检测离线Agent - 定时任务
     */
    @Scheduled(fixedRate = 10000) // 10秒检测一次
    public void detectOfflineAgents() {
        // 扫描Redis中过期的Agent
        // 更新数据库状态
        // 触发告警
    }
}
```

---

## 3. A2A 通信协议设计

### 3.1 协议概述

A2A（Agent to Agent）协议定义Agent之间的通信标准，支持：

- **同步调用**：等待目标Agent返回结果
- **异步调用**：通过消息队列解耦
- **广播**：向所有在线Agent发送消息
- **委托**：将任务转交给其他Agent

### 3.2 A2A消息格式

```json
{
  "messageId": "msg_uuid_xxxxx",
  "sessionId": "session_uuid_xxxxx",
  "sourceAgent": {
    "agentId": "agent-meeting",
    "instanceId": "ins-001"
  },
  "targetAgent": {
    "agentId": "agent-calendar",
    "instanceId": null  // null表示任意实例
  },
  "action": "invoke",  // invoke | respond | delegate | broadcast
  "payload": {
    "taskType": "query_available_time",
    "intent": "查询明天下午2-4点是否有空",
    "params": {
      "date": "2026-04-15",
      "startHour": 14,
      "endHour": 16
    },
    "context": {
      "userId": "user-001",
      "conversationId": "conv-001"
    }
  },
  "responseFormat": "json",
  "timeout": 30000,
  "retryCount": 0,
  "maxRetries": 2,
  "timestamp": "2026-04-14T10:00:00Z",
  "correlationId": null
}
```

### 3.3 Action类型说明

| Action | 说明 | 使用场景 |
|--------|------|----------|
| invoke | 请求目标Agent执行任务 | 发起A2A调用 |
| respond | 返回执行结果 | 被调用方响应 |
| delegate | 委托其他Agent继续处理 | 任务链式传递 |
| broadcast | 向所有在线Agent广播 | 全局通知 |

### 3.4 同步 vs 异步架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              同步 A2A 调用流程                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   Agent-A                         中台                           Agent-B   │
│      │                              │                               │      │
│      │──── invoke ─────────────────>│                               │      │
│      │                              │──── invoke ─────────────────>│      │
│      │                              │                               │      │
│      │                              │<─── respond ──────────────────│      │
│      │<─── respond ─────────────────│                               │      │
│      │                              │                               │      │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                              异步 A2A 调用流程                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   Agent-A                         中台                      Redis Stream     │
│      │                              │                               │      │
│      │──── invoke ─────────────────>│                               │      │
│      │                              │──── XADD ────────────────────>│      │
│      │<─── taskId ─────────────────│                               │      │
│      │                              │                               │      │
│      │                              │                    ┌──────────▼──────┐
│      │                              │                    │  Agent-B (Consumer) │
│      │                              │<── XREADGROUP ─────│                  │
│      │                              │                               │      │
│      │                              │──── invoke ─────────────────>│      │
│      │                              │<─── respond ─────────────────│      │
│      │                              │                               │      │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.5 消息队列选型：Redis Stream

**选型理由**：

| 特性 | Redis Stream | RabbitMQ | Kafka |
|------|--------------|----------|-------|
| 持久化 | 支持 | 支持 | 支持 |
| 消费组 | 支持 (XREADGROUP) | 支持 | 支持 |
| 延迟消息 | 支持 | 支持 | 需额外配置 |
| 客户端支持 | Spring Data Redis | AMQP | 需额外依赖 |
| 运维复杂度 | 低 | 中 | 高 |

**Redis Stream Key设计**：

```
a2a:stream:{sessionId}          # A2A消息流
a2a:consumer:{agentId}          # 各Agent消费组
a2a:result:{correlationId}      # 同步结果缓存（TTL=5min）
```

### 3.6 A2A核心服务

```java
@Service
@RequiredArgsConstructor
public class A2AMessageService {
    private final RedisTemplate<String, Object> redisTemplate;
    private final A2ATaskMapper taskMapper;
    private final AgentRegistry agentRegistry;

    /**
     * 发送A2A消息（同步/异步）
     */
    public A2AMessageResult sendMessage(A2AMessage message) {
        // 1. 权限校验
        validateAuth(message.getSourceAgent(), message.getTargetAgent());

        // 2. 创建任务记录
        A2ATask task = createTask(message);

        // 3. 根据同步/异步选择不同处理
        if (message.isAsync()) {
            // 异步：写入Redis Stream
            return sendAsync(message, task);
        } else {
            // 同步：等待结果
            return sendSync(message, task);
        }
    }

    /**
     * 同步发送：使用 CompletableFuture + Timeout
     */
    private A2AMessageResult sendSync(A2AMessage message, A2ATask task) {
        String resultKey = "a2a:result:" + task.getCorrelationId();

        // 发送消息到Redis Stream
        redisTemplate.opsForStream().add(toStreamRecord(message));

        // 等待结果（超时控制）
        try {
            Object result = redisTemplate.opsForValue().get(resultKey);
            return new A2AMessageResult(task.getTaskId(), result, "SUCCESS");
        } catch (Exception e) {
            return new A2AMessageResult(task.getTaskId(), null, "TIMEOUT");
        }
    }

    /**
     * 异步发送：Redis Stream
     */
    private A2AMessageResult sendAsync(A2AMessage message, A2ATask task) {
        String streamKey = "a2a:stream:" + message.getSessionId();
        redisTemplate.opsForStream().add(toStreamRecord(message));
        return new A2AMessageResult(task.getTaskId(), null, "ACCEPTED");
    }
}
```

### 3.7 A2A安全控制

```java
@Service
@RequiredArgsConstructor
public class A2AAuthService {
    private final A2AAuthMapper authMapper;

    /**
     * 校验A2A调用权限
     */
    public void validateAuth(AgentInfo source, AgentInfo target) {
        // 1. 查询授权关系
        A2AAuth auth = authMapper.findAuth(source.getAgentId(), target.getAgentId());

        if (auth == null) {
            throw new BizException("未授权的A2A调用: " + source.getAgentId() + " -> " + target.getAgentId());
        }

        // 2. 校验QPS限制（使用Redis滑动窗口）
        if (!checkQpsLimit(source.getAgentId(), auth.getQpsLimit())) {
            throw new BizException("A2A调用超过QPS限制");
        }

        // 3. 校验调用链路深度（防止循环调用）
        if (!checkCallChainDepth(source.getAgentId())) {
            throw new BizException("A2A调用链路深度超限");
        }
    }

    /**
     * QPS限制：Redis滑动窗口实现
     */
    private boolean checkQpsLimit(Long agentId, int limit) {
        String key = "a2a:qps:" + agentId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, 1, TimeUnit.SECONDS);
        }
        return count != null && count <= limit;
    }
}
```

---

## 4. SpringAI 集成架构

### 4.1 集成架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           SpringAI Integration Layer                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                      ChatModelService                                │    │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                │    │
│  │  │OpenAI        │  │Anthropic    │  │ Ollama      │  ...           │    │
│  │  │ChatModel     │  │ChatModel    │  │ ChatModel   │                │    │
│  │  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘                │    │
│  │         │                │                │                        │    │
│  │         └────────────────┼────────────────┘                        │    │
│  │                          ▼                                           │    │
│  │                  ┌─────────────────┐                                  │    │
│  │                  │  ChatModelCache │  (Map<String, ChatModel>)       │    │
│  │                  │  模型实例缓存    │                                  │    │
│  │                  └─────────────────┘                                  │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                      AgentBindingService                             │    │
│  │  Agent ──────────────────────> ChatModel                             │    │
│  │  (ai_agent.springai_model_id)    (动态创建)                           │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                      PromptTemplateService                           │    │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                 │    │
│  │  │ SystemPrompt│  │ UserPrompt  │  │ FewShot     │                 │    │
│  │  │ Template    │  │ Template    │  │ Examples    │                 │    │
│  │  └─────────────┘  └─────────────┘  └─────────────┘                 │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              AI Model Providers                              │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐       │
│  │   OpenAI    │  │ Anthropic  │  │ 阿里云通义  │  │   Ollama    │       │
│  │  (GPT-4o)   │  │ (Claude-3)  │  │  (Qwen)     │  │  (Local)    │       │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘       │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 与现有AiModel表的兼容性设计

**扩展方案**：在现有 `ai_model` 表基础上增加 SpringAI 相关字段

```sql
-- 扩展现有 ai_model 表
ALTER TABLE ai_model ADD COLUMN model_type VARCHAR(20) DEFAULT 'traditional'
    COMMENT '模型类型（traditional=传统API, springai=SpringAI模型）';
ALTER TABLE ai_model ADD COLUMN api_key_encrypted VARCHAR(500) DEFAULT NULL
    COMMENT '加密后的API Key（SpringAI用）';
ALTER TABLE ai_model ADD COLUMN springai_model_name VARCHAR(100) DEFAULT NULL
    COMMENT 'SpringAI模型名称（如 gpt-4o, claude-3-opus）';
ALTER TABLE ai_model ADD COLUMN default_params JSON DEFAULT NULL
    COMMENT '默认参数（temperature、max_tokens等）';
```

### 4.3 模型服务实现

```java
@Service
@RequiredArgsConstructor
public class ChatModelService {
    private final AiModelMapper modelMapper;
    private final Map<String, ChatModel> modelCache = new ConcurrentHashMap<>();

    /**
     * 获取ChatModel实例（懒加载 + 缓存）
     */
    public ChatModel getChatModel(Long modelId) {
        String cacheKey = "model:" + modelId;

        return modelCache.computeIfAbsent(cacheKey, k -> {
            AiModel model = modelMapper.selectById(modelId);
            if (model == null) {
                throw new BizException("模型不存在: " + modelId);
            }
            return createChatModel(model);
        });
    }

    /**
     * 根据Provider创建对应的ChatModel
     */
    private ChatModel createChatModel(AiModel model) {
        return switch (model.getProvider().toLowerCase()) {
            case "openai" -> createOpenAIModel(model);
            case "anthropic" -> createAnthropicModel(model);
            case "aliyun" -> createAliyunModel(model);
            case "ollama" -> createOllamaModel(model);
            default -> throw new BizException("不支持的模型厂商: " + model.getProvider());
        };
    }

    /**
     * OpenAI模型创建示例
     */
    private ChatModel createOpenAIModel(AiModel model) {
        OpenAiApi api = new OpenAiApi(model.getEndpoint(), model.getApiKeyEncrypted());
        OpenAiChatOptions options = buildOptions(model.getDefaultParams());
        return new OpenAiChatModel(api, options);
    }

    /**
     * 执行Chat调用
     */
    public String chat(Long modelId, String prompt) {
        ChatModel model = getChatModel(modelId);
        ChatResponse response = model.call(new Prompt(prompt));
        return response.getResult().getOutput().getContent();
    }
}
```

### 4.4 Agent与SpringAI绑定

```java
// AiAgent实体新增字段
public class AiAgent {
    // ... 现有字段 ...

    private Long springaiModelId;        // 绑定的SpringAI模型ID
    private String systemPrompt;          // 系统提示词
    private String promptTemplate;        // Prompt模板
    private Integer temperature;          // 生成温度
    private Integer maxTokens;            // 最大Token数
}
```

---

## 5. 数据模型变更设计

### 5.1 新增表清单

| 表名 | 说明 | 优先级 |
|------|------|--------|
| ai_agent_instance | Agent实例表 | P0 |
| ai_agent_heartbeat | Agent心跳表 | P0 |
| ai_heartbeat_log | 心跳日志表 | P1 |
| ai_alert_record | 告警记录表 | P1 |
| ai_a2a_task | A2A任务表 | P0 |
| ai_a2a_auth | Agent调用授权表 | P0 |
| ai_workflow | 工作流表 | P1 |
| ai_workflow_execution | 工作流执行记录表 | P1 |
| ai_springai_model | SpringAI模型配置表 | P0 |
| ai_prompt_template | Prompt模板表 | P1 |

### 5.2 核心表结构设计

#### 5.2.1 ai_agent_instance（Agent实例表）

```sql
CREATE TABLE ai_agent_instance (
    id                  BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '主键ID',
    agent_id            BIGINT          NOT NULL                    COMMENT 'Agent ID',
    instance_id         VARCHAR(100)    NOT NULL                    COMMENT '实例唯一标识',
    version             VARCHAR(20)                         DEFAULT NULL COMMENT '实例版本',
    status              TINYINT         NOT NULL    DEFAULT 1   COMMENT '状态（1=在线 2=离线 3=告警）',
    load                DECIMAL(5,2)                         DEFAULT 0   COMMENT '当前负载（0-1）',
    queue_size          INT                                 DEFAULT 0   COMMENT '队列长度',
    capabilities        JSON                                DEFAULT NULL COMMENT '能力列表',
    metadata            JSON                                DEFAULT NULL COMMENT '元数据',
    last_heartbeat_time DATETIME                                DEFAULT NULL COMMENT '最近心跳时间',
    register_time       DATETIME                                DEFAULT NULL COMMENT '注册时间',
    create_time         DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_instance_id (instance_id),
    KEY idx_agent_id (agent_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent实例表';
```

#### 5.2.2 ai_heartbeat_log（心跳日志表）

```sql
CREATE TABLE ai_heartbeat_log (
    id                  BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '主键ID',
    instance_id         VARCHAR(100)    NOT NULL                    COMMENT '实例ID',
    agent_id            BIGINT          NOT NULL                    COMMENT 'Agent ID',
    heartbeat_type      VARCHAR(20)     NOT NULL                    COMMENT '类型（register/beat/unregister）',
    load                DECIMAL(5,2)                         DEFAULT NULL COMMENT '当时负载',
    queue_size          INT                                 DEFAULT NULL COMMENT '当时队列长度',
    create_time         DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_instance_id (instance_id),
    KEY idx_agent_id (agent_id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='心跳日志表';
```

#### 5.2.3 ai_alert_record（告警记录表）

```sql
CREATE TABLE ai_alert_record (
    id                  BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '主键ID',
    alert_type          VARCHAR(50)     NOT NULL                    COMMENT '告警类型（agent_offline/load_high/heartbeat_timeout）',
    agent_id            BIGINT          NOT NULL                    COMMENT 'Agent ID',
    instance_id         VARCHAR(100)                         DEFAULT NULL COMMENT '实例ID',
    alert_content       TEXT                                DEFAULT NULL COMMENT '告警内容',
    alert_level         VARCHAR(20)                         DEFAULT 'WARNING' COMMENT '告警级别（INFO/WARNING/CRITICAL）',
    status              VARCHAR(20)     NOT NULL    DEFAULT 'triggered' COMMENT '状态（triggered/resolved）',
    trigger_time        DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '触发时间',
    resolve_time        DATETIME                                DEFAULT NULL COMMENT '解决时间',
    create_time         DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_agent_id (agent_id),
    KEY idx_status (status),
    KEY idx_trigger_time (trigger_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警记录表';
```

#### 5.2.4 ai_a2a_task（A2A任务表）

```sql
CREATE TABLE ai_a2a_task (
    id                  BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '主键ID',
    task_id             VARCHAR(50)     NOT NULL                    COMMENT '任务唯一ID',
    session_id          VARCHAR(50)                         DEFAULT NULL COMMENT '会话ID',
    workflow_id         BIGINT                                DEFAULT NULL COMMENT '工作流ID（工作流触发时）',
    source_agent_id     BIGINT          NOT NULL                    COMMENT '源Agent ID',
    target_agent_id     BIGINT          NOT NULL                    COMMENT '目标Agent ID',
    task_type           VARCHAR(50)                         DEFAULT NULL COMMENT '任务类型',
    task_description    TEXT                                DEFAULT NULL COMMENT '任务描述',
    context             JSON                                DEFAULT NULL COMMENT '上下文信息',
    response_format     VARCHAR(20)                         DEFAULT 'json' COMMENT '期望响应格式',
    status              VARCHAR(20)     NOT NULL    DEFAULT 'pending' COMMENT '状态（pending/running/success/failed/timeout/cancelled）',
    result              JSON                                DEFAULT NULL COMMENT '执行结果',
    error_message       TEXT                                DEFAULT NULL COMMENT '错误信息',
    timeout             INT                                 DEFAULT 30000 COMMENT '超时时间（毫秒）',
    retry_count         INT                                 DEFAULT 0   COMMENT '重试次数',
    start_time          DATETIME                                DEFAULT NULL COMMENT '开始时间',
    end_time            DATETIME                                DEFAULT NULL COMMENT '结束时间',
    create_time         DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_id (task_id),
    KEY idx_source_agent (source_agent_id),
    KEY idx_target_agent (target_agent_id),
    KEY idx_status (status),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='A2A任务表';
```

#### 5.2.5 ai_a2a_auth（Agent调用授权表）

```sql
CREATE TABLE ai_a2a_auth (
    id                  BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '主键ID',
    caller_agent_id     BIGINT          NOT NULL                    COMMENT '调用方Agent ID',
    callee_agent_id     BIGINT          NOT NULL                    COMMENT '被调用方Agent ID',
    auth_type           VARCHAR(20)     NOT NULL    DEFAULT 'allow' COMMENT '授权类型（allow/deny）',
    qps_limit           INT             NOT NULL    DEFAULT 100  COMMENT 'QPS限制',
    daily_limit         INT                                 DEFAULT 10000 COMMENT '日调用量上限',
    timeout             INT             NOT NULL    DEFAULT 30000 COMMENT '超时时间（毫秒）',
    status              TINYINT         NOT NULL    DEFAULT 1   COMMENT '状态（0禁用 1启用）',
    create_time         DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_caller_callee (caller_agent_id, callee_agent_id),
    KEY idx_caller_agent (caller_agent_id),
    KEY idx_callee_agent (callee_agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent调用授权表';
```

#### 5.2.6 ai_workflow（工作流表）

```sql
CREATE TABLE ai_workflow (
    id                  BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '主键ID',
    workflow_code       VARCHAR(50)     NOT NULL                    COMMENT '工作流编码（唯一）',
    workflow_name       VARCHAR(100)    NOT NULL                    COMMENT '工作流名称',
    description         TEXT                                DEFAULT NULL COMMENT '描述',
    workflow_config     JSON                                NOT NULL COMMENT '节点配置',
    variables           JSON                                DEFAULT NULL COMMENT '变量定义',
    status              TINYINT         NOT NULL    DEFAULT 0   COMMENT '状态（0草稿 1已发布）',
    version             INT             NOT NULL    DEFAULT 1   COMMENT '版本号',
    create_user_id      BIGINT                                DEFAULT NULL COMMENT '创建人',
    publish_time        DATETIME                                DEFAULT NULL COMMENT '发布时间',
    create_time         DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_code (workflow_code),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流表';
```

#### 5.2.7 ai_workflow_execution（工作流执行记录表）

```sql
CREATE TABLE ai_workflow_execution (
    id                  BIGINT          NOT NULL    AUTO_INCREMENT  COMMENT '主键ID',
    execution_id        VARCHAR(50)     NOT NULL                    COMMENT '执行ID',
    workflow_id         BIGINT          NOT NULL                    COMMENT '工作流ID',
    trigger_type        VARCHAR(20)     NOT NULL                    COMMENT '触发类型（manual/api/schedule）',
    trigger_params      JSON                                DEFAULT NULL COMMENT '触发参数',
    status              VARCHAR(20)     NOT NULL    DEFAULT 'running' COMMENT '状态（pending/running/success/failed）',
    node_executions     JSON                                DEFAULT NULL COMMENT '各节点执行结果',
    start_time          DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    end_time            DATETIME                                DEFAULT NULL COMMENT '结束时间',
    error_message       TEXT                                DEFAULT NULL COMMENT '错误信息',
    create_time         DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_execution_id (execution_id),
    KEY idx_workflow_id (workflow_id),
    KEY idx_status (status),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流执行记录表';
```

---

## 6. API 设计

### 6.1 Agent心跳相关接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/agent-heartbeat/register | Agent注册 |
| POST | /api/v1/agent-heartbeat/beat | 心跳上报 |
| POST | /api/v1/agent-heartbeat/unregister | Agent注销 |

### 6.2 监控相关接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/v1/monitor/agent-status | Agent在线状态列表 |
| GET | /api/v1/monitor/agent-status/{instanceId} | 实例状态详情 |
| GET | /api/v1/monitor/agent-alerts | 告警记录查询 |
| PUT | /api/v1/monitor/agent-alerts/{id}/resolve | 标记告警已解决 |
| POST | /api/v1/monitor/health-check/{agentId} | 触发健康检查 |
| GET | /api/v1/monitor/dashboard/stats | 监控统计数据 |

### 6.3 A2A相关接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/a2a/call | 发起A2A调用 |
| GET | /api/v1/a2a/tasks | 查询任务列表 |
| GET | /api/v1/a2a/tasks/{taskId} | 查询任务详情 |
| POST | /api/v1/a2a/tasks/{taskId}/cancel | 取消任务 |
| POST | /api/v1/a2a/tasks/{taskId}/retry | 重试任务 |
| POST | /api/v1/a2a/workflows | 创建工作流 |
| GET | /api/v1/a2a/workflows | 查询工作流列表 |
| GET | /api/v1/a2a/workflows/{id} | 查询工作流详情 |
| PUT | /api/v1/a2a/workflows/{id} | 更新工作流 |
| DELETE | /api/v1/a2a/workflows/{id} | 删除工作流 |
| POST | /api/v1/a2a/workflows/{id}/publish | 发布工作流 |
| POST | /api/v1/a2a/workflows/{id}/execute | 执行工作流 |
| GET | /api/v1/a2a/workflows/{id}/executions | 查询执行记录 |
| POST | /api/v1/a2a/auth | 创建调用授权 |
| GET | /api/v1/a2a/auth | 查询授权列表 |
| DELETE | /api/v1/a2a/auth/{id} | 删除授权 |

### 6.4 SpringAI相关接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/springai/models | 添加SpringAI模型配置 |
| GET | /api/v1/springai/models | 查询模型列表 |
| PUT | /api/v1/springai/models/{id} | 更新模型配置 |
| DELETE | /api/v1/springai/models/{id} | 删除模型配置 |
| POST | /api/v1/springai/models/{id}/test | 测试模型连接 |
| POST | /api/v1/springai/prompts | 创建Prompt模板 |
| GET | /api/v1/springai/prompts | 查询模板列表 |
| PUT | /api/v1/springai/prompts/{id} | 更新模板 |
| DELETE | /api/v1/springai/prompts/{id} | 删除模板 |
| POST | /api/v1/springai/prompts/{id}/preview | 预览模板效果 |
| POST | /api/v1/springai/chat | Chat对话接口 |

---

## 7. 部署架构建议

### 7.1 推荐部署架构

```
                              ┌─────────────────┐
                              │   Nginx /       │
                              │   API Gateway    │
                              │   (负载均衡)      │
                              └────────┬────────┘
                                       │
                    ┌──────────────────┼──────────────────┐
                    │                  │                  │
               ┌────▼────┐        ┌────▼────┐        ┌────▼────┐
               │ Backend  │        │ Backend  │        │ Backend  │
               │ Instance1│        │ Instance2│        │ Instance3│
               │ (Spring  │        │ (Spring  │        │ (Spring  │
               │  Boot)   │        │  Boot)   │        │  Boot)  │
               └────┬────┘        └────┬────┘        └────┬────┘
                    │                  │                  │
                    └──────────────────┼──────────────────┘
                                       │
               ┌───────────────────────┼───────────────────────┐
               │                       │                       │
          ┌────▼────┐            ┌──────▼──────┐        ┌──────▼──────┐
          │  MySQL   │            │    Redis     │        │  Redis      │
          │ (主从)   │            │  (Session)   │        │  (Stream)   │
          └─────────┘            └──────────────┘        └──────────────┘
```

### 7.2 Docker Compose 快速部署

```yaml
# docker-compose.yml
version: '3.8'
services:
  backend:
    build: ./backend
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/ai_platform
      - SPRING_REDIS_HOST=redis
    depends_on:
      - mysql
      - redis
    deploy:
      replicas: 2

  mysql:
    image: mysql:8.0
    environment:
      - MYSQL_ROOT_PASSWORD=password
      - MYSQL_DATABASE=ai_platform
    volumes:
      - mysql_data:/var/lib/mysql

  redis:
    image: redis:7-alpine
    volumes:
      - redis_data:/data

volumes:
  mysql_data:
  redis_data:
```

### 7.3 性能优化建议

| 优化项 | 建议 |
|--------|------|
| 心跳写入 | 使用Redis缓存 + 批量异步写入MySQL，避免频繁IO |
| WebSocket | 使用Redis Pub/Sub进行集群内消息同步 |
| A2A消息 | Redis Stream消费组模式，支持多实例并行消费 |
| 模型缓存 | ChatModel实例缓存，避免重复创建开销 |
| 连接池 | Redis连接池合理配置（Lettuce默认已启用） |

---

## 8. 项目模块划分

```
backend/
├── src/main/java/com/aipal/
│   ├── controller/
│   │   ├── AgentController.java          # Agent管理
│   │   ├── HeartbeatController.java      # 心跳接口
│   │   ├── MonitorController.java        # 监控接口
│   │   ├── A2AController.java             # A2A接口
│   │   └── SpringAIController.java        # SpringAI接口
│   │
│   ├── service/
│   │   ├── AgentService.java              # Agent服务
│   │   ├── HeartbeatService.java          # 心跳服务
│   │   ├── MonitorService.java            # 监控服务
│   │   ├── A2AMessageService.java          # A2A消息服务
│   │   ├── A2AAuthService.java            # A2A权限服务
│   │   ├── WorkflowService.java           # 工作流服务
│   │   ├── ChatModelService.java          # ChatModel服务
│   │   └── PromptTemplateService.java     # Prompt模板服务
│   │
│   ├── entity/
│   │   ├── AiAgent.java                   # Agent实体
│   │   ├── AiAgentInstance.java           # Agent实例实体
│   │   ├── AiAgentHeartbeat.java          # 心跳实体
│   │   ├── A2ATask.java                   # A2A任务实体
│   │   ├── A2AAuth.java                   # A2A授权实体
│   │   ├── Workflow.java                  # 工作流实体
│   │   ├── WorkflowExecution.java        # 工作流执行实体
│   │   └── AlertRecord.java               # 告警记录实体
│   │
│   ├── mapper/
│   │   ├── AiAgentMapper.java
│   │   ├── AiAgentInstanceMapper.java
│   │   ├── AiAgentHeartbeatMapper.java
│   │   ├── A2ATaskMapper.java
│   │   ├── A2AAuthMapper.java
│   │   ├── WorkflowMapper.java
│   │   └── AlertRecordMapper.java
│   │
│   ├── config/
│   │   ├── RedisConfig.java               # Redis配置
│   │   ├── WebSocketConfig.java          # WebSocket配置
│   │   ├── WebMvcConfig.java              # Web配置
│   │   └── SpringAIConfig.java            # SpringAI配置
│   │
│   ├── dto/
│   │   ├── HeartbeatRequest.java         # 心跳请求
│   │   ├── A2AMessage.java                # A2A消息
│   │   ├── A2ACallRequest.java            # A2A调用请求
│   │   ├── WorkflowConfig.java            # 工作流配置
│   │   └── ChatRequest.java               # Chat请求
│   │
│   └── schedule/
│       ├── HeartbeatMonitorJob.java       # 心跳检测定时任务
│       └── MetricsAggregationJob.java     # 指标聚合定时任务
```

---

## 9. 实现优先级

### Phase 1: 核心功能（P0）

| 任务 | 负责人 | 工期 | 依赖 |
|------|--------|------|------|
| SpringAI模型配置管理 | backend-dev | 2天 | 无 |
| ChatModelService实现 | backend-dev | 2天 | SpringAI配置 |
| Agent心跳注册接口 | backend-dev | 1天 | 无 |
| HeartbeatService实现 | backend-dev | 2天 | 心跳接口 |
| A2A任务调度核心 | backend-dev | 3天 | Agent服务 |
| A2AAuth权限校验 | backend-dev | 1天 | A2A任务 |

### Phase 2: 重要功能（P1）

| 任务 | 负责人 | 工期 | 依赖 |
|------|--------|------|------|
| WebSocket实时推送 | backend-dev | 2天 | HeartbeatService |
| 离线告警功能 | backend-dev | 2天 | HeartbeatService |
| 工作流编排引擎 | backend-dev | 3天 | A2A核心 |
| Prompt模板管理 | backend-dev | 2天 | SpringAI配置 |

### Phase 3: 优化功能（P2）

| 任务 | 负责人 | 工期 | 依赖 |
|------|--------|------|------|
| 监控Dashboard | frontend-dev | 3天 | API完成 |
| A2A可视化编排 | frontend-dev | 3天 | 工作流引擎 |
| 健康检查功能 | backend-dev | 1天 | HeartbeatService |

---

## 10. 风险与应对

| 风险 | 影响 | 应对措施 |
|------|------|----------|
| SpringAI版本迭代 | 框架API可能变化 | 封装抽象层ChatModelService，解耦业务与框架 |
| 心跳高频写入MySQL | 数据库压力 | Redis缓存 + 批量异步写入 |
| A2A循环调用 | 资源耗尽 | 调用链路深度限制 + QPS限制 |
| WebSocket集群扩展 | 消息同步问题 | Redis Pub/Sub广播 |
| 多模型响应格式差异 | 解析失败 | 统一Response结构 |

---

## 11. 参考文档

- [SpringAI Official Documentation](https://docs.spring.io/spring-ai/reference/)
- [Redis Streams Documentation](https://redis.io/docs/data-types/streams/)
- [WebSocket with Spring Boot](https://docs.spring.io/spring-boot/reference/web-sockets.html)
- [v2.0需求文档](./requirements-v2.md)
- [SpringAI技术方案](./technical-design-springai.md)
