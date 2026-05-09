from typing import Literal

from pydantic import BaseModel, Field


class EmbeddingRequest(BaseModel):
    model: str | None = None
    input: str | list[str]
    encoding_format: Literal["float"] | None = "float"


class EmbeddingItem(BaseModel):
    object: str = "embedding"
    embedding: list[float]
    index: int


class Usage(BaseModel):
    prompt_tokens: int = 0
    total_tokens: int = 0


class EmbeddingResponse(BaseModel):
    object: str = "list"
    data: list[EmbeddingItem]
    model: str
    usage: Usage = Field(default_factory=Usage)


class ModelItem(BaseModel):
    id: str
    object: str = "model"
    owned_by: str = "local"


class ModelsResponse(BaseModel):
    object: str = "list"
    data: list[ModelItem]

