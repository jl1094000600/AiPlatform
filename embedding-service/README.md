# BGEM3 Embedding Service

本服务把本地 BGEM3 模型包装成 OpenAI-compatible embedding API，供 AI Platform 后端统一调用。

## 接口

- `GET /health`：服务健康检查
- `GET /v1/models`：返回当前 embedding 模型
- `POST /v1/embeddings`：生成向量

平台模型管理中填写：

```text
模型名称：BGEM3 Embedding
模型编码：bge-m3
厂商：Local
SDK 协议：openai-compatible
Base URL：http://localhost:8000/v1
API Key：如未配置服务端 API Key，可留空
状态：启用
```

RAG 页面中 Chroma 地址仍填写：

```text
http://localhost:9000
```

## 启动

先安装依赖：

```powershell
cd F:\Projects\AIPlatform\embedding-service
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

使用本地模型目录启动：

```powershell
$env:BGE_M3_MODEL_PATH="F:\models\bge-m3"
python -m uvicorn app.main:app --host 0.0.0.0 --port 8500
```

也可以使用脚本：

```powershell
.\start.ps1 -ModelPath "F:\models\bge-m3" -Port 8500
```

## 配置项

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `BGE_M3_MODEL_PATH` | `BAAI/bge-m3` | 本地模型目录或 HuggingFace 模型名 |
| `BGE_M3_MODEL_ID` | `bge-m3` | OpenAI API 中的模型编码 |
| `BGE_M3_DEVICE` | 空 | 例如 `cpu`、`cuda` |
| `BGE_M3_BATCH_SIZE` | `16` | 批量编码大小 |
| `BGE_M3_MAX_LENGTH` | `8192` | 最大文本长度 |
| `BGE_M3_NORMALIZE` | `true` | 是否归一化向量 |
| `BGE_M3_USE_FP16` | `false` | GPU 环境可改为 `true` |
| `BGE_M3_API_KEY` | 空 | 可选，设置后请求必须带 `Authorization: Bearer ...` |

## 验证

```powershell
Invoke-RestMethod `
  -Uri http://localhost:8000/v1/embeddings `
  -Method Post `
  -ContentType 'application/json' `
  -Body '{"model":"bge-m3","input":["测试文本"]}'
```

返回中应包含：

```json
{
  "data": [
    {
      "object": "embedding",
      "embedding": [0.01, 0.02],
      "index": 0
    }
  ]
}
```

