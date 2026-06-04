"""Adapter OpenRouter — implémente les ports LLMProvider / LLMChatProvider.

OpenRouter expose l'API OpenAI standard (POST {base}/chat/completions, SSE), donc
cet adapter est en réalité un client "OpenAI-compatible". Le `generate` one-shot
passe lui aussi par le streaming (puis recollage) pour éviter les coupures de
passerelle sur les longues générations (cf. 1min.ai / Cloudflare 524).

Modèles GRATUITS : utiliser un id finissant par `:free` (ex.
`meta-llama/llama-3.3-70b-instruct:free`) ou le routeur `openrouter/free` (défaut)
qui choisit automatiquement un modèle gratuit — aucun crédit consommé.
"""
from __future__ import annotations

import json
from typing import AsyncIterator

import httpx

from app.core.config import Settings
from app.domain.models import ChatMessage
from app.domain.ports import LLMProviderError

_API_URL = "https://openrouter.ai/api/v1/chat/completions"


class OpenRouterLLMProvider:
    """Adapter OpenRouter (OpenAI-compatible) — satisfait LLMProvider et LLMChatProvider."""

    def __init__(self, settings: Settings) -> None:
        if not settings.openrouter_api_key:
            raise LLMProviderError(
                "Clé API OpenRouter manquante. Configure-la depuis l'écran Paramètres."
            )
        self._api_key = settings.openrouter_api_key
        self._model = settings.openrouter_model
        self._timeout = settings.llm_timeout_seconds

    def _headers(self) -> dict[str, str]:
        return {
            "Authorization": f"Bearer {self._api_key}",
            "Content-Type": "application/json",
            # Attribution facultative (classement OpenRouter) — sans impact fonctionnel.
            "HTTP-Referer": "https://loremind.app",
            "X-Title": "LoreMind",
        }

    async def generate(
        self,
        prompt: str,
        *,
        output_format: str | None = None,
        temperature: float | None = None,
    ) -> str:
        """One-shot via streaming (puis recollage) pour robustesse sur longues sorties."""
        chunks: list[str] = []
        async for token in self._stream([ChatMessage(role="user", content=prompt)], None, temperature):
            chunks.append(token)
        return "".join(chunks)

    async def stream_chat(
        self,
        messages: list[ChatMessage],
        *,
        system_prompt: str | None = None,
        temperature: float | None = None,
    ) -> AsyncIterator[str]:
        async for token in self._stream(messages, system_prompt, temperature):
            yield token

    async def _stream(
        self,
        messages: list[ChatMessage],
        system_prompt: str | None,
        temperature: float | None,
    ) -> AsyncIterator[str]:
        payload_messages: list[dict[str, str]] = []
        if system_prompt:
            payload_messages.append({"role": "system", "content": system_prompt})
        for m in messages:
            payload_messages.append({"role": m.role, "content": m.content})

        body: dict[str, object] = {
            "model": self._model,
            "messages": payload_messages,
            "stream": True,
        }
        if temperature is not None:
            body["temperature"] = temperature

        async with httpx.AsyncClient(timeout=self._timeout) as client:
            try:
                async with client.stream(
                    "POST", _API_URL, headers=self._headers(), json=body
                ) as response:
                    response.raise_for_status()
                    async for token in self._parse_sse(response):
                        yield token
            except httpx.HTTPError as exc:
                raise LLMProviderError(self._format_http_error(exc)) from exc

    @staticmethod
    async def _parse_sse(response: httpx.Response) -> AsyncIterator[str]:
        """SSE OpenAI : lignes `data: {json}`, fin sur `data: [DONE]`."""
        async for line in response.aiter_lines():
            if not line or not line.startswith("data:"):
                continue  # lignes vides ou commentaires keep-alive (`: ...`)
            data = line[len("data:"):].strip()
            if data == "[DONE]":
                return
            try:
                obj = json.loads(data)
            except json.JSONDecodeError:
                continue
            choices = obj.get("choices")
            if not choices:
                continue
            delta = choices[0].get("delta") or {}
            content = delta.get("content")
            if content:
                yield content

    def _format_http_error(self, exc: httpx.HTTPError) -> str:
        """Message lisible (timeout, quota 429, crédits 402, modèle inconnu…)."""
        if isinstance(exc, httpx.TimeoutException):
            return (
                f"Erreur OpenRouter : délai dépassé (timeout {self._timeout}s). Le modèle a "
                "mis trop de temps — réduis la taille des morceaux d'import ou augmente le timeout."
            )
        detail = str(exc) or exc.__class__.__name__
        return f"Erreur OpenRouter ({exc.__class__.__name__}) : {detail}"
