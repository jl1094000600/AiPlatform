# image-agent 结构修复记录

## 基本信息
- 时间：2026-05-07
- 范围：`image-agent`
- 目标：将 `image-agent` 修改为可运行、可注册、可被平台 Agent 管理和调用图谱识别的正确结构。

## 团队模式
- 项目经理：跟踪问题闭环，确认剩余风险集中在本地缺少 Maven/JDK 命令导致无法实际编译运行。
- 产品经理：验收口径为启动 `image-agent` 后，Agent 管理页和调用图谱能看到 `image-recognition-agent`，实例为 `image-001`。
- 架构师：确认只保留 `com.aipal.agent.image` 一套包结构，并与 `marketing-agent` 使用同一套注册和心跳协议。
- 后端：完成包结构清理、配置统一、启动注册、心跳上报和 A2A 路由修复。
- 测试：新增注册/心跳请求构造测试和 A2A 目标 Agent 路由测试；Redis 相关按约定忽略。

## 根因
`image-agent` 同时存在 `com.aipal.agent.image` 和旧的 `com.aipal.imageagent` 两套结构。旧包引用了主平台包名，例如 `com.aipal.dto.A2AMessage`、`com.aipal.entity.AgentHeartbeat` 和 `com.aipal.mapper.AgentHeartbeatMapper`，在独立模块中不可用，会破坏编译结构。

同时，当前心跳仍使用旧路径 `/api/agent/heartbeat`，并混用 `agentId` 与随机 `instanceId`；配置文件使用 `agent.*`，Java 配置类却读取 `image-agent.*`，导致平台可见性链路不一致。

## 已修复内容
- 删除旧包 `com.aipal.imageagent` 及其旧测试。
- `AgentConfig` 改为读取 `agent.*`，与 `application.yml` 一致。
- `image-agent` 启动时调用 `/api/v1/registry/agents` 注册平台。
- 心跳改为调用 `/api/v1/heartbeat/report`，请求体顶层包含 `agentCode=image-recognition-agent` 和 `instanceId=image-001`。
- 健康检查和 A2A 本地处理使用同一个 `AgentConfig`。
- A2A 入站消息按 `targetAgent` 路由，不再按 `sourceAgent` 查找处理器。
- Spring AI 版本调整为与已运行的 `marketing-agent` 一致的 `1.0.5`，并补齐 `spring-milestones` 仓库和 Spring Boot Maven 插件。

## 测试与验收
- 新增 `HeartbeatServiceTest`：验证注册和心跳请求使用平台可见的 `agentCode + instanceId`。
- 新增 `A2AMessageServiceTest`：验证 A2A 入站消息按 `targetAgent` 路由。
- 静态检查：确认不再引用旧包 `com.aipal.imageagent`、主平台 DTO/Entity/Mapper 包名和旧心跳路径。
- 待运行环境具备 Maven/JDK 后执行：`mvn test`。
- 产品验收：启动主平台和 `image-agent` 后，确认 `/api/v1/agents`、`/api/v1/registry/agents`、`/api/v1/monitor/agent-graph` 均能看到 `image-recognition-agent`。

## 编译问题补充
- 时间：2026-05-07
- 问题：`A2ACommunicationService` 使用了 `ReadOffset.last()`，当前 Spring Data Redis 版本没有该方法，编译报错。
- 修复：Redis Stream consumer group 读取改为 `ReadOffset.lastConsumed()`，语义为读取该 consumer group 尚未消费的新消息。
- 复查：已扫描 `image-agent` 下 `ReadOffset.last()` 调用，确认无残留。
