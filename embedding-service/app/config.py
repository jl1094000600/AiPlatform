import os
from dataclasses import dataclass


def _bool_env(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "y", "on"}


def _int_env(name: str, default: int) -> int:
    value = os.getenv(name)
    if value is None or not value.strip():
        return default
    return int(value)


@dataclass(frozen=True)
class Settings:
    model_path: str = os.getenv("BGE_M3_MODEL_PATH", "BAAI/bge-m3")
    model_id: str = os.getenv("BGE_M3_MODEL_ID", "bge-m3")
    device: str | None = os.getenv("BGE_M3_DEVICE") or None
    batch_size: int = _int_env("BGE_M3_BATCH_SIZE", 16)
    max_length: int = _int_env("BGE_M3_MAX_LENGTH", 8192)
    normalize: bool = _bool_env("BGE_M3_NORMALIZE", True)
    use_fp16: bool = _bool_env("BGE_M3_USE_FP16", False)
    api_key: str | None = os.getenv("BGE_M3_API_KEY") or None


settings = Settings()

