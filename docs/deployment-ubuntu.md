# AI中台管理系统 Ubuntu 部署文档

| 版本 | 日期       | 作者 | 备注 |
|------|------------|------|------|
| 1.0  | 2026-04-13 | 运维 | 初稿 |

---

## 目录

1. [环境要求](#1-环境要求)
2. [基础环境准备](#2-基础环境准备)
3. [中间件部署](#3-中间件部署)
4. [应用服务部署](#4-应用服务部署)
5. [前端部署](#5-前端部署)
6. [Nginx 反向代理](#6-nginx-反向代理)
7. [健康检查与验证](#7-健康检查与验证)
8. [常见问题](#8-常见问题)

---

## 1. 环境要求

### 1.1 服务器规划（最小生产环境）

| 节点 | 用途 | 推荐配置 | 数量 |
|------|------|----------|------|
| app-node | 业务服务 | 8 核 16 GB | 2 |
| db-master | MySQL 主库 | 8 核 16 GB | 1 |
| db-slave | MySQL 从库 | 8 核 16 GB | 1 |
| cache-node | Redis Cluster | 4 核 8 GB | 3（6实例）|
| mw-node | Nacos、RocketMQ、XXL-JOB | 4 核 8 GB | 1 |
| elk-node | ELK | 8 核 16 GB | 1 |
| sky-node | SkyWalking | 4 核 8 GB | 1 |
| web-node | Nginx + 前端 | 2 核 4 GB | 1 |

> 开发/测试环境可将多个角色合并到同一台机器，最低要求：8 核 16 GB，100 GB 磁盘。

### 1.2 软件版本

| 软件 | 版本 |
|------|------|
| JDK | 21 |
| MySQL | 8.0 |
| Redis | 7.x |
| Nacos | 2.3.x |
| RocketMQ | 5.x |
| XXL-JOB | 2.4.x |
| Elasticsearch | 8.x |
| Logstash | 8.x |
| Kibana | 8.x |
| SkyWalking | 9.x |
| Nginx | 1.24+ |
| Node.js | 20.x |

---

## 2. 基础环境准备

### 2.1 更新系统包

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y curl wget unzip net-tools lsof vim
```

### 2.2 关闭 swap

```bash
sudo swapoff -a
sudo sed -i '/swap/s/^/#/' /etc/fstab
```

### 2.3 调整系统参数

```bash
cat << 'EOF' | sudo tee /etc/sysctl.d/99-aiplatform.conf
vm.max_map_count=262144
fs.file-max=1000000
net.core.somaxconn=65535
net.ipv4.tcp_max_syn_backlog=65535
EOF
sudo sysctl -p /etc/sysctl.d/99-aiplatform.conf
```

```bash
cat << 'EOF' | sudo tee /etc/security/limits.d/99-aiplatform.conf
* soft nofile 1000000
* hard nofile 1000000
* soft nproc 65535
* hard nproc 65535
EOF
```

### 2.4 安装 JDK 21

```bash
sudo apt install -y openjdk-21-jdk
java -version

echo 'export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64' | sudo tee /etc/profile.d/java.sh
echo 'export PATH=$JAVA_HOME/bin:$PATH' | sudo tee -a /etc/profile.d/java.sh
source /etc/profile.d/java.sh
```

---

## 3. 中间件部署

### 3.1 MySQL 8.0（主从架构）

#### 主库（db-master）

```bash
sudo apt install -y mysql-server
sudo systemctl enable mysql
sudo systemctl start mysql
sudo mysql_secure_installation
```

编辑 `/etc/mysql/mysql.conf.d/mysqld.cnf`，在 `[mysqld]` 段追加：

```ini
server-id=1
log_bin=mysql-bin
binlog_format=ROW
binlog_do_db=ai_platform
innodb_buffer_pool_size=4G
max_connections=1000
```

```bash
sudo systemctl restart mysql

sudo mysql -u root -p << 'SQL'
CREATE DATABASE ai_platform DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'ai_app'@'%' IDENTIFIED BY 'Your_Strong_Password_123';
GRANT ALL PRIVILEGES ON ai_platform.* TO 'ai_app'@'%';
CREATE USER 'repl_user'@'%' IDENTIFIED BY 'Repl_Password_456';
GRANT REPLICATION SLAVE ON *.* TO 'repl_user'@'%';
FLUSH PRIVILEGES;
SHOW MASTER STATUS;
SQL
# 记录输出的 File 和 Position 值
```

#### 从库（db-slave）

```ini
server-id=2
relay-log=relay-bin
read_only=1
innodb_buffer_pool_size=4G
max_connections=500
```

```bash
sudo systemctl restart mysql

sudo mysql -u root -p << 'SQL'
CHANGE MASTER TO
  MASTER_HOST='<db-master-ip>',
  MASTER_USER='repl_user',
  MASTER_PASSWORD='Repl_Password_456',
  MASTER_LOG_FILE='mysql-bin.000001',
  MASTER_LOG_POS=0;
START SLAVE;
SHOW SLAVE STATUS\G
SQL
```

导入数据库表结构：

```bash
mysql -h <db-master-ip> -u ai_app -p ai_platform < sql/init.sql
```

---

### 3.2 Redis Cluster（3 主 3 从）

```bash
sudo apt install -y redis-server
```

创建配置目录和文件（`/etc/redis/cluster/redis-6379.conf`，端口替换为 6380/6381...）：

```conf
port 6379
bind 0.0.0.0
daemonize yes
cluster-enabled yes
cluster-config-file /var/lib/redis/6379/nodes.conf
cluster-node-timeout 5000
appendonly yes
requirepass YourRedisPassword
masterauth YourRedisPassword
```

启动 6 个实例后，在任意节点执行：

```bash
redis-cli --cluster create \
  <node1>:6379 <node1>:6380 \
  <node2>:6379 <node2>:6380 \
  <node3>:6379 <node3>:6380 \
  --cluster-replicas 1 -a YourRedisPassword
```

---

### 3.3 Nacos 2.3.x

```bash
cd /opt
wget https://github.com/alibaba/nacos/releases/download/2.3.2/nacos-server-2.3.2.tar.gz
tar -xzf nacos-server-2.3.2.tar.gz
mv nacos /opt/nacos

mysql -h <db-master-ip> -u ai_app -p ai_platform < /opt/nacos/conf/mysql-schema.sql
```

编辑 `/opt/nacos/conf/application.properties`：

```properties
server.port=8848
spring.datasource.platform=mysql
db.url.0=jdbc:mysql://<db-master-ip>:3306/ai_platform?useSSL=false&serverTimezone=UTC
db.user.0=ai_app
db.password.0=Your_Strong_Password_123
nacos.core.auth.enabled=true
nacos.core.auth.plugin.nacos.token.secret.key=VGhpc0lzTXlDdXN0b21TZWNyZXRLZXkwMTIzNDU2
```

```bash
export MODE=standalone
/opt/nacos/bin/startup.sh -m standalone
```

---

### 3.4 Apache RocketMQ 5.x

```bash
cd /opt
wget https://archive.apache.org/dist/rocketmq/5.2.0/rocketmq-all-5.2.0-bin-release.zip
unzip rocketmq-all-5.2.0-bin-release.zip
mv rocketmq-all-5.2.0-bin-release /opt/rocketmq
```

启动：

```bash
export ROCKETMQ_HOME=/opt/rocketmq
nohup /opt/rocketmq/bin/mqnamesrv &> /opt/rocketmq/logs/namesrv.log &
sleep 5
nohup /opt/rocketmq/bin/mqbroker -n localhost:9876 -autoCreateTopicEnable=true &> /opt/rocketmq/logs/broker.log &
```

---

### 3.5 XXL-JOB 2.4.x

```bash
cd /opt
wget https://github.com/xuxueli/xxl-job/releases/download/2.4.1/xxl-job-admin-2.4.1.jar

mysql -h <db-master-ip> -u ai_app -p ai_platform < sql/xxl-job-tables.sql
```

创建 `/opt/xxl-job/application.properties`：

```properties
server.port=8080
spring.datasource.url=jdbc:mysql://<db-master-ip>:3306/ai_platform?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
spring.datasource.username=ai_app
spring.datasource.password=Your_Strong_Password_123
xxl.job.accessToken=YourXxlJobAccessToken
```

```bash
nohup java -jar /opt/xxl-job/xxl-job-admin-2.4.1.jar --spring.config.location=/opt/xxl-job/application.properties &> /opt/xxl-job/xxl-job.log &
```

---

### 3.6 ELK 日志栈

#### Elasticsearch

```bash
wget -qO - https://artifacts.elastic.co/GPG-KEY-elasticsearch | sudo gpg --dearmor -o /usr/share/keyrings/elasticsearch-keyring.gpg
echo "deb [signed-by=/usr/share/keyrings/elasticsearch-keyring.gpg] https://artifacts.elastic.co/packages/8.x/apt stable main" | sudo tee /etc/apt/sources.list.d/elastic-8.x.list
sudo apt update && sudo apt install -y elasticsearch
```

编辑 `/etc/elasticsearch/elasticsearch.yml`：

```yaml
cluster.name: ai-platform-logs
node.name: elk-node-1
network.host: 0.0.0.0
http.port: 9200
xpack.security.enabled: false
```

```bash
sudo systemctl enable elasticsearch
sudo systemctl start elasticsearch
curl http://localhost:9200/_cluster/health?pretty
```

#### Logstash

```bash
sudo apt install -y logstash
cat << 'EOF' | sudo tee /etc/logstash/conf.d/aiplatform.conf
input { beats { port => 5044 } }
filter {
  json { source => "message" }
  date { match => ["timestamp", "ISO8601"] }
}
output {
  elasticsearch { hosts => ["localhost:9200"] index => "aiplatform-logs-%{+YYYY.MM.dd}" }
}
EOF
sudo systemctl enable logstash
sudo systemctl start logstash
```

#### Kibana

```bash
sudo apt install -y kibana
# 编辑 /etc/kibana/kibana.yml 设置 elasticsearch.hosts
sudo systemctl enable kibana
sudo systemctl start kibana
```

---

### 3.7 SkyWalking 9.x

```bash
cd /opt
wget https://archive.apache.org/dist/skywalking/9.7.0/apache-skywalking-apm-9.7.0.tar.gz
tar -xzf apache-skywalking-apm-9.7.0.tar.gz
mv apache-skywalking-apm-bin /opt/skywalking
```

编辑 `/opt/skywalking/config/application.yml`，设置存储为 Elasticsearch：

```yaml
storage:
  selector: ${SW_STORAGE:elasticsearch}
  elasticsearch:
    clusterNodes: ${SW_STORAGE_ES_CLUSTER_NODES:<elk-node-ip>:9200}
```

启动：

```bash
nohup /opt/skywalking/bin/oapService.sh &> /opt/skywalking/logs/oap.log &
sleep 15
nohup /opt/skywalking/bin/webappService.sh &> /opt/skywalking/logs/webapp.log &
```

SkyWalking UI 默认端口 `8080`，如与 XXL-JOB 冲突，修改 `/opt/skywalking/webapp/application.yml` 中的 `port: 18080`。

---

## 4. 应用服务部署

### 4.1 项目构建

```bash
cd /path/to/AIPlatform/backend
./mvnw clean package -DskipTests
```

产物位于各模块 `target/` 目录下。

### 4.2 SkyWalking Agent

```bash
scp -r /opt/skywalking/agent ubuntu@<app-node-ip>:/opt/skywalking-agent
```

### 4.3 启动脚本模板

`/opt/aiplatform/start.sh`（每个服务独立一份，替换 APP_NAME 和 PORT）：

```bash
#!/bin/bash
APP_NAME="gateway"
JAR="/opt/aiplatform/${APP_NAME}/${APP_NAME}-1.0.0.jar"
LOG="/opt/aiplatform/logs/${APP_NAME}.log"
PORT=8000
NACOS_ADDR="<mw-node-ip>:8848"
SW_COLLECTOR="<sky-node-ip>:11800"

nohup java \
  -javaagent:/opt/skywalking-agent/skywalking-agent.jar \
  -DSW_AGENT_NAME=ai-platform-${APP_NAME} \
  -DSW_AGENT_COLLECTOR_BACKEND_SERVICES=${SW_COLLECTOR} \
  -Xms512m -Xmx1g \
  -jar ${JAR} \
  --server.port=${PORT} \
  --spring.cloud.nacos.discovery.server-addr=${NACOS_ADDR} \
  &> ${LOG} &
echo "$APP_NAME started"
```

### 4.4 服务端口规划

| 服务 | 端口 |
|------|------|
| Spring Cloud Gateway | 8000 |
| service-agent | 8001 |
| service-monitor | 8002 |
| service-model | 8003 |
| service-permission | 8004 |
| XXL-JOB Admin | 8080 |
| Nacos | 8848 |
| RocketMQ NameServer | 9876 |
| Elasticsearch | 9200 |
| Kibana | 5601 |
| SkyWalking UI | 18080（改后）|

### 4.5 启动顺序

```bash
# 1. 权限服务（其他服务依赖鉴权）
bash /opt/aiplatform/permission-service/start.sh

# 2. 业务服务
bash /opt/aiplatform/agent-service/start.sh
bash /opt/aiplatform/monitor-service/start.sh
bash /opt/aiplatform/model-service/start.sh

# 3. 网关（最后启动）
bash /opt/aiplatform/gateway/start.sh
```

### 4.6 systemd 服务示例

`/etc/systemd/system/aiplatform-gateway.service`：

```ini
[Unit]
Description=AIPlatform Gateway
After=network.target nacos.service

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/opt/aiplatform/gateway
ExecStart=/usr/bin/java \
  -javaagent:/opt/skywalking-agent/skywalking-agent.jar \
  -DSW_AGENT_NAME=ai-platform-gateway \
  -DSW_AGENT_COLLECTOR_BACKEND_SERVICES=<sky-node-ip>:11800 \
  -Xms512m -Xmx1g \
  -jar /opt/aiplatform/gateway/gateway-1.0.0.jar \
  --server.port=8000 \
  --spring.cloud.nacos.discovery.server-addr=<mw-node-ip>:8848
StandardOutput=append:/opt/aiplatform/logs/gateway.log
StandardError=append:/opt/aiplatform/logs/gateway-err.log
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable aiplatform-gateway
sudo systemctl start aiplatform-gateway
```

---

## 5. 前端部署

```bash
# 安装 Node.js 20
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs

cd /path/to/AIPlatform/frontend
npm install
npm run build
# 产物目录：dist/
```

上传到 web-node：

```bash
sudo mkdir -p /var/www/aiplatform
scp -r dist/* ubuntu@<web-node-ip>:/var/www/aiplatform/
```

---

## 6. Nginx 反向代理

```bash
sudo apt install -y nginx
```

创建 `/etc/nginx/sites-available/aiplatform`：

```nginx
upstream gateway_backend {
    server <app-node-1-ip>:8000;
    server <app-node-2-ip>:8000;
    keepalive 32;
}

server {
    listen 80;
    server_name your-domain.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name your-domain.com;

    ssl_certificate     /etc/nginx/ssl/aiplatform.crt;
    ssl_certificate_key /etc/nginx/ssl/aiplatform.key;
    ssl_protocols       TLSv1.2 TLSv1.3;

    root /var/www/aiplatform;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
        expires 1h;
    }

    location /api/ {
        proxy_pass http://gateway_backend;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /ws/ {
        proxy_pass http://gateway_backend;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

```bash
sudo ln -s /etc/nginx/sites-available/aiplatform /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl enable nginx
sudo systemctl reload nginx
```

---

## 7. 健康检查与验证

### 中间件

```bash
# MySQL
mysql -h <db-master-ip> -u ai_app -p -e "SELECT 1;"

# Redis
redis-cli -h <cache-node-1> -p 6379 -a YourRedisPassword ping

# Nacos
curl -s http://<mw-node-ip>:8848/nacos/v1/console/health/liveness

# RocketMQ
/opt/rocketmq/bin/mqadmin clusterList -n <mw-node-ip>:9876

# Elasticsearch
curl -s http://<elk-node-ip>:9200/_cluster/health
```

### 应用服务

```bash
curl -s http://<app-node-1-ip>:8000/actuator/health
curl -s http://<app-node-1-ip>:8001/actuator/health
curl -s http://<app-node-1-ip>:8002/actuator/health
curl -s http://<app-node-1-ip>:8003/actuator/health
curl -s http://<app-node-1-ip>:8004/actuator/health
```

### API 冒烟测试

```bash
TOKEN=$(curl -s -X POST https://your-domain.com/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | \
  python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["token"])')

curl -s -H "Authorization: Bearer $TOKEN" \
  https://your-domain.com/api/v1/agents | python3 -m json.tool
```

---

## 8. 常见问题

### Q1: Nacos 启动失败，数据库连接超时

检查 MySQL 远程访问权限：

```bash
sudo mysql -u root -p -e "SELECT host, user FROM mysql.user WHERE user='ai_app';"
# 确认有 host='%' 的记录
```

确认防火墙开放 3306 端口：

```bash
sudo ufw allow 3306/tcp
```

### Q2: Redis Cluster 节点 FAIL

检查时钟同步：

```bash
sudo apt install -y ntpdate
sudo ntpdate -u pool.ntp.org
```

确认 cluster bus 端口开放：

```bash
sudo ufw allow 16379/tcp
sudo ufw allow 16380/tcp
```

### Q3: 业务服务无法注册到 Nacos

确认网络连通性：

```bash
nc -zv <mw-node-ip> 8848
nc -zv <mw-node-ip> 9848
```

### Q4: SkyWalking 采集不到 Trace

确认 OAP gRPC 端口可达：

```bash
nc -zv <sky-node-ip> 11800
```

查看 agent 日志：

```bash
tail -f /opt/skywalking-agent/logs/skywalking-api.log
```

### Q5: Elasticsearch 内存不足

调整 JVM 堆大小 `/etc/elasticsearch/jvm.options`：

```
-Xms2g
-Xmx2g
```

---

## 附录：防火墙端口汇总

| 端口 | 服务 |
|------|------|
| 80 / 443 | Nginx |
| 3306 | MySQL |
| 6379-6380 | Redis 实例 |
| 16379-16380 | Redis Cluster Bus |
| 8848 / 9848 / 9849 | Nacos |
| 9876 | RocketMQ NameServer |
| 10909 / 10911 | RocketMQ Broker |
| 8080 | XXL-JOB Admin |
| 9200 / 9300 | Elasticsearch |
| 5601 | Kibana |
| 5044 | Logstash Beats |
| 11800 / 12800 | SkyWalking OAP |
| 18080 | SkyWalking UI |
| 8000 | Gateway |
| 8001-8004 | 业务微服务 |
