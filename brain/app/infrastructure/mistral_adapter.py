"""Adapter Mistral — implémente les ports LLMProvider / LLMChatProvider.

Mistral (La Plateforme) expose l'API OpenAI standard (POST {base}/chat/completions,
SSE), donc cet adapter est un client "OpenAI-compatible" — même structure que
l'adapter OpenRouter. Le `generate` one-shot passe par le streaming (puis
recollage) avec un timeout au temps écoulé pour ne jamais pendre à l'infini.

Tier GRATUIT : compte sur console.mistral.ai (tier « Experiment »), clé API à
coller dans l'écran Paramètres. Modèles conseillés pour l'extraction : un grand
contexte fidèle comme `mistral-large-latest` (128k) ou `mistral-small-latest`.
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

_API_URL = "https://api.mistral.ai/v1/chat/completions"

# Délai max pour le PREMIER token de contenu (échec rapide si le modèle est en file
# d'attente et n'envoie que des keep-alive). Généreux car la file d'un tier gratuit
# peut être longue.
_FIRST_TOKEN_TIMEOUT_SECONDS = 120.0


class MistralLLMProvider:
    """Adapter Mistral (OpenAI-compatible) — satisfait LLMProvider et LLMChatProvider."""

    def __init__(self, settings: Settings) -> None:
        if not settings.mistral_api_key:
            raise LLMProviderError(
                "Clé API Mistral manquante. Configure-la depuis l'écran Paramètres."
            )
        self._api_key = settings.mistral_api_key
        self._model = settings.mistral_model
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
        """One-shot via streaming (puis recollage) pour robustesse sur longues sorties.

        Timeout au TEMPS ÉCOULÉ (asyncio) en plus du timeout réseau d'httpx :
        si le provider envoyait des keep-alive sans contenu, l'appel pendrait à
        l'infini. Ici on coupe net après `self._timeout` secondes.
        """
        return await self._collect_with_timeouts(
            [ChatMessage(role="user", content=prompt)], temperature, output_format
        )

    async def _collect_with_timeouts(
        self,
        messages: list[ChatMessage],
        temperature: float | None,
        output_format: str | None,
    ) -> str:
        """Collecte le stream avec deux garde-fous au temps écoulé : 1er token borné
        (file d'attente → échec rapide) + ceiling global `self._timeout`."""
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
                            f"Erreur Mistral : aucun contenu produit en "
                            f"{int(_FIRST_TOKEN_TIMEOUT_SECONDS)}s — le modèle est probablement "
                            "en file d'attente (tier gratuit, 2 req/min). Réessayez plus tard ou "
                            "choisissez un modèle plus disponible."
                        )
                    chunks.append(token)
            finally:
                await agen.aclose()
            return "".join(chunks)

        try:
            return await asyncio.wait_for(_collect(), timeout=self._timeout)
        except asyncio.TimeoutError as exc:
            raise LLMGenerationTimeout(
                f"Erreur Mistral : génération non terminée en {self._timeout}s. Réduisez la "
                "taille des morceaux d'import, augmentez le timeout, ou changez de modèle."
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
        # Mode JSON natif : TOUS les modèles Mistral le supportent → plus de fences
        # ```json ni de JSON invalide (retours à la ligne bruts dans les chaînes),
        # principale cause de morceaux d'import ignorés. Un SCHÉMA (dict) est
        # traduit en json_object : suffisant ici, les grands modèles cloud
        # respectent la structure demandée par le prompt.
        if output_format is not None:
            body["response_format"] = {"type": "json_object"}

        async with httpx.AsyncClient(timeout=self._timeout) as client:
            try:
                async with client.stream(
                    "POST", _API_URL, headers=self._headers(), json=body
                ) as response:
                    if response.status_code >= 400:
                        # En streaming le corps n'est pas lu automatiquement : on le
                        # lit pour exposer le détail de Mistral (modèle inconnu, clé
                        # invalide 401, quota 429…), sinon on n'a que le code HTTP nu.
                        detail = (await response.aread()).decode("utf-8", "replace").strip()
                        raise LLMProviderError(
                            f"Erreur Mistral (HTTP {response.status_code})"
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
                continue  # lignes vides ou keep-alive (`: ...`)
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
        """Message lisible (timeout, quota 429, clé invalide 401, modèle inconnu…)."""
        if isinstance(exc, httpx.TimeoutException):
            return (
                f"Erreur Mistral : délai dépassé (timeout {self._timeout}s). Le modèle a "
                "mis trop de temps — réduis la taille des morceaux d'import ou augmente le timeout."
            )
        detail = str(exc) or exc.__class__.__name__
        return f"Erreur Mistral ({exc.__class__.__name__}) : {detail}"
