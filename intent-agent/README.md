# Intent Agent

Intent Recognition Agent follows the same standalone Spring Boot structure as the existing agent modules.

## Endpoints

- `GET /agent/intent/health`
- `GET /agent/intent/info`
- `GET /agent/intent/intents`
- `POST /agent/intent/classify`
- `POST /agent/intent/invoke`
- `POST /api/v1/a2a/message`
- `GET /api/v1/a2a/status`

## Default Runtime

- Port: `8083`
- Agent code: `intent-agent`
- Instance id: `intent-001`
- Platform registry: `http://localhost:8080/api/v1/registry/agents`
- Platform heartbeat: `http://localhost:8080/api/v1/heartbeat/report`

Example classify request:

```json
{
  "text": "Generate sales trend statistics chart"
}
```
