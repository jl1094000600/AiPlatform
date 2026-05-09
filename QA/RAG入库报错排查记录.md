# RAG 入库报错排查记录

时间：2026-05-08

## 现象
- 页面执行 RAG 入库失败。
- 后端返回：`RAG ingestion failed: 400 Bad Request: "Invalid HTTP request received."`
- `embedding-service` 控制台出现：
  - `WARNING: Unsupported upgrade request.`
  - `WARNING: Invalid HTTP request received.`

## 已验证
- 模型管理中 `bge-m3` 当前配置为：`http://localhost:8500/v1`。
- `GET http://localhost:8500/health` 可访问。
- `GET http://localhost:8500/v1/models` 可访问。
- `POST http://localhost:8500/v1/embeddings` 可到达服务，但旧服务进程返回 500，说明模型加载或推理阶段还有异常需要暴露。

## 根因判断
- `Invalid HTTP request received` 发生在 Uvicorn 网关层，优先判断为 Java HTTP 客户端与 Uvicorn 的 HTTP upgrade / 协议兼容问题。
- 已将 Java `RagService` 调 embedding 接口改为 `SimpleClientHttpRequestFactory`，强制走普通 HTTP/1.1。
- 同时补充 `embedding-service` 的异常返回，后续模型加载失败时会返回具体异常类型和原因。

## 处理结果
- Java 后端需要重启，使 HTTP 客户端修复生效。
- `embedding-service` 需要重启，使详细错误返回生效。
- 重启后先单独验证 `/v1/embeddings`，再回到 RAG 页面入库。

## 2026-05-08 追加排查
- 新错误：`Embedding generation failed: ValueError: Path F:\\models\\bge-m3 not found`。
- 本机确认 `F:\models\bge-m3` 不存在。
- 本机存在 Ollama 模型：`bge-m3:latest`，Ollama 地址 `http://localhost:11434`。
- 已验证 `POST http://localhost:11434/v1/embeddings` 可返回 embedding。
- 已将数据库中 `bge-m3` 模型配置更新为 `http://localhost:11434/v1`，RAG 可直接走 Ollama OpenAI-compatible 接口。

## 2026-05-08 Chroma 写入报错
- 新错误：`I/O error on POST request for "http://localhost:9000/api/v1/collections/CodingRules/add": null`。
- 已验证 `GET http://localhost:9000/api/v2/heartbeat` 正常。
- 已验证 Chroma v2 手工创建集合并写入向量成功。
- 根因判断：Java 写 Chroma 仍使用默认 `RestClient`，可能触发 Chroma/Uvicorn 不兼容请求；同时代码吞掉 v2 异常后继续走 v1 fallback，导致页面只显示 v1 误导性错误。
- 已处理：Chroma 写入改为 `SimpleClientHttpRequestFactory` 普通 HTTP/1.1；失败时同时返回 v2 和 v1 fallback 的真实错误。

## 2026-05-08 Chroma 连接拒绝
- 新错误：`Connection refused: getsockopt`。
- 已验证 PowerShell 访问 `http://localhost:9000/api/v2/heartbeat` 成功，但 `http://127.0.0.1:9000` 失败。
- `netstat` 显示 Chroma 只监听 `[::1]:9000`，未监听 `127.0.0.1:9000`。
- 根因判断：Java 后端访问 `localhost` 时命中 IPv4 `127.0.0.1`，而本机 Chroma 当前仅监听 IPv6 loopback。
- 已处理：后端保留页面填写的 `http://localhost:9000`，当 Chroma 连接失败时自动追加尝试 `http://[::1]:9000`。

## 验证命令
```powershell
$body = @{ model = 'bge-m3'; input = @('测试文本') } | ConvertTo-Json -Depth 4
Invoke-RestMethod `
  -Uri http://localhost:8500/v1/embeddings `
  -Method Post `
  -ContentType 'application/json' `
  -Body $body
```
