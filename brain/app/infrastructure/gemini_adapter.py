"""Adapter Google Gemini — implémente les ports LLMProvider / LLMChatProvider.

Gemini expose un endpoint COMPATIBLE OpenAI
(POST {base}/openai/chat/completions, SSE), donc cet adapter est un client
"OpenAI-compatible" — même structure que les adapters OpenRouter / Mistral.

Tier GRATUIT : clé API sur aistudio.google.com (sans CB). Atout majeur pour
l'extraction de PDF : un CONTEXTE de ~1M tokens → un livre entier tient en 1-2
appels, donc quasi aucun morceau perdu et peu de requêtes (limites jamais
atteintes). Modèle conseillé : `gemini-2.0-flash` (rapide, gros contexte, fidèle).
"""
from __future__ import annotations

import asyncio
import json
import logging
from typing import AsyncIterator

import httpx

from app.core.config import Settings
from app.domain.models import ChatMessage
from app.domain.ports import LLMGenerationTimeout, LLMProviderError

logger = logging.getLogger(__name__)

_API_URL = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"

# Délai max pour le PREMIER token de contenu (échec rapide si le modèle ne produit
# rien). Gemini répond vite ; 120s est large.
_FIRST_TOKEN_TIMEOUT_SECONDS = 120.0


class GeminiLLMProvider:
    """Adapter Gemini (OpenAI-compatible) — satisfait LLMProvider et LLMChatProvider."""

    def __init__(self, settings: Settings) -> None:
        if not settings.gemini_api_key:
            raise LLMProviderError(
                "Clé API Gemini manquante. Configure-la depuis l'écran Paramètres "
                "(clé gratuite sur aistudio.google.com)."
            )
        self._api_key = settings.gemini_api_key
        self._model = settings.gemini_model
        self._timeout = settings.llm_timeout_seconds

    def _headers(self) -> dict[str, str]:
        return {
            "Authorization": f"Bearer {self._api_key}",
            "Content-Type": "application/json",
            "Accept": "application/json",
        }

    async def generate(
        self,
        prompt: str,
        *,
        output_format: str | None = None,
        temperature: float | None = None,
    ) -> str:
        """One-shot via streaming (puis recollage), avec garde-fous au temps écoulé."""
        return await self._collect_with_timeouts(
            [ChatMessage(role="user", content=prompt)], temperature, output_format
        )

    async def _collect_with_timeouts(
        self,
        messages: list[ChatMessage],
        temperature: float | None,
        output_format: str | None,
    ) -> str:
        """Collecte le stream avec deux garde-fous : 1er token borné (échec rapide
        si rien ne sort) + ceiling global `self._timeout`."""
        async def _collect() -> str:
            chunks: list[str] = []
            agen = self._stream(messages, None, temperature, output_format)
            try:
                while True:
                    first = _FIRST_TOKEN_TIMEOUT_SECONDS if not chunks else None
                    try:
                        token = await asyncio.wait_for(agen.__anext__(), timeout=first)
                    except StopAsyncIteration:
                        break
                    except asyncio.TimeoutError:
                        raise LLMProviderError(
                            f"Erreur Gemini : aucun contenu produit en "
                            f"{int(_FIRST_TOKEN_TIMEOUT_SECONDS)}s. Réessayez ou vérifiez "
                            "votre quota gratuit."
                        )
                    chunks.append(token)
            finally:
                await agen.aclose()
            return "".join(chunks)

        try:
            return await asyncio.wait_for(_collect(), timeout=self._timeout)
        except asyncio.TimeoutError as exc:
            raise LLMGenerationTimeout(
                f"Erreur Gemini : génération non terminée en {self._timeout}s. Réduisez la "
                "taille des morceaux d'import ou augmentez le timeout."
            ) from exc

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
        output_format: str | None = None,
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
        # Mode JSON natif (supporté par l'endpoint OpenAI-compatible de Gemini) :
        # supprime fences ```json et JSON invalide, principale cause de morceaux
        # ignorés. Un SCHÉMA (dict) est traduit en json_object : suffisant, les
        # grands modèles cloud respectent la structure demandée par le prompt.
        if output_format is not None:
            body["response_format"] = {"type": "json_object"}

        async with httpx.AsyncClient(timeout=self._timeout) as client:
            try:
                async with client.stream(
                    "POST", _API_URL, headers=self._headers(), json=body
                ) as response:
                    if response.status_code >= 400:
                        detail = (await response.aread()).decode("utf-8", "replace").strip()
                        raise LLMProviderError(
                            f"Erreur Gemini (HTTP {response.status_code})"
                            + (f" : {detail[:500]}" if detail else "")
                        )
                    async for token in self._parse_sse(response):
                        yield token
            except httpx.HTTPError as exc:
                raise LLMProviderError(self._format_http_error(exc)) from exc

    @staticmethod
    async def _parse_sse(response: httpx.Response) -> AsyncIterator[str]:
        """SSE OpenAI : lignes `data: {json}`, fin sur `data: [DONE]`."""
        async for line in response.aiter_lines():
            if not line or not line.startswith("data:"):
                continue
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
        if isinstance(exc, httpx.TimeoutException):
            return (
                f"Erreur Gemini : délai dépassé (timeout {self._timeout}s). Le modèle a "
                "mis trop de temps — réduis la taille des morceaux d'import ou augmente le timeout."
            )
        detail = str(exc) or exc.__class__.__name__
        return f"Erreur Gemini ({exc.__class__.__name__}) : {detail}"
