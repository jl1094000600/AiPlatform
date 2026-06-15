# AI Platform deployment

The deploy stack reflects the repository's real monolithic topology:

- `front`: Vue static assets and the public Nginx entry point on port `80`
- `backend`: one Spring Boot application on port `8080`
- `mysql`: application database on port `3306`
- `redis`: cache and runtime state on port `6379`

There are no gateway, agent, monitor, model, or permission microservice containers. Those capabilities are modules in the single backend JAR.

## Start

```bash
cp deploy/.env.example deploy/.env
# Replace every change-me value, JWT_SECRET, and BOOTSTRAP_ADMIN_PASSWORD_HASH before starting.
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d --build --wait
```

Open `http://localhost`. The backend remains available directly at `http://localhost:8080` by default.

## Health checks

```bash
curl --fail http://localhost/health
curl --fail http://localhost/api/actuator/health
curl --fail http://localhost:8080/api/actuator/health
```

The actuator base path is intentionally `/api/actuator` so it follows the existing `/api/**` security policy without changing business Java source.

## Database initialization

MySQL mounts the real repository script at `backend/sql/init.sql`. The script is executed only when the `mysql_data` volume is first created. Application-owned schema initializers apply the additional module tables during backend startup.

## Images

Local Compose builds both application images. CI publishes immutable SHA tags and supplies them through `BACKEND_IMAGE` and `FRONT_IMAGE` during remote deployment.

## Static validation

```bash
node scripts/check-compose.mjs
```

The check rejects pseudo services, wrong backend ports or health paths, missing health checks, and broken bind-mount sources. It also runs `docker compose config` when Docker Compose is available.
