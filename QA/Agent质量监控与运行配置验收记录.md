# Agent质量监控与运行配置验收记录

## 需求结论
- 第一版质量指标来自数据集测评，不做线上业务调用回标。
- 指标采用标准答案匹配，默认字段为 `input` 和 `expectedOutput`。
- 平台为每个 Agent 保存模型、数据集、topK、temperature 等运行配置。
- Agent 通过主动拉取 `/api/v1/agent-config/{agentCode}` 生效，平台不做配置推送。

## 团队模式记录
- 项目经理：按后端数据模型/API、Agent配置拉取、前端质量监控、测试验收四块推进。
- 产品经理：验收口径为页面能直观看到准确率、精确率、召回率、F1、趋势、配置和明细。
- 业务部门负责人：确认指标用于日常判断 Agent 在指定数据集上的可用质量。
- 架构师：确认新增独立质量测评链路，不复用旧随机评分逻辑，不影响现有 Benchmark 页面。
- 后端：新增运行配置、质量测评运行与明细数据模型，提供配置、测评、趋势和明细 API。
- 前端：新增“质量监控”菜单和页面，支持配置、触发测评、查看结果。
- 测试：覆盖配置默认值与参数校验、accuracy/precision/recall/F1 确定性计算、前端入口与指标展示逻辑。

## 验收要点
- 保存 Agent 配置后，`GET /api/v1/agents/{agentId}/runtime-config` 返回保存值。
- Agent 拉取 `GET /api/v1/agent-config/{agentCode}` 可得到启用配置和 `modelCode`。
- 触发 `POST /api/v1/agent-quality/evaluations` 后生成 run 和逐条 result。
- `GET /api/v1/agent-quality/summary` 可看到最新准确率、精确率、召回率、F1。
- 前端“质量监控”页面可完成配置、测评、趋势和明细查看。
