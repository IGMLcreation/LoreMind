"""Tests des adapters d'embeddings (Mistral cloud + Ollama local)."""
from __future__ import annotations

import json

import httpx
import pytest
import respx

from app.application.embeddings import EmbeddingError
from app.core.config import Settings
from app.infrastructure.mistral_embedding_adapter import MistralEmbeddingProvider
from app.infrastructure.ollama_embedding_adapter import OllamaEmbeddingProvider

_MISTRAL = "https://api.mistral.ai/v1/embeddings"
_OLLAMA = "http://ollama:11434/api/embed"


def _settings(**kw) -> Settings:
    base = dict(_env_file=None, llm_timeout_seconds=30, ollama_base_url="http://ollama:11434")
    base.update(kw)
    return Settings(**base)


# --- Mistral -------------------------------------------------------------------

def test_mistral_missing_key_raises_at_construction():
    with pytest.raises(EmbeddingError):
        MistralEmbeddingProvider(_settings(mistral_api_key=""))


async def test_mistral_empty_texts_short_circuits():
    svc = MistralEmbeddingProvider(_settings(mistral_api_key="k"))
    assert await svc.embed([]) == []


@respx.mock
async def test_mistral_returns_vectors():
    respx.post(_MISTRAL).mock(return_value=httpx.Response(200, json={
        "data": [{"embedding": [0.1, 0.2]}, {"embedding": [0.3, 0.4]}]
    }))
    svc = MistralEmbeddingProvider(_settings(mistral_api_key="k", mistral_embedding_model="mistral-embed"))
    vectors = await svc.embed(["texte un", "texte deux"])
    assert vectors == [[0.1, 0.2], [0.3, 0.4]]


@respx.mock
async def test_mistral_http_error_raises():
    respx.post(_MISTRAL).mock(return_value=httpx.Response(429, text="rate limit"))
    svc = MistralEmbeddingProvider(_settings(mistral_api_key="k"))
    with pytest.raises(EmbeddingError) as exc:
        await svc.embed(["x"])
    assert "429" in str(exc.value)


@respx.mock
async def test_mistral_size_mismatch_raises():
    respx.post(_MISTRAL).mock(return_value=httpx.Response(200, json={"data": [{"embedding": [0.1]}]}))
    svc = MistralEmbeddingProvider(_settings(mistral_api_key="k"))
    with pytest.raises(EmbeddingError):
        await svc.embed(["a", "b"])  # 2 demandés, 1 reçu


# --- Ollama --------------------------------------------------------------------

async def test_ollama_empty_texts_short_circuits():
    svc = OllamaEmbeddingProvider(_settings(ollama_embedding_model="nomic-embed-text"))
    assert await svc.embed([]) == []


@respx.mock
async def test_ollama_returns_vectors():
    respx.post(_OLLAMA).mock(return_value=httpx.Response(200, json={"embeddings": [[0.1], [0.2]]}))
    svc = OllamaEmbeddingProvider(_settings(ollama_embedding_model="mxbai-embed-large"))
    assert await svc.embed(["a", "b"]) == [[0.1], [0.2]]


@respx.mock
async def test_ollama_applies_nomic_task_prefix():
    route = respx.post(_OLLAMA).mock(return_value=httpx.Response(200, json={"embeddings": [[0.0]]}))
    svc = OllamaEmbeddingProvider(_settings(ollama_embedding_model="nomic-embed-text"))
    await svc.embed(["question ?"], kind="query")
    sent = json.loads(route.calls.last.request.content)["input"]
    assert sent == ["search_query: question ?"]


@respx.mock
async def test_ollama_no_prefix_for_non_nomic_model():
    route = respx.post(_OLLAMA).mock(return_value=httpx.Response(200, json={"embeddings": [[0.0]]}))
    svc = OllamaEmbeddingProvider(_settings(ollama_embedding_model="mxbai-embed-large"))
    await svc.embed(["doc"], kind="document")
    sent = json.loads(route.calls.last.request.content)["input"]
    assert sent == ["doc"]


@respx.mock
async def test_ollama_http_error_mentions_pull_hint():
    respx.post(_OLLAMA).mock(return_value=httpx.Response(404, text="model not found"))
    svc = OllamaEmbeddingProvider(_settings(ollama_embedding_model="nomic-embed-text"))
    with pytest.raises(EmbeddingError) as exc:
        await svc.embed(["x"])
    assert "ollama pull" in str(exc.value)
