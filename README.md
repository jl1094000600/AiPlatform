# AI中台管理系统

基于Spring Boot + Vue3的AI Agent管理平台，实现对AI Agent的统一管理、监控与复用。

## 技术栈

### 后端
- JDK 21
- Spring Boot 3.2
- MyBatis-Plus
- MySQL 8.0
- Redis
- JWT认证

### 前端
- Vue3
- Element Plus
- Vite
- Axios
- Vue Router

## 项目结构

```
AIPlatform/
├── backend/                    # 后端项目
│   ├── sql/
│   │   └── init.sql           # 数据库初始化脚本
│   └── src/main/java/com/aipal/
│       ├── controller/        # 控制器
│       ├── service/          # 服务层
│       ├── mapper/           # 数据访问层
│       ├── entity/           # 实体类
│       ├── dto/              # 数据传输对象
│       ├── config/           # 配置类
│       └── common/           # 通用类
├── front/                     # 前端项目
│   └── src/
│       ├── views/            # 页面
│       ├── router/          # 路由
│       ├── api/              # API调用
│       └── components/       # 组件
├── docs/                      # 文档
│   ├── requirements.md       # 需求文档
│   ├── progress.md           # 开发进度
│   └── deployment-ubuntu.md  # 部署文档
└── deploy/                    # 部署配置
```

## 功能模块

### 1. Agent管理
- Agent注册、编辑、删除
- Agent发布与下线
- Agent版本管理
- Agent调用

### 2. 接口监控
- 实时监控（在线Agent、QPS、响应时间）
- 调用统计（次数、成功率、响应时间）
- 调用链路追踪
- 调用日志查询

### 3. 模型管理
- 模型目录管理
- 使用量统计
- Token消费统计

### 4. 权限管理
- 用户管理
- 角色管理
- 权限控制
- 操作审计

## 快速启动

### 后端启动

1. 导入数据库
```bash
mysql -uroot -p < backend/sql/init.sql
```

2. 修改配置
编辑 `backend/src/main/resources/application.yml` 中的数据库连接信息

3. 启动服务
```bash
cd backend
mvn spring-boot:run
```

### 前端启动

```bash
cd front
npm install
npm run dev
```

## API接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/auth/login | 用户登录 |
| POST | /api/v1/auth/register | 用户注册 |
| GET | /api/v1/agents | 获取Agent列表 |
| POST | /api/v1/agents | 创建Agent |
| PUT | /api/v1/agents/{id} | 更新Agent |
| DELETE | /api/v1/agents/{id} | 删除Agent |
| POST | /api/v1/agents/{id}/publish | 发布Agent |
| POST | /api/v1/agents/{id}/offline | 下线Agent |
| GET | /api/v1/monitor/records | 调用记录查询 |
| GET | /api/v1/monitor/statistics | 统计报表 |
| GET | /api/v1/monitor/realtime | 实时监控 |
| GET | /api/v1/models | 模型列表 |
| POST | /api/v1/models | 创建模型 |

## 部署

详细部署文档请参考 [docs/deployment-ubuntu.md](docs/deployment-ubuntu.md)

### Docker部署

```bash
cd deploy
docker-compose up -d
```

## 默认账号

| 角色 | 用户名 | 密码 |
|------|--------|------|
| 系统管理员 | admin | admin123 |

## License

MIT
