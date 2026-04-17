# AI Platform v3.0 Release Notes

**Version**: 3.0.0
**Release Date**: 2026-04-17
**Status**: Released

---

## 一、版本概述

AI Platform v3.0 在 v2.0 的 Agent 管理和 A2A 通信协议基础上，新增两个垂直领域的专业 Agent：图像识别 Agent 和市场营销 Agent。

---

## 二、新增功能

### 2.1 图像识别Agent (Image Recognition Agent)

**端口**: 8082

**核心能力**:
- 多格式图像识别 (JPEG, PNG, GIF, BMP, WebP)
- 非图像文件文本提取 (PDF, Word, Excel, TXT, Markdown)
- A2A通信支持
- 心跳监控

**API端点**:
| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/image-agent/recognize` | POST | 图像识别 |
| `/api/image-agent/task/{taskId}` | GET | 查询任务 |
| `/api/image-agent/health` | GET | 健康检查 |
| `/api/image-agent/a2a/send` | POST | A2A消息发送 |
| `/api/image-agent/heartbeat/report` | POST | 心跳上报 |

### 2.2 市场营销Agent (Marketing Agent)

**端口**: 8081

**核心能力**:
- 销售数据查询 (多维度：时间、区域、产品)
- 同比环比趋势分析 (YoY, MoM)
- 统计汇总排名 (TOP N)
- 图表数据生成 (ECharts格式)

**API端点**:
| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/v1/marketing-agent/invoke` | POST | 调用Agent能力 |
| `/api/v1/marketing-agent/info` | GET | Agent信息 |
| `/api/v1/marketing-agent/health` | GET | 健康检查 |

**Agent意图**:
- `sales_query` - 销售数据查询
- `trend_analysis` - 同比环比分析
- `statistics` - 统计排名
- `chart_generation` - 图表生成
- `export_data` - 数据导出

---

## 三、技术架构

### 3.1 系统架构

```
┌─────────────────┐
│   主平台 (8080)  │
│  - Heartbeat API│
│  - A2A API      │
│  - Monitor API  │
└────────┬────────┘
         │
    ┌────┴────┐
    │         │
┌───▼──┐  ┌──▼────┐
│ Image │  │Market │
│Agent  │  │Agent  │
│(8082) │  │(8081) │
└───────┘  └───────┘
```

### 3.2 技术栈

- **Spring Boot**: 3.2.4
- **Java**: 21
- **SpringAI**: 1.0.0-M4
- **MyBatis Plus**: 3.5.6
- **Redis**: Stream消息队列
- **Apache Tika**: 2.9.2 (文档解析)

---

## 四、数据库变更

### 4.1 新增表

| 表名 | 说明 |
|------|------|
| `ai_agent_info` | Agent信息表 |
| `agent_image_recognition` | 图像识别Agent配置 |
| `agent_marketing` | 市场营销Agent配置 |
| `ai_agent_invocation_log` | Agent调用记录 |
| `marketing_sales_data` | 营销销售数据 |

### 4.2 初始化脚本

执行 `backend/sql/schema-v3.sql` 初始化数据库。

---

## 五、部署指南

### 5.1 前置条件

- JDK 21+
- MySQL 8.0+
- Redis 6.0+

### 5.2 启动顺序

1. 启动 MySQL 和 Redis
2. 执行 `backend/sql/schema-v3.sql` 初始化数据库
3. 启动主平台 (port 8080)
4. 启动图像识别Agent (port 8082)
5. 启动市场营销Agent (port 8081)

### 5.3 验证部署

```bash
# 检查Agent注册
curl http://localhost:8080/api/v1/a2a/agents

# 检查心跳状态
curl http://localhost:8080/api/v1/heartbeat/status/1

# 测试图像识别
curl -X POST http://localhost:8082/api/image-agent/health

# 测试市场营销Agent
curl -X GET http://localhost:8081/api/v1/marketing-agent/health
```

---

## 六、升级注意事项

1. **数据库迁移**: 必须执行 `schema-v3.sql` 创建新表
2. **端口变更**: 图像识别Agent使用8082端口，市场营销Agent使用8081端口
3. **配置更新**: 更新Agent配置文件中的平台URL

---

## 七、已知问题

| 问题ID | 描述 | 严重程度 | 状态 |
|-------|------|---------|------|
| - | 无已知问题 | - | - |

---

## 八、版本历史

| 版本 | 日期 | 作者 | 说明 |
|------|------|------|------|
| 3.0.0 | 2026-04-17 | Team | 初始发布：图像识别Agent + 市场营销Agent |

---

**文档作者**: QA Team
**评审人**: -
**版本状态**: Released
