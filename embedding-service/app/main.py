from fastapi import Depends, FastAPI, Header, HTTPException

from app.config import settings
from app.model_runner import runner
from app.schemas import EmbeddingItem, EmbeddingRequest, EmbeddingResponse, ModelItem, ModelsResponse, Usage

app = FastAPI(title="BGEM3 Embedding Service", version="1.0.0")


def require_api_key(authorization: str | None = Header(default=None)) -> None:
    if not settings.api_key:
        return
    expected = f"Bearer {settings.api_key}"
    if authorization != expected:
        raise HTTPException(status_code=401, detail="Invalid API key")


@app.get("/health")
def health():
    return {
        "status": "ok",
        "model": settings.model_id,
        "modelPath": settings.model_path,
        "loaded": runner.loaded,
        "backend": runner.backend,
    }


@app.get("/v1/models", response_model=ModelsResponse)
def list_models(_: None = Depends(require_api_key)):
    return ModelsResponse(data=[ModelItem(id=settings.model_id)])


@app.post("/v1/embeddings", response_model=EmbeddingResponse)
def create_embeddings(request: EmbeddingRequest, _: None = Depends(require_api_key)):
    if request.encoding_format and request.encoding_format != "float":
        raise HTTPException(status_code=400, detail="Only float embeddings are supported")
    texts = [request.input] if isinstance(request.input, str) else request.input
    if not texts or any(text is None or not str(text).strip() for text in texts):
        raise HTTPException(status_code=400, detail="Input text is required")
    model_id = request.model or settings.model_id
    if model_id != settings.model_id:
        raise HTTPException(status_code=404, detail=f"Model {model_id} is not available")

    try:
        vectors = runner.encode([str(text) for text in texts])
    except Exception as exc:
        raise HTTPException(
            status_code=500,
            detail=f"Embedding generation failed: {type(exc).__name__}: {exc}",
        ) from exc
    usage_tokens = sum(max(1, len(str(text)) // 4) for text in texts)
    return EmbeddingResponse(
        data=[
            EmbeddingItem(embedding=vector, index=index)
            for index, vector in enumerate(vectors)
        ],
        model=settings.model_id,
        usage=Usage(prompt_tokens=usage_tokens, total_tokens=usage_tokens),
    )
