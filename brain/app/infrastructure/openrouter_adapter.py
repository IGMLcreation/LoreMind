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

import asyncio
import json
import logging
from typing import AsyncIterator

import httpx

from app.core.config import Settings
from app.domain.models import ChatMessage

logger = logging.getLogger(__name__)
from app.domain.ports import LLMProviderError

_API_URL = "https://openrouter.ai/api/v1/chat/completions"

# Délai max pour le PREMIER token de contenu. Un modèle gratuit "en file d'attente"
# n'envoie que des keep-alive (aucun contenu) → on échoue vite et clairement au lieu
# de pendre. Généreux (2 min) car la file d'attente d'un tier gratuit peut être longue.
_FIRST_TOKEN_TIMEOUT_SECONDS = 120.0


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
        """One-shot via streaming (puis recollage) pour robustesse sur longues sorties.

        Timeout au TEMPS ÉCOULÉ (asyncio) en plus du timeout réseau d'httpx : un
        modèle gratuit saturé/en file d'attente envoie des keep-alive (`: OPENROUTER
        PROCESSING`) mais AUCUN contenu → httpx ne déclenche jamais son read-timeout
        (des octets arrivent) et l'appel pendrait à l'infini. Ici on coupe net après
        `self._timeout` secondes, quoi qu'il arrive.
        """
        return await self._collect_with_timeouts(
            [ChatMessage(role="user", content=prompt)], temperature, output_format, "OpenRouter"
        )

    async def _collect_with_timeouts(
        self,
        messages: list[ChatMessage],
        temperature: float | None,
        output_format: str | None,
        provider: str,
    ) -> str:
        """Collecte le stream avec DEUX garde-fous au temps écoulé :
        - 1er token borné (`_FIRST_TOKEN_TIMEOUT_SECONDS`) : détecte un modèle bloqué
          en file d'attente (que des keep-alive, aucun contenu) → échec rapide ;
        - ceiling global (`self._timeout`) : génération qui ne se termine jamais.
        Le timeout réseau d'httpx ne suffit pas : des keep-alive font 'arriver des
        octets' et empêchent son read-timeout de se déclencher.
        """
        async def _collect() -> str:
            chunks: list[str] = []
            agen = self._stream(messages, None, temperature, output_format)
            try:
                while True:
                    # Borne SEULEMENT l'attente du 1er token (file d'attente) ; ensuite
                    # on laisse générer (le ceiling global couvre le reste).
                    first = _FIRST_TOKEN_TIMEOUT_SECONDS if not chunks else None
                    try:
                        token = await asyncio.wait_for(agen.__anext__(), timeout=first)
                    except StopAsyncIteration:
                        break
                    except asyncio.TimeoutError:
                        raise LLMProviderError(
                            f"Erreur {provider} : aucun contenu produit en "
                            f"{int(_FIRST_TOKEN_TIMEOUT_SECONDS)}s — le modèle gratuit est "
                            "probablement en file d'attente / saturé. Réessayez plus tard ou "
                            "choisissez un autre modèle (1min.ai, ou payant)."
                        )
                    chunks.append(token)
            finally:
                await agen.aclose()
            return "".join(chunks)

        try:
            return await asyncio.wait_for(_collect(), timeout=self._timeout)
        except asyncio.TimeoutError as exc:
            raise LLMProviderError(
                f"Erreur {provider} : génération non terminée en {self._timeout}s. Réduisez la "
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
        # NB : on n'impose PAS `response_format=json_object`. Beaucoup de modèles/
        # providers GRATUITS ne le supportent pas et renvoient une réponse VIDE.
        # On laisse le modèle répondre librement ; l'extraction JSON en aval
        # (load_json_object + nettoyage du raisonnement) récupère le JSON dans la prose.

        async with httpx.AsyncClient(timeout=self._timeout) as client:
            try:
                async with client.stream(
                    "POST", _API_URL, headers=self._headers(), json=body
                ) as response:
                    if response.status_code >= 400:
                        # En streaming, le corps n'est pas lu automatiquement : on le
                        # lit pour exposer le détail d'OpenRouter (ex. le 429 précise
                        # "free-models-per-day" vs "per-minute"), sinon on n'a que le
                        # code HTTP nu et le diagnostic est impossible.
                        detail = (await response.aread()).decode("utf-8", "replace").strip()
                        raise LLMProviderError(
                            f"Erreur OpenRouter (HTTP {response.status_code})"
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
