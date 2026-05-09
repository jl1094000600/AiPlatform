import math
import threading
from typing import Iterable

from app.config import settings


class EmbeddingModelRunner:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._model = None
        self._backend = ""

    @property
    def loaded(self) -> bool:
        return self._model is not None

    @property
    def backend(self) -> str:
        return self._backend

    def encode(self, texts: list[str]) -> list[list[float]]:
        model = self._load()
        if self._backend == "FlagEmbedding":
            vectors = self._encode_with_flag_embedding(model, texts)
        else:
            vectors = model.encode(
                texts,
                batch_size=settings.batch_size,
                normalize_embeddings=settings.normalize,
                convert_to_numpy=False,
            )
        return [self._to_float_list(vector) for vector in vectors]

    def _load(self):
        if self._model is not None:
            return self._model
        with self._lock:
            if self._model is not None:
                return self._model
            try:
                from FlagEmbedding import BGEM3FlagModel

                kwargs = {"use_fp16": settings.use_fp16}
                if settings.device:
                    kwargs["devices"] = settings.device
                self._model = BGEM3FlagModel(settings.model_path, **kwargs)
                self._backend = "FlagEmbedding"
            except Exception:
                from sentence_transformers import SentenceTransformer

                self._model = SentenceTransformer(settings.model_path, device=settings.device)
                self._backend = "sentence-transformers"
            return self._model

    def _encode_with_flag_embedding(self, model, texts: list[str]):
        try:
            result = model.encode(
                texts,
                batch_size=settings.batch_size,
                max_length=settings.max_length,
                return_dense=True,
                return_sparse=False,
                return_colbert_vecs=False,
            )
        except TypeError:
            result = model.encode(texts, batch_size=settings.batch_size, max_length=settings.max_length)

        vectors = result.get("dense_vecs") if isinstance(result, dict) else result
        if settings.normalize:
            return [self._normalize(vector) for vector in vectors]
        return vectors

    def _normalize(self, vector: Iterable[float]) -> list[float]:
        values = self._to_float_list(vector)
        norm = math.sqrt(sum(value * value for value in values))
        if norm == 0:
            return values
        return [value / norm for value in values]

    def _to_float_list(self, vector: Iterable[float]) -> list[float]:
        if hasattr(vector, "tolist"):
            vector = vector.tolist()
        return [float(value) for value in vector]


runner = EmbeddingModelRunner()

