"""Tests de caractérisation des adapters LLM « OpenAI-compatible »
(OpenRouter, Gemini, Mistral).

But : VERROUILLER le comportement observable AVANT d'extraire une classe de base
commune (les trois adapters partageaient ~80 % de code). On couvre via respx
(mock du transport httpx) : collecte du stream, payload envoyé, en-têtes, parsing
SSE, et traduction des erreurs HTTP — sans aucun appel réseau réel.

Ces tests doivent rester verts à l'identique après le refactor.
"""
from __future__ import annotations

import json

import httpx
import pytest
import respx

from app.core.config import Settings
from app.domain.models import ChatMessage
from app.domain.ports import LLMProviderError
from app.infrastructure.gemini_adapter import GeminiLLMProvider
from app.infrastructure.mistral_adapter import MistralLLMProvider
from app.infrastructure.openrouter_adapter import OpenRouterLLMProvider


def _settings(**kw) -> Settings:
    return Settings(_env_file=None, llm_timeout_seconds=30, **kw)


def _sse(*contents: str) -> str:
    """Construit un corps SSE OpenAI : une trame `data: {choices:[{delta:{content}}]}`
    par fragment, terminé par `data: [DONE]`."""
    lines: list[str] = []
    for c in contents:
        lines.append("data: " + json.dumps({"choices": [{"delta": {"content": c}}]}))
        lines.append("")
    lines += ["data: [DONE]", ""]
    return "\n".join(lines)


# (id, classe, url, kwargs settings (clé+modèle), supporte response_format=json_object)
CASES = [
    pytest.param(
        OpenRouterLLMProvider,
        "https://openrouter.ai/api/v1/chat/completions",
        dict(openrouter_api_key="k", openrouter_model="m"),
        False,
        "OpenRouter",
        id="openrouter",
    ),
    pytest.param(
        GeminiLLMProvider,
        "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
        dict(gemini_api_key="k", gemini_model="m"),
        True,
        "Gemini",
        id="gemini",
    ),
    pytest.param(
        MistralLLMProvider,
        "https://api.mistral.ai/v1/chat/completions",
        dict(mistral_api_key="k", mistral_model="m"),
        True,
        "Mistral",
        id="mistral",
    ),
]


@pytest.mark.parametrize("cls, url, skw, supports_json, label", CASES)
@respx.mock
async def test_generate_collects_full_stream(cls, url, skw, supports_json, label):
    respx.post(url).mock(return_value=httpx.Response(200, text=_sse("Bonjour", " le", " monde")))
    svc = cls(_settings(**skw))
    assert await svc.generate("salut") == "Bonjour le monde"


@pytest.mark.parametrize("cls, url, skw, supports_json, label", CASES)
@respx.mock
async def test_stream_chat_yields_tokens(cls, url, skw, supports_json, label):
    respx.post(url).mock(return_value=httpx.Response(200, text=_sse("A", "B", "C")))
    svc = cls(_settings(**skw))
    tokens = [t async for t in svc.stream_chat([ChatMessage(role="user", content="hi")])]
    assert tokens == ["A", "B", "C"]


@pytest.mark.parametrize("cls, url, skw, supports_json, label", CASES)
@respx.mock
async def test_payload_system_prompt_and_temperature(cls, url, skw, supports_json, label):
    route = respx.post(url).mock(return_value=httpx.Response(200, text=_sse("x")))
    svc = cls(_settings(**skw))
    _ = [t async for t in svc.stream_chat(
        [ChatMessage(role="user", content="Q")],
        system_prompt="SYS",
        temperature=0.5,
    )]
    body = json.loads(route.calls.last.request.content)
    assert body["model"] == "m"
    assert body["stream"] is True
    assert body["messages"][0] == {"role": "system", "content": "SYS"}
    assert body["messages"][-1] == {"role": "user", "content": "Q"}
    assert body["temperature"] == 0.5


@pytest.mark.parametrize("cls, url, skw, supports_json, label", CASES)
@respx.mock
async def test_payload_omits_temperature_when_none(cls, url, skw, supports_json, label):
    route = respx.post(url).mock(return_value=httpx.Response(200, text=_sse("x")))
    svc = cls(_settings(**skw))
    await svc.generate("p")
    body = json.loads(route.calls.last.request.content)
    assert "temperature" not in body
    # Sans system_prompt, generate envoie un unique message user.
    assert body["messages"] == [{"role": "user", "content": "p"}]


@pytest.mark.parametrize("cls, url, skw, supports_json, label", CASES)
@respx.mock
async def test_response_format_json_only_when_supported(cls, url, skw, supports_json, label):
    route = respx.post(url).mock(return_value=httpx.Response(200, text=_sse("{}")))
    svc = cls(_settings(**skw))
    await svc.generate("p", output_format="json")
    body = json.loads(route.calls.last.request.content)
    if supports_json:
        assert body["response_format"] == {"type": "json_object"}
    else:
        assert "response_format" not in body


@pytest.mark.parametrize("cls, url, skw, supports_json, label", CASES)
@respx.mock
async def test_authorization_header_bearer(cls, url, skw, supports_json, label):
    route = respx.post(url).mock(return_value=httpx.Response(200, text=_sse("x")))
    svc = cls(_settings(**skw))
    await svc.generate("p")
    assert route.calls.last.request.headers["Authorization"] == "Bearer k"


@respx.mock
async def test_openrouter_attribution_headers():
    route = respx.post("https://openrouter.ai/api/v1/chat/completions").mock(
        return_value=httpx.Response(200, text=_sse("x")))
    svc = OpenRouterLLMProvider(_settings(openrouter_api_key="k", openrouter_model="m"))
    await svc.generate("p")
    headers = route.calls.last.request.headers
    assert headers["HTTP-Referer"] == "https://loremind.app"
    assert headers["X-Title"] == "LoreMind"


@pytest.mark.parametrize("cls, url, skw, supports_json, label", CASES)
@respx.mock
async def test_http_error_translated_to_provider_error(cls, url, skw, supports_json, label):
    respx.post(url).mock(return_value=httpx.Response(429, text="quota exceeded"))
    svc = cls(_settings(**skw))
    with pytest.raises(LLMProviderError) as exc:
        await svc.generate("p")
    msg = str(exc.value)
    assert label in msg
    assert "429" in msg
    assert "quota exceeded" in msg


@respx.mock
async def test_gemini_rejected_key_gives_actionable_message():
    respx.post(
        "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
    ).mock(return_value=httpx.Response(403, text="API key not valid"))
    svc = GeminiLLMProvider(_settings(gemini_api_key="k", gemini_model="m"))
    with pytest.raises(LLMProviderError) as exc:
        await svc.generate("p")
    assert "refusée par Google" in str(exc.value)


@pytest.mark.parametrize("cls, url, skw, supports_json, label", CASES)
@respx.mock
async def test_sse_skips_keepalive_and_malformed_lines(cls, url, skw, supports_json, label):
    body = "\n".join([
        ": OPENROUTER PROCESSING",  # commentaire keep-alive
        "",
        "data: not-json",            # JSON invalide -> ignoré
        "",
        "data: " + json.dumps({"choices": []}),  # pas de choix -> ignoré
        "",
        "data: " + json.dumps({"choices": [{"delta": {}}]}),  # delta sans content -> ignoré
        "",
        "data: " + json.dumps({"choices": [{"delta": {"content": "OK"}}]}),
        "",
        "data: [DONE]",
        "",
    ])
    respx.post(url).mock(return_value=httpx.Response(200, text=body))
    svc = cls(_settings(**skw))
    assert await svc.generate("p") == "OK"


@pytest.mark.parametrize("cls, skw", [
    pytest.param(OpenRouterLLMProvider, dict(openrouter_api_key=""), id="openrouter"),
    pytest.param(GeminiLLMProvider, dict(gemini_api_key=""), id="gemini"),
    pytest.param(MistralLLMProvider, dict(mistral_api_key=""), id="mistral"),
])
def test_missing_api_key_raises_at_construction(cls, skw):
    with pytest.raises(LLMProviderError):
        cls(_settings(**skw))
