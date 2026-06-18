"""Tests de l'adapter 1min.ai (API propriétaire : prompt unique aplati, SSE
`event: content`/`data:{content}`)."""
from __future__ import annotations

import httpx
import pytest
import respx

from app.core.config import Settings
from app.domain.models import ChatMessage
from app.domain.ports import LLMProviderError
from app.infrastructure.onemin_adapter import OneMinAiLLMProvider

_URL = "https://api.1min.ai/api/chat-with-ai?isStreaming=true"


def _svc() -> OneMinAiLLMProvider:
    s = Settings(_env_file=None, onemin_api_key="k", onemin_model="gpt-4o-mini",
                 llm_timeout_seconds=30)
    return OneMinAiLLMProvider(s)


def _sse(*blocks: str) -> str:
    return "".join(blocks)


# --- streaming -----------------------------------------------------------------

@respx.mock
async def test_generate_collects_content_chunks():
    body = _sse(
        "event: content\ndata: {\"content\": \"Bon\"}\n\n",
        "event: content\ndata: {\"content\": \"jour\"}\n\n",
        "event: done\ndata: {}\n\n",
    )
    respx.post(_URL).mock(return_value=httpx.Response(200, text=body))
    assert await _svc().generate("salut") == "Bonjour"


@respx.mock
async def test_generate_sends_api_key_header_and_prompt_payload():
    route = respx.post(_URL).mock(return_value=httpx.Response(
        200, text="event: done\ndata: {}\n\n"))
    await _svc().generate("ma question")
    req = route.calls.last.request
    assert req.headers["API-KEY"] == "k"
    import json
    body = json.loads(req.content)
    assert body["model"] == "gpt-4o-mini"
    assert body["promptObject"]["prompt"] == "ma question"


@respx.mock
async def test_error_event_raises_provider_error():
    body = "event: error\ndata: {\"message\": \"quota dépassé\"}\n\n"
    respx.post(_URL).mock(return_value=httpx.Response(200, text=body))
    with pytest.raises(LLMProviderError) as exc:
        await _svc().generate("p")
    assert "quota dépassé" in str(exc.value)


@respx.mock
async def test_http_error_is_translated():
    respx.post(_URL).mock(return_value=httpx.Response(502, text="bad gateway"))
    with pytest.raises(LLMProviderError) as exc:
        await _svc().generate("p")
    assert "1min.ai" in str(exc.value)


@respx.mock
async def test_stream_chat_flattens_and_streams():
    route = respx.post(_URL).mock(return_value=httpx.Response(
        200, text="event: content\ndata: {\"content\": \"R\"}\n\nevent: done\ndata: {}\n\n"))
    tokens = [t async for t in _svc().stream_chat(
        [ChatMessage(role="user", content="Q")], system_prompt="SYS")]
    assert tokens == ["R"]
    import json
    prompt = json.loads(route.calls.last.request.content)["promptObject"]["prompt"]
    assert "[SYSTEM]" in prompt and "SYS" in prompt
    assert "[USER]" in prompt and "Q" in prompt


# --- helpers purs --------------------------------------------------------------

def test_flatten_messages_structure():
    out = OneMinAiLLMProvider._flatten_messages(
        [ChatMessage(role="user", content="Q1"), ChatMessage(role="assistant", content="R1")],
        "instructions système",
    )
    assert "[SYSTEM]\ninstructions système" in out
    assert "[USER]\nQ1" in out
    assert "[ASSISTANT]\nR1" in out
    assert out.rstrip().endswith("[ASSISTANT]")


def test_extract_content_chunk_json_and_fallback():
    assert OneMinAiLLMProvider._extract_content_chunk('{"content": "x"}') == "x"
    assert OneMinAiLLMProvider._extract_content_chunk('{"token": "y"}') == "y"
    # Non-JSON : filet de sécurité, on renvoie le brut.
    assert OneMinAiLLMProvider._extract_content_chunk("texte brut") == "texte brut"


def test_extract_result_reads_nested_result_object():
    payload = {"aiRecord": {"aiRecordDetail": {"resultObject": ["partie 1", "partie 2"]}}}
    assert OneMinAiLLMProvider._extract_result(payload) == "partie 1partie 2"


def test_extract_result_raises_on_unexpected_schema():
    with pytest.raises(LLMProviderError):
        OneMinAiLLMProvider._extract_result({"unexpected": True})
