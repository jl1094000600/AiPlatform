from fastapi.testclient import TestClient

from app.main import app
from app.model_runner import runner


class DummyRunner:
    loaded = True
    backend = "dummy"

    def encode(self, texts):
        return [[float(index), 1.0] for index, _ in enumerate(texts)]


def test_embeddings_endpoint_returns_openai_compatible_shape(monkeypatch):
    monkeypatch.setattr("app.main.runner", DummyRunner())
    client = TestClient(app)

    response = client.post("/v1/embeddings", json={"model": "bge-m3", "input": ["hello", "world"]})

    assert response.status_code == 200
    payload = response.json()
    assert payload["object"] == "list"
    assert payload["model"] == "bge-m3"
    assert payload["data"][0]["object"] == "embedding"
    assert payload["data"][0]["embedding"] == [0.0, 1.0]
    assert payload["data"][1]["index"] == 1


def test_rejects_unknown_model(monkeypatch):
    monkeypatch.setattr("app.main.runner", runner)
    client = TestClient(app)

    response = client.post("/v1/embeddings", json={"model": "other", "input": "hello"})

    assert response.status_code == 404

