# Agent 动态注册与监控方案 - 架构设计文档

**版本**: v1.0
**日期**: 2026-04-24
**作者**: 架构师
**状态**: 已完成

---

## 1. 架构总览

### 1.1 架构图

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              AI Platform                                        │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌─────────────────┐     ┌──────────────────┐     ┌────────────────────────┐   │
│  │  Agent Registry  │────▶│ Heartbeat Service │────▶│  Event Notification   │   │
│  │  (内存 + DB)     │     │  (30s/90s)        │     │  (Spring Application   │   │
│  │                 │     │                   │     │   Event)               │   │
│  └────────┬────────┘     └──────────────────┘     └────────────────────────┘   │
│           │                                                                   │
│           ▼                                                                   │
│  ┌─────────────────┐     ┌──────────────────┐     ┌────────────────────────┐   │
│  │  A2AMessage     │────▶│   Monitor Service │────▶│  Agent Graph          │   │
│  │  Service        │     │   (数据采集)       │     │  (前端展示)            │   │
│  └─────────────────┘     └──────────────────┘     └────────────────────────┘   │
│                                                                                 │
│  ┌─────────────────┐     ┌──────────────────┐                                  │
│  │ Workflow Engine │────▶│  Task Scheduler   │                                  │
│  │ (编排执行)       │     │  (调度触发)        │                                  │
│  └─────────────────┘     └──────────────────┘                                  │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
           │                                        ▲
           ▼                                        │
┌──────────────────────┐              ┌──────────────────────┐
│   TtsAgent           │              │   MarketingAgent    │
│   (自注册)            │              │   (自注册)           │
│   /health            │              │   /health           │
└──────────────────────┘              └──────────────────────┘
           │                                        ▲
           │           ┌──────────────────┐          │
           └──────────▶│   Pull Probe     │◀────────┘
                       │   (定时探测)      │
                       └──────────────────┘
```

### 1.2 核心设计原则

1. **Push + Pull 双通道注册**: Agent 启动时自注册（Push），平台定时探测 /health 端点（Pull）
2. **心跳与注册联动**: 心跳超时自动触发注销事件，多实例支持
3. **事件驱动架构**: 使用 Spring ApplicationEvent 实现注册/注销事件通知
4. **多实例兼容**: 使用 Redis 分布式锁保证心跳检测的互斥性

---

## 2. 数据模型设计

### 2.1 实体类 (Entity)

#### 2.1.1 AgentRegistration（注册信息）

```java
@Data
@TableName("ai_agent_registration")
public class AgentRegistration {
    @TableId(type = IdType.AUTO)
    private Long id;

    // Agent 基础信息
    private String agentCode;           // Agent 唯一编码
    private String agentName;           // Agent 名称
    private String description;        // 能力描述
    private String category;            // 分类

    // 注册方式: PUSH(推送注册) / PULL(平台探测)
    private String registryType;

    // API 信息
    private String apiUrl;             // Agent API 地址
    private String healthEndpoint;      // 健康检查端点（默认 /health）
    private String requestSchema;      // 请求Schema
    private String responseSchema;      // 响应Schema

    // 实例管理（支持多实例）
    private String instanceId;          // 实例ID（用于多实例部署）

    // 心跳配置
    private Integer heartbeatInterval;  // 心跳间隔（秒），默认 30
    private Integer heartbeatTimeout;    // 心跳超时（秒），默认 90

    // 状态: 0-待激活, 1-在线, 2-离线, 3-已注销
    private Integer status;

    // 元数据
    private Long ownerId;
    private LocalDateTime lastHeartbeat;  // 最后心跳时间
    private LocalDateTime registeredTime;  // 注册时间
    private LocalDateTime unregisteredTime; // 注销时间
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
```

#### 2.1.2 AgentRegistrationEvent（注册事件记录）

```java
@Data
@TableName("ai_agent_registration_event")
public class AgentRegistrationEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String agentCode;
    private String instanceId;
    private String eventType;           // REGISTER / UNREGISTER / HEARTBEAT_TIMEOUT / STATUS_CHANGE
    private Integer previousStatus;
    private Integer currentStatus;
    private String eventData;          // 事件详情（JSON）
    private String source;             // 事件来源：PUSH_API / PULL_PROBE / HEARTBEAT_TIMEOUT
    private LocalDateTime createTime;
}
```

#### 2.1.3 Workflow（编排配置）

```java
@Data
@TableName("ai_workflow")
public class Workflow {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String workflowCode;       // 编排编码
    private String workflowName;       // 编排名称
    private String description;        // 编排描述
    private String triggerType;        // 触发类型: MANUAL / SCHEDULE / EVENT
    private String triggerConfig;     // 触发配置（JSON）
    private String workflowDefinition; // 编排定义（JSON）
    private Integer status;            // 状态: 0-禁用, 1-启用
    private Long ownerId;
    private LocalDateTime lastTriggerTime;
    private Integer triggerCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
```

#### 2.1.4 WorkflowExecution（编排执行记录）

```java
@Data
@TableName("ai_workflow_execution")
public class WorkflowExecution {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String executionId;        // 执行ID
    private Long workflowId;
    private String triggerType;        // 触发类型
    private String triggerSource;      // 触发来源
    private String status;             // PENDING / RUNNING / COMPLETED / FAILED / CANCELLED
    private String startParams;        // 启动参数（JSON）
    private String executionContext;  // 执行上下文（JSON）
    private String result;            // 执行结果
    private String errorMessage;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
}
```

### 2.2 DTO 设计

#### 2.2.1 注册请求/响应 DTO

```java
// Agent Push 注册请求
@Data
public class AgentRegisterRequest {
    @NotBlank(message = "agentCode 不能为空")
    private String agentCode;

    @NotBlank(message = "agentName 不能为空")
    private String agentName;

    private String description;
    private String category;
    private String apiUrl;
    private String healthEndpoint;
    private String requestSchema;
    private String responseSchema;

    @NotBlank(message = "instanceId 不能为空")
    private String instanceId;

    private Integer heartbeatInterval;  // 可选，默认 30
    private Integer heartbeatTimeout;  // 可选，默认 90
}

// 注册响应
@Data
public class AgentRegisterResponse {
    private Boolean success;
    private String agentCode;
    private String instanceId;
    private String message;
    private LocalDateTime registeredTime;
    private Integer heartbeatInterval;
    private Integer heartbeatTimeout;
}
```

#### 2.2.2 心跳请求 DTO

```java
@Data
public class HeartbeatRequest {
    @NotBlank(message = "agentCode 不能为空")
    private String agentCode;

    private String instanceId;  // 可选，默认 "default"

    @Min(0) @Max(100)
    private Integer healthScore;  // 健康评分 0-100
    private String endpoint;      // 当前健康端点
    private Map<String, Object> metadata;  // 额外元数据
}
```

#### 2.2.3 图谱数据 DTO

```java
@Data
public class AgentGraphDto {
    private Long id;
    private String agentCode;
    private String agentName;
    private String category;
    private Integer status;           // 0-离线, 1-在线
    private String instanceId;
    private LocalDateTime lastHeartbeat;
    private Integer instanceCount;   // 实例数量
    private Double healthScore;
    private String apiUrl;
}

@Data
public class AgentEdgeDto {
    private Long sourceAgentId;
    private Long targetAgentId;
    private String sourceAgentCode;
    private String targetAgentCode;
    private Long callCount;
    private Double avgResponseTime;
    private LocalDateTime lastCallTime;
}
```

---

## 3. 服务接口设计

### 3.1 AgentRegistryService（注册服务接口）

```java
public interface AgentRegistryService {

    /**
     * Push 模式注册 Agent
     * @param request 注册请求
     * @return 注册响应
     */
    AgentRegisterResponse register(AgentRegisterRequest request);

    /**
     * 注销 Agent
     * @param agentCode Agent 编码
     * @param instanceId 实例ID
     */
    void unregister(String agentCode, String instanceId);

    /**
     * Pull 模式探测 Agent 健康状态
     * @param agentCode Agent 编码
     * @param instanceId 实例ID
     * @return 是否在线
     */
    boolean probeAgent(String agentCode, String instanceId);

    /**
     * 获取 Agent 注册信息
     */
    AgentRegistration getRegistration(String agentCode, String instanceId);

    /**
     * 获取所有注册的 Agent
     */
    List<AgentRegistration> getAllRegistrations();

    /**
     * 刷新注册列表（从数据库加载）
     */
    void refreshRegistrations();

    /**
     * 更新 Agent 状态
     */
    void updateStatus(String agentCode, String instanceId, Integer status);
}
```

### 3.2 HeartbeatManagementService（心跳管理接口）

```java
public interface HeartbeatManagementService {

    /**
     * 记录心跳
     * @param request 心跳请求
     */
    void recordHeartbeat(HeartbeatRequest request);

    /**
     * 检测离线 Agent（定时任务）
     */
    void detectOfflineAgents();

    /**
     * 检查 Agent 是否在线
     */
    boolean isAgentOnline(String agentCode, String instanceId);

    /**
     * 获取 Agent 心跳信息
     */
    AgentHeartbeat getHeartbeat(String agentCode, String instanceId);

    /**
     * 获取 Agent 所有实例的心跳
     */
    List<AgentHeartbeat> getAgentInstances(String agentCode);
}
```

### 3.3 AgentEventService（事件通知接口）

```java
public interface AgentEventService {

    /**
     * 发布 Agent 注册事件
     */
    void publishRegisterEvent(AgentRegistration registration);

    /**
     * 发布 Agent 注销事件
     */
    void publishUnregisterEvent(AgentRegistration registration);

    /**
     * 发布心跳超时事件
     */
    void publishHeartbeatTimeoutEvent(String agentCode, String instanceId);

    /**
     * 发布 Agent 状态变更事件
     */
    void publishStatusChangeEvent(String agentCode, String instanceId,
                                   Integer previousStatus, Integer currentStatus);

    /**
     * 获取 Agent 事件历史
     */
    List<AgentRegistrationEvent> getEventHistory(String agentCode, String instanceId,
                                                  LocalDateTime startTime, LocalDateTime endTime);
}
```

### 3.4 WorkflowExecutionService（编排执行接口）

```java
public interface WorkflowExecutionService {

    /**
     * 创建编排执行
     */
    String createExecution(Long workflowId, String triggerType, String triggerSource,
                          String startParams);

    /**
     * 启动编排执行
     */
    void startExecution(String executionId);

    /**
     * 执行编排步骤
     */
    void executeStep(String executionId, WorkflowStep step);

    /**
     * 获取执行状态
     */
    WorkflowExecution getExecution(String executionId);

    /**
     * 取消执行
     */
    void cancelExecution(String executionId);

    /**
     * 触发编排（支持手动/定时/事件触发）
     */
    void triggerWorkflow(Long workflowId, String triggerType, Map<String, Object> params);
}
```

---

## 4. 事件通知机制

### 4.1 Spring Event 事件类

```java
// Agent 注册事件
public class AgentRegisteredEvent extends ApplicationEvent {
    private final AgentRegistration registration;

    public AgentRegisteredEvent(Object source, AgentRegistration registration) {
        super(source);
        this.registration = registration;
    }
}

// Agent 注销事件
public class AgentUnregisteredEvent extends ApplicationEvent {
    private final AgentRegistration registration;

    public AgentUnregisteredEvent(Object source, AgentRegistration registration) {
        super(source);
        this.registration = registration;
    }
}

// 心跳超时事件
public class HeartbeatTimeoutEvent extends ApplicationEvent {
    private final String agentCode;
    private final String instanceId;
    private final LocalDateTime lastHeartbeat;

    public HeartbeatTimeoutEvent(Object source, String agentCode, String instanceId,
                                  LocalDateTime lastHeartbeat) {
        super(source);
        this.agentCode = agentCode;
        this.instanceId = instanceId;
        this.lastHeartbeat = lastHeartbeat;
    }
}

// Agent 状态变更事件
public class AgentStatusChangedEvent extends ApplicationEvent {
    private final String agentCode;
    private final String instanceId;
    private final Integer previousStatus;
    private final Integer currentStatus;

    public AgentStatusChangedEvent(Object source, String agentCode, String instanceId,
                                   Integer previousStatus, Integer currentStatus) {
        super(source);
        this.agentCode = agentCode;
        this.instanceId = instanceId;
        this.previousStatus = previousStatus;
        this.currentStatus = currentStatus;
    }
}
```

### 4.2 事件监听器示例

```java
@Slf4j
@Component
public class AgentEventListener {

    private final AgentRegistryService agentRegistryService;
    private final A2AMessageService a2aMessageService;

    @EventListener
    public void onAgentRegistered(AgentRegisteredEvent event) {
        AgentRegistration registration = event.getRegistration();
        log.info("Agent registered: {} [{}]", registration.getAgentCode(),
                 registration.getInstanceId());

        // 1. 注册 A2A Handler
        registerA2AHandler(registration);

        // 2. 通知图谱更新
        notifyGraphUpdate(registration, "REGISTER");
    }

    @EventListener
    public void onAgentUnregistered(AgentUnregisteredEvent event) {
        AgentRegistration registration = event.getRegistration();
        log.info("Agent unregistered: {} [{}]", registration.getAgentCode(),
                 registration.getInstanceId());

        // 1. 注销 A2A Handler
        unregisterA2AHandler(registration);

        // 2. 通知图谱更新
        notifyGraphUpdate(registration, "UNREGISTER");
    }

    @EventListener
    public void onHeartbeatTimeout(HeartbeatTimeoutEvent event) {
        log.warn("Heartbeat timeout for agent: {} [{}]",
                 event.getAgentCode(), event.getInstanceId());

        // 自动注销 Agent
        agentRegistryService.updateStatus(event.getAgentCode(),
                                          event.getInstanceId(), 2); // 2=离线
    }

    @EventListener
    public void onAgentStatusChanged(AgentStatusChangedEvent event) {
        log.info("Agent status changed: {} [{}] {} -> {}",
                 event.getAgentCode(), event.getInstanceId(),
                 event.getPreviousStatus(), event.getCurrentStatus());

        // 通知图谱更新
        notifyGraphUpdate(event.getAgentCode(), event.getInstanceId(),
                          event.getCurrentStatus(), "STATUS_CHANGE");
    }
}
```

---

## 5. 核心流程图

### 5.1 Agent Push 注册流程

```
┌─────────┐    ┌──────────────┐    ┌─────────────────┐    ┌──────────────────┐
│ Agent   │───▶│ POST /api/v1 │───▶│ AgentRegistry   │───▶│ AgentRegistry    │
│ Start   │    │ /agents      │    │ Controller      │    │ Service          │
└─────────┘    └──────────────┘    └─────────────────┘    └────────┬─────────┘
                                                                    │
                     ┌───────────────────────────────────────────────┘
                     ▼
              ┌───────────────┐    ┌─────────────────┐    ┌────────────────┐
              │ 保存到数据库   │───▶│ 发布注册事件     │───▶│ EventListener │
              │ ai_agent      │    │                 │    │ 处理           │
              └───────────────┘    └─────────────────┘    └────────┬───────┘
                                                                    │
                     ┌───────────────────────────────────────────────┘
                     ▼
              ┌───────────────┐    ┌─────────────────┐
              │ 注册 A2A      │    │ 记录事件日志     │
              │ Handler       │    │ ai_agent_event  │
              └───────────────┘    └─────────────────┘
```

### 5.2 Pull 模式探测流程

```
┌──────────────┐    ┌─────────────────┐    ┌──────────────────┐
│ 定时任务      │───▶│ PullProbeService│───▶│ 获取待探测列表   │
│ (每30s)      │    │                 │    │ status=1的Agent  │
└──────────────┘    └─────────────────┘    └────────┬─────────┘
                                                    │
                     ┌──────────────────────────────┘
                     ▼
              ┌───────────────┐    ┌─────────────────┐    ┌────────────────┐
              │ 探测 /health  │───▶│ 成功: 更新心跳  │    │ 失败: 标记超时 │
              │ 端点          │    │                 │    │ 发布事件       │
              └───────────────┘    └─────────────────┘    └────────────────┘
```

### 5.3 心跳超时检测流程

```
┌──────────────┐    ┌─────────────────┐    ┌──────────────────┐
│ 定时任务      │───▶│ HeartbeatService│───▶│ 扫描 Redis       │
│ (每30s)      │    │ detectOffline   │    │ agent:heartbeat:*│
└──────────────┘    └─────────────────┘    └────────┬─────────┘
                                                    │
                     ┌──────────────────────────────┘
                     ▼
              ┌───────────────┐    ┌─────────────────┐
              │ 超时检测      │───▶│ 标记 status=2   │
              │ (90s)         │    │ 发布超时事件     │
              └───────────────┘    └─────────────────┘
```

### 5.4 A2A 消息流转与图谱数据采集

```
┌─────────┐    ┌──────────────┐    ┌─────────────────┐    ┌──────────────────┐
│ Agent A │───▶│ sendMessage  │───▶│ A2AMessage      │───▶│ 写入 ai_a2a_task │
│         │    │              │    │ Service         │    │                  │
└─────────┘    └──────────────┘    └─────────────────┘    └────────┬─────────┘
                                                                     │
                     ┌────────────────────────────────────────────────┘
                     ▼
              ┌───────────────┐    ┌─────────────────┐    ┌────────────────┐
              │ traceId       │───▶│ 调用链追踪       │───▶│ MonitorService │
              │ 传递         │    │                 │    │ 获取图谱数据   │
              └───────────────┘    └─────────────────┘    └────────────────┘
```

### 5.5 编排触发流程

```
┌─────────────────────────────────────────────────────────────────┐
│                        触发类型                                   │
├─────────────────┬─────────────────┬─────────────────────────────┤
│   MANUAL        │   SCHEDULE       │   EVENT                    │
│   (手动触发)     │   (定时触发)      │   (事件触发)                │
└────────┬────────┴────────┬────────┴──────────────┬────────────┘
         │                 │                        │
         ▼                 ▼                        ▼
┌─────────────────┐ ┌─────────────────┐  ┌─────────────────────────┐
│ 用户点击触发     │ │ @Scheduled      │  │ AgentStatusChangedEvent  │
│                 │ │ Cron 表达式     │  │ 等事件触发                │
└────────┬────────┘ └────────┬────────┘  └─────────────┬───────────┘
         │                 │                          │
         └─────────────────┼──────────────────────────┘
                           ▼
                  ┌─────────────────┐
                  │ WorkflowEngine │
                  │ triggerWorkflow │
                  └────────┬────────┘
                           │
         ┌─────────────────┼─────────────────┐
         ▼                 ▼                 ▼
  ┌─────────────┐   ┌─────────────┐   ┌─────────────┐
  │ 创建执行记录  │   │ 解析编排定义 │   │ 依次执行步骤 │
  │             │   │             │   │             │
  └─────────────┘   └─────────────┘   └─────────────┘
```

---

## 6. 数据库表设计

### 6.1 ai_agent_registration（注册信息表）

```sql
CREATE TABLE ai_agent_registration (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_code VARCHAR(64) NOT NULL COMMENT 'Agent编码',
    agent_name VARCHAR(128) NOT NULL COMMENT 'Agent名称',
    description VARCHAR(512) COMMENT '能力描述',
    category VARCHAR(64) COMMENT '分类',
    registry_type VARCHAR(16) NOT NULL DEFAULT 'PUSH' COMMENT '注册方式: PUSH/PULL',
    api_url VARCHAR(256) COMMENT 'API地址',
    health_endpoint VARCHAR(128) DEFAULT '/health' COMMENT '健康检查端点',
    request_schema TEXT COMMENT '请求Schema',
    response_schema TEXT COMMENT '响应Schema',
    instance_id VARCHAR(64) NOT NULL DEFAULT 'default' COMMENT '实例ID',
    heartbeat_interval INT DEFAULT 30 COMMENT '心跳间隔(秒)',
    heartbeat_timeout INT DEFAULT 90 COMMENT '心跳超时(秒)',
    status TINYINT DEFAULT 0 COMMENT '状态: 0-待激活, 1-在线, 2-离线, 3-已注销',
    owner_id BIGINT COMMENT '所有者ID',
    last_heartbeat DATETIME COMMENT '最后心跳时间',
    registered_time DATETIME COMMENT '注册时间',
    unregistered_time DATETIME COMMENT '注销时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_agent_instance (agent_code, instance_id),
    INDEX idx_status (status),
    INDEX idx_registry_type (registry_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 6.2 ai_agent_registration_event（注册事件表）

```sql
CREATE TABLE ai_agent_registration_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_code VARCHAR(64) NOT NULL COMMENT 'Agent编码',
    instance_id VARCHAR(64) NOT NULL DEFAULT 'default',
    event_type VARCHAR(32) NOT NULL COMMENT '事件类型',
    previous_status TINYINT COMMENT '变更前状态',
    current_status TINYINT COMMENT '变更后状态',
    event_data JSON COMMENT '事件详情',
    source VARCHAR(32) COMMENT '事件来源',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_agent_time (agent_code, create_time),
    INDEX idx_event_type (event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 6.3 ai_workflow（编排配置表）

```sql
CREATE TABLE ai_workflow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_code VARCHAR(64) NOT NULL COMMENT '编排编码',
    workflow_name VARCHAR(128) NOT NULL COMMENT '编排名称',
    description VARCHAR(512) COMMENT '编排描述',
    trigger_type VARCHAR(16) NOT NULL COMMENT '触发类型: MANUAL/SCHEDULE/EVENT',
    trigger_config JSON COMMENT '触发配置',
    workflow_definition JSON NOT NULL COMMENT '编排定义',
    status TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    owner_id BIGINT,
    last_trigger_time DATETIME COMMENT '最后触发时间',
    trigger_count INT DEFAULT 0 COMMENT '触发次数',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_workflow_code (workflow_code),
    INDEX idx_trigger_type (trigger_type),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 6.4 ai_workflow_execution（编排执行记录表）

```sql
CREATE TABLE ai_workflow_execution (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    execution_id VARCHAR(64) NOT NULL COMMENT '执行ID',
    workflow_id BIGINT NOT NULL COMMENT '编排ID',
    trigger_type VARCHAR(16) NOT NULL COMMENT '触发类型',
    trigger_source VARCHAR(128) COMMENT '触发来源',
    status VARCHAR(16) NOT NULL COMMENT '状态',
    start_params JSON COMMENT '启动参数',
    execution_context JSON COMMENT '执行上下文',
    result TEXT COMMENT '执行结果',
    error_message TEXT COMMENT '错误信息',
    start_time DATETIME COMMENT '开始时间',
    end_time DATETIME COMMENT '结束时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_execution_id (execution_id),
    INDEX idx_workflow_status (workflow_id, status),
    INDEX idx_trigger_type (trigger_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 7. API 接口设计

### 7.1 Agent 注册 Controller

```java
@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
@Slf4j
public class AgentRegistryController {

    private final AgentRegistryService agentRegistryService;

    /**
     * Push 模式注册 Agent
     */
    @PostMapping
    public ApiResponse<AgentRegisterResponse> register(@Valid @RequestBody AgentRegisterRequest request) {
        log.info("Received agent registration request: {}", request.getAgentCode());
        AgentRegisterResponse response = agentRegistryService.register(request);
        return ApiResponse.success(response);
    }

    /**
     * 注销 Agent
     */
    @DeleteMapping("/{agentCode}")
    public ApiResponse<Void> unregister(@PathVariable String agentCode,
                                        @RequestParam(defaultValue = "default") String instanceId) {
        log.info("Unregistering agent: {} [{}]", agentCode, instanceId);
        agentRegistryService.unregister(agentCode, instanceId);
        return ApiResponse.success();
    }

    /**
     * 获取 Agent 注册信息
     */
    @GetMapping("/{agentCode}")
    public ApiResponse<AgentRegistration> getRegistration(
            @PathVariable String agentCode,
            @RequestParam(defaultValue = "default") String instanceId) {
        AgentRegistration registration = agentRegistryService.getRegistration(agentCode, instanceId);
        return ApiResponse.success(registration);
    }

    /**
     * 获取所有已注册的 Agent
     */
    @GetMapping
    public ApiResponse<List<AgentRegistration>> getAllRegistrations() {
        List<AgentRegistration> registrations = agentRegistryService.getAllRegistrations();
        return ApiResponse.success(registrations);
    }

    /**
     * 手动刷新注册列表
     */
    @PostMapping("/refresh")
    public ApiResponse<Void> refreshRegistrations() {
        agentRegistryService.refreshRegistrations();
        return ApiResponse.success();
    }

    /**
     * 心跳上报
     */
    @PostMapping("/heartbeat")
    public ApiResponse<Void> heartbeat(@Valid @RequestBody HeartbeatRequest request) {
        HeartbeatManagementService recordHeartbeat = null; // 获取心跳服务
        // TODO: 实现心跳记录
        return ApiResponse.success();
    }
}
```

### 7.2 Workflow Controller

```java
@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
@Slf4j
public class WorkflowController {

    private final WorkflowService workflowService;
    private final WorkflowExecutionService executionService;

    /**
     * 创建编排配置
     */
    @PostMapping
    public ApiResponse<Workflow> createWorkflow(@Valid @RequestBody WorkflowRequest request) {
        Workflow workflow = workflowService.createWorkflow(request);
        return ApiResponse.success(workflow);
    }

    /**
     * 触发编排执行
     */
    @PostMapping("/{workflowId}/trigger")
    public ApiResponse<String> triggerWorkflow(@PathVariable Long workflowId,
                                                @RequestParam(defaultValue = "MANUAL") String triggerType,
                                                @RequestBody(required = false) Map<String, Object> params) {
        String executionId = executionService.triggerWorkflow(workflowId, triggerType, params);
        return ApiResponse.success(executionId);
    }

    /**
     * 获取编排执行状态
     */
    @GetMapping("/executions/{executionId}")
    public ApiResponse<WorkflowExecution> getExecution(@PathVariable String executionId) {
        WorkflowExecution execution = executionService.getExecution(executionId);
        return ApiResponse.success(execution);
    }

    /**
     * 取消执行
     */
    @PostMapping("/executions/{executionId}/cancel")
    public ApiResponse<Void> cancelExecution(@PathVariable String executionId) {
        executionService.cancelExecution(executionId);
        return ApiResponse.success();
    }
}
```

---

## 8. 多实例部署考虑

### 8.1 分布式锁

心跳检测使用 Redis 分布式锁保证互斥：

```java
@Component
@RequiredArgsConstructor
public class DistributedLock {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String LOCK_PREFIX = "lock:";

    public boolean tryLock(String key, long timeoutSeconds) {
        String lockKey = LOCK_PREFIX + key;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, Thread.currentThread().getId(), timeoutSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(acquired);
    }

    public void unlock(String key) {
        String lockKey = LOCK_PREFIX + key;
        redisTemplate.delete(lockKey);
    }
}
```

### 8.2 实例级别隔离

- **Redis Key**: `agent:heartbeat:{agentId}:{instanceId}`
- **数据库**: `instance_id` 字段区分不同实例
- **A2A Handler**: 按 `agentCode` + `instanceId` 区分

---

## 9. 与现有代码兼容性

### 9.1 复用现有组件

| 现有组件 | 复用方式 |
|---------|---------|
| `AgentRegistry` | 扩展为 `AgentRegistryService` 接口实现 |
| `HeartbeatService` | 抽象为 `HeartbeatManagementService` 接口 |
| `MonitorService` | 继续用于图谱数据查询，监听事件更新 |
| `A2AMessageService` | 复用消息流转，新增 traceId 传递 |
| `AgentHeartbeat` 实体 | 保留，新增 `AgentRegistration` 实体 |

### 9.2 增量开发策略

1. **Phase 1**: 新增 `AgentRegistration` 实体和表，保持与 `AiAgent` 表共存
2. **Phase 2**: 实现 `AgentRegistryService`，支持 Push 注册
3. **Phase 3**: 实现 `HeartbeatManagementService`，联动注册与心跳
4. **Phase 4**: 实现事件通知机制，监听器更新图谱
5. **Phase 5**: 实现 Workflow 编排功能

---

## 10. 异常处理与日志规范

### 10.1 异常类定义

```java
// 注册异常
public class AgentRegistrationException extends RuntimeException {
    private final String agentCode;
    private final String errorCode;

    public AgentRegistrationException(String agentCode, String errorCode, String message) {
        super(message);
        this.agentCode = agentCode;
        this.errorCode = errorCode;
    }
}

// 心跳异常
public class HeartbeatException extends RuntimeException {
    private final String agentCode;
    private final String instanceId;

    public HeartbeatException(String agentCode, String instanceId, String message) {
        super(message);
        this.agentCode = agentCode;
        this.instanceId = instanceId;
    }
}
```

### 10.2 日志规范

```java
// 注册成功
log.info("Agent registered successfully: {} [{}], heartbeatInterval={}s, heartbeatTimeout={}s",
         agentCode, instanceId, heartbeatInterval, heartbeatTimeout);

// 注册失败
log.error("Failed to register agent: {} [{}], error={}",
          agentCode, instanceId, e.getMessage(), e);

// 心跳超时
log.warn("Heartbeat timeout for agent: {} [{}], lastHeartbeat={}, timeout={}s",
         agentCode, instanceId, lastHeartbeat, HEARTBEAT_TIMEOUT);

// 探测失败
log.warn("Agent health probe failed: {} [{}], endpoint={}, statusCode={}",
         agentCode, instanceId, endpoint, statusCode);
```

---

## 11. 配置项

### 11.1 application.yml 配置

```yaml
agent:
  registry:
    # Pull 探测间隔（秒）
    pull-interval: 30
    # 默认心跳间隔（秒）
    default-heartbeat-interval: 30
    # 默认心跳超时（秒）
    default-heartbeat-timeout: 90
    # 在线状态阈值（健康分 >= 此值视为在线）
    online-health-threshold: 50
```

---

## 12. 验收标准

1. **Push 注册**: Agent 启动时能成功注册到平台
2. **Pull 探测**: 平台能定时探测 Agent 健康状态
3. **心跳联动**: 心跳超时能自动触发注销流程
4. **事件通知**: 注册/注销/超时事件能正确发布和消费
5. **图谱更新**: 事件触发后图谱能实时更新
6. **多实例支持**: 同一 Agent 的多个实例能正确管理
7. **编排触发**: 支持手动、定时、事件三种触发方式
8. **调用链追踪**: A2A 消息携带 traceId，可追溯调用链

---

**文档结束**
