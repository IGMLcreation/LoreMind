"""Tests de caractérisation de l'adapter Ollama (protocole propre : /api/generate
one-shot + /api/chat NDJSON streamé)."""
from __future__ import annotations

import json

import httpx
import pytest
import respx

from app.core.config import Settings
from app.domain.models import ChatMessage
from app.domain.ports import LLMGenerationTimeout, LLMProviderError
from app.infrastructure.ollama_adapter import OllamaLLMProvider

_GEN = "http://ollama:11434/api/generate"
_CHAT = "http://ollama:11434/api/chat"


def _svc() -> OllamaLLMProvider:
    s = Settings(_env_file=None, ollama_base_url="http://ollama:11434",
                 llm_model="gemma", llm_timeout_seconds=30, llm_num_ctx=8192)
    return OllamaLLMProvider(s)


@respx.mock
async def test_generate_returns_response_field():
    respx.post(_GEN).mock(return_value=httpx.Response(200, json={"response": "texte", "done_reason": "stop"}))
    assert await _svc().generate("prompt") == "texte"


@respx.mock
async def test_generate_payload_always_sends_num_ctx_and_omits_temperature():
    route = respx.post(_GEN).mock(return_value=httpx.Response(200, json={"response": "x"}))
    await _svc().generate("p")
    body = json.loads(route.calls.last.request.content)
    assert body["model"] == "gemma"
    assert body["stream"] is False
    assert body["options"] == {"num_ctx": 8192}
    assert "format" not in body


@respx.mock
async def test_generate_payload_includes_temperature_and_format_when_given():
    route = respx.post(_GEN).mock(return_value=httpx.Response(200, json={"response": "x"}))
    await _svc().generate("p", output_format="json", temperature=0.1)
    body = json.loads(route.calls.last.request.content)
    assert body["options"]["temperature"] == 0.1
    assert body["format"] == "json"


@respx.mock
async def test_generate_http_error_surfaces_ollama_message():
    respx.post(_GEN).mock(return_value=httpx.Response(404, json={"error": "model 'x' not found"}))
    with pytest.raises(LLMProviderError) as exc:
        await _svc().generate("p")
    assert "not found" in str(exc.value)
    assert "404" in str(exc.value)


@respx.mock
async def test_generate_read_timeout_is_generation_timeout():
    respx.post(_GEN).mock(side_effect=httpx.ReadTimeout("trop lent"))
    with pytest.raises(LLMGenerationTimeout):
        await _svc().generate("p")


@respx.mock
async def test_generate_connect_timeout_is_provider_error():
    respx.post(_GEN).mock(side_effect=httpx.ConnectTimeout("injoignable"))
    with pytest.raises(LLMProviderError) as exc:
        await _svc().generate("p")
    assert not isinstance(exc.value, LLMGenerationTimeout)


@respx.mock
async def test_stream_chat_yields_tokens_until_done():
    body = (
        '{"message":{"content":"Bon"},"done":false}\n'
        '{"message":{"content":"jour"},"done":false}\n'
        '{"done":true}\n'
    )
    respx.post(_CHAT).mock(return_value=httpx.Response(200, text=body))
    tokens = [t async for t in _svc().stream_chat([ChatMessage(role="user", content="hi")])]
    assert tokens == ["Bon", "jour"]


@respx.mock
async def test_stream_chat_prepends_system_prompt():
    route = respx.post(_CHAT).mock(return_value=httpx.Response(200, text='{"done":true}\n'))
    _ = [t async for t in _svc().stream_chat(
        [ChatMessage(role="user", content="Q")], system_prompt="SYS")]
    body = json.loads(route.calls.last.request.content)
    assert body["messages"][0] == {"role": "system", "content": "SYS"}
    assert body["messages"][-1] == {"role": "user", "content": "Q"}
