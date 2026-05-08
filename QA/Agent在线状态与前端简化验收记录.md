# Agent 在线状态修复与前端简化验收记录

## 问题
- 现象：MarketingAgent 未启动时，Agent 管理页仍显示 `marketing-agent` 在线。
- 影响：Agent 管理、调用图谱、首页概况的在线数量都可能误导业务值班人员。

## 根因
- 后端展示运行状态时只读取 `ai_agent_heartbeat.status = 1`，没有校验 `last_heartbeat` 是否超过心跳超时时间。
- 离线扫描只遍历 Redis `agent:heartbeat:*` key。Redis key 过期或缺失后，数据库中残留的在线心跳不会被改为离线。
- 首页概况原先按 `ai_agent.status = 1` 统计在线 Agent，也会受到主数据发布状态和运行状态混用影响。

## 修复内容
- 后端：新增统一运行状态判定，只有 `status=1` 且最后心跳未超过 90 秒才视为 `online`。
- 后端：Agent 管理、调用图谱、首页概况、接口监控实时在线数全部改用同一套心跳新鲜度口径。
- 后端：离线扫描增加数据库兜底扫描，将 stale 的 `ai_agent_heartbeat` 记录标记为离线，即使 Redis key 已不存在。
- 前端：Agent 管理页状态列改为展示 `runtimeStatus`，不再直接展示主表发布状态。
- 前端：新增并强化 `/dashboard` 作为首页概况入口，登录后进入首页。
- 前端：全局主题从霓虹风格调整为简洁运营台风格，减少渐变、发光、动效和装饰网格。

## 团队模式记录
- 项目经理：本次闭环范围定为“假在线修复 + 首页概况 + 前端视觉简化”，Redis 不作为阻塞项，但数据库 stale 兜底必须完成。
- 产品经理：验收重点为 MarketingAgent 停止后页面显示离线，首页能看概况，界面不再偏演示感。
- 业务部门负责人：确认在线状态必须反映真实可调度能力，不能用历史心跳误导值班判断。
- 架构师：确认 `ai_agent` 仍是主数据，运行态由 `ai_agent_heartbeat` 动态计算，发布态和运行态分离。
- 后端：完成运行态统一判定、离线兜底扫描、首页和接口监控在线统计修复。
- 前端：完成首页入口、状态列口径、主题简化。
- 测试：补充 stale heartbeat 单元测试，覆盖 Agent 列表、调用图谱、首页统计和离线扫描兜底。

## 验收要点
- MarketingAgent 未启动且最后心跳超过 90 秒后，`/api/v1/agents` 中 `marketing-agent.runtimeStatus` 应为 `offline`。
- `/api/v1/monitor/agent-graph` 中该节点 `status` 应为 `offline`，`instanceCount` 为 0。
- `/api/v1/business-dashboard/summary` 的 `onlineAgents` 不统计 stale heartbeat。
- `/api/v1/monitor/realtime` 的 `onlineAgents` 不统计 stale heartbeat。
- 前端 Agent 管理页显示“离线”，首页概况显示正确在线数量。
