# AI中台管理系统 部署指南

## 目录

- [快速开始（Docker Compose）](#快速开始docker-compose)
- [目录结构](#目录结构)
- [环境要求](#环境要求)
- [启动前准备](#启动前准备)
- [启动服务](#启动服务)
- [验证部署](#验证部署)
- [各服务说明](#各服务说明)
- [常见问题](#常见问题)

---

## 快速开始（Docker Compose）

### 1. 环境要求

- Docker 24.0+
- Docker Compose 2.20+
- 最低配置：8 核 16 GB 内存

### 2. 启动

```bash
cd deploy

# 复制环境变量配置
cp env.template .env
# 编辑 .env 填入实际密码

# 构建并启动所有服务
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f
```

### 3. 停止

```bash
docker-compose down      # 停止并移除容器（数据卷保留）
docker-compose down -v    # 停止并清除所有数据卷（慎用，会丢失数据）
```

---

## 目录结构

```
deploy/
├── docker-compose.yml      # 主编排文件
├── env.template            # 环境变量模板
├── nginx/
│   ├── nginx.conf          # Nginx 主配置
│   └── aiplatform.conf     # AI中台站点配置
├── mysql/
│   └── init.sql            # 数据库初始化脚本
├── rocketmq/
│   └── broker.conf         # RocketMQ Broker 配置
├── logstash/
│   └── pipeline/
│       └── aiplatform.conf  # Logstash 管道配置
└── README.md               # 本文件
```

---

## 环境要求

### 开发/测试环境

| 组件 | 最低配置 | 说明 |
|------|----------|------|
| CPU | 8 核 | - |
| 内存 | 16 GB | Elasticsearch 占 2 GB |
| 磁盘 | 100 GB | 日志和数据存储 |
| Docker | 24.0+ | - |

### 生产环境建议

- MySQL：使用外部主从实例，而非 Docker Compose 内嵌
- Redis：使用外部 Redis Cluster
- Elasticsearch：至少 3 节点分布式部署
- 各微服务独立部署，使用 K8s 编排

---

## 启动前准备

### 1. 配置环境变量

```bash
cp env.template .env
```

编辑 `.env` 文件，修改以下必填项：

```env
MYSQL_ROOT_PASSWORD=your_strong_root_password
MYSQL_APP_PASSWORD=your_strong_app_password
REDIS_PASSWORD=your_strong_redis_password
XXL_JOB_TOKEN=your_xxljob_token
```

### 2. 初始化数据库

首次启动时，MySQL 容器会自动执行 `mysql/init.sql` 初始化表结构。

如果需要手动执行：

```bash
docker-compose exec mysql mysql -u root -p ai_platform < mysql/init.sql
```

### 3. 前端构建（可选，如已有构建产物可跳过）

```bash
cd ../frontend
npm install
npm run build
# 产物将输出到 ../frontend/dist
```

### 4. SSL 证书（可选，HTTPS 部署）

将证书文件放入 `nginx/ssl/` 目录：

```
nginx/ssl/
├── aiplatform.crt   # SSL 证书
└── aiplatform.key   # 私钥
```

然后修改 `nginx/aiplatform.conf` 中的 HTTPS server 配置（已提供模板，注释已解开）。

---

## 启动服务

### 启动顺序（自动处理依赖）

Docker Compose 会根据 `depends_on` 和健康检查自动按顺序启动。

### 启动后检查

```bash
# 检查所有容器状态
docker-compose ps

# 检查关键服务健康状态
docker-compose exec mysql mysql -u root -p -e "SELECT 1;"
docker-compose exec redis redis-cli -a redis_password ping
docker-compose exec nacos curl -s http://localhost:8848/nacos/v1/console/health/liveness

# 检查 Gateway
curl http://localhost:8000/actuator/health

# 检查前端
curl http://localhost/
```

---

## 验证部署

### 1. 访问控制台

| 服务 | 地址 | 默认账号 |
|------|------|----------|
| 前端 | http://localhost | - |
| Nacos | http://localhost:8848/nacos | nacos / nacos |
| XXL-JOB | http://localhost:8080/xxl-job-admin | admin / 123456 |
| Kibana | http://localhost:5601 | elastic / elastic |
| SkyWalking | http://localhost:18080 | - |

### 2. API 冒烟测试

```bash
# 获取访问令牌（假设已创建用户）
TOKEN=$(curl -s -X POST http://localhost/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | \
  python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["token"])')

# 查询 Agent 列表
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost/api/v1/agents | python3 -m json.tool
```

期望响应：

```json
{
  "code": 200,
  "message": "success",
  "data": { "total": 0, "records": [] },
  "timestamp": "...",
  "traceId": "..."
}
```

---

## 各服务说明

### 基础设施层

| 容器名 | 镜像 | 端口 | 说明 |
|--------|------|------|------|
| aiplatform-mysql | mysql:8.0 | 3306 | 主数据库 |
| aiplatform-redis | redis:7-alpine | 6379 | 缓存/Session |
| aiplatform-nacos | nacos/nacos-server:v2.3.2 | 8848,9848,9849 | 注册中心/配置中心 |
| aiplatform-rocketmq-namesrv | apache/rocketmq:5.2.0 | 9876 | 消息队列 NameServer |
| aiplatform-rocketmq-broker | apache/rocketmq:5.2.0 | 10909,10911 | 消息队列 Broker |
| aiplatform-xxljob | xuxueli/xxl-job-admin:2.4.1 | 8080 | 任务调度中心 |

### 可观测性层

| 容器名 | 镜像 | 端口 | 说明 |
|--------|------|------|------|
| aiplatform-elasticsearch | elasticsearch:8.12.0 | 9200 | 日志存储 |
| aiplatform-logstash | logstash:8.12.0 | 5044 | 日志采集 |
| aiplatform-kibana | kibana:8.12.0 | 5601 | 日志可视化 |
| aiplatform-skywalking-oap | apache/skywalking-oap:9.7.0 | 11800,12800 | 链路追踪 OAP |
| aiplatform-skywalking-ui | apache/skywalking-ui:9.7.0 | 18080 | 链路追踪 UI |

### 业务服务层

| 容器名 | 镜像 | 端口 | 说明 |
|--------|------|------|------|
| aiplatform-gateway | 本地构建 | 8000 | Spring Cloud Gateway |
| aiplatform-agent-service | 本地构建 | 8001 | Agent 管理服务 |
| aiplatform-monitor-service | 本地构建 | 8002 | 监控服务 |
| aiplatform-model-service | 本地构建 | 8003 | 模型服务 |
| aiplatform-permission-service | 本地构建 | 8004 | 权限服务 |

### 网关层

| 容器名 | 镜像 | 端口 | 说明 |
|--------|------|------|------|
| aiplatform-nginx | nginx:1.24-alpine | 80,443 | 反向代理/前端静态资源 |

---

## 常见问题

### Q1: 容器启动失败，提示端口被占用

检查端口占用情况：

```bash
netstat -tlnp | grep -E '3306|6379|8848|9876|8080|9200|5601|11800|12800|18080|8000'
```

然后修改 `docker-compose.yml` 中的端口映射，或停止占用端口的其他服务。

### Q2: MySQL 初始化失败

首次启动 MySQL 需要几秒钟完成初始化：

```bash
# 等待 MySQL 就绪
docker-compose exec mysql mysqladmin ping -u root -p

# 查看 MySQL 日志
docker-compose logs mysql
```

### Q3: Nacos 无法注册服务

确认网络连通性：

```bash
docker-compose exec gateway ping nacos
```

### Q4: Elasticsearch 内存不足

修改 `docker-compose.yml` 中 Elasticsearch 的内存限制：

```yaml
environment:
  - ES_JAVA_OPTS=-Xms4g -Xmx4g
```

并确保宿主机有足够内存。

### Q5: 前端构建失败

```bash
cd ../frontend
npm install
npm run build
```

检查 Node.js 版本是否为 20.x。

### Q6: 跨域问题

Gateway 已配置 CORS，如仍有跨域问题，检查 `docker-compose.yml` 中 Gateway 的环境变量：

```yaml
 SPRING_GLOBAL_CORS_ORIGINS: "*"
```

---

## 生产部署注意事项

1. **数据库**：使用外部 MySQL 主从实例，而非 Docker Compose 内嵌
2. **Redis**：使用 Redis Cluster 或 Sentinel 保证高可用
3. **消息队列**：RocketMQ 至少 2 主 2 从
4. **存储**：所有数据卷使用宿主机挂载目录，便于数据持久化
5. **安全**：
   - 修改所有默认密码
   - 生产环境启用 HTTPS
   - Nacos 开启鉴权
6. **监控**：接入 Prometheus + Grafana
7. **日志**：配置日志轮转，避免磁盘满
