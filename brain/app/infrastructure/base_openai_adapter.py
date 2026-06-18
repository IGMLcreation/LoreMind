"""Socle commun aux adapters LLM « OpenAI-compatible » (OpenRouter, Gemini,
Mistral) — ils exposent tous `POST {base}/chat/completions` en SSE avec le même
schéma de payload et de flux.

Cette classe de base porte la mécanique partagée (construction du payload, appel
HTTP streamé, parsing SSE, garde-fous de timeout au temps écoulé, traduction des
erreurs). Chaque adapter concret ne fournit plus que ses spécificités :
URL, en-têtes, support du mode JSON natif, messages d'erreur, lecture de la config.

`generate` one-shot passe lui aussi par le streaming (puis recollage) pour éviter
les coupures de passerelle sur les longues générations (cf. Cloudflare 524).
"""
from __future__ import annotations

import asyncio
import json
import logging
from typing import AsyncIterator

import httpx

from app.domain.models import ChatMessage
from app.domain.ports import LLMGenerationTimeout, LLMProviderError

logger = logging.getLogger(__name__)

# Délai max pour le PREMIER token de contenu. Un modèle « en file d'attente »
# n'envoie que des keep-alive (aucun contenu) → on échoue vite et clairement au
# lieu de pendre. Le timeout réseau d'httpx ne suffit pas : des keep-alive font
# « arriver des octets » et empêchent son read-timeout de se déclencher.
_FIRST_TOKEN_TIMEOUT_SECONDS = 120.0


class BaseOpenAICompatibleAdapter:
    """Base des adapters clients d'une API OpenAI-compatible (chat/completions SSE).

    Satisfait par duck typing les ports LLMProvider et LLMChatProvider. Les
    sous-classes définissent : ``_provider_label``, ``_api_url``,
    ``_supports_json_object`` (mode JSON natif), et surchargent au besoin
    ``_headers`` / ``_error_for_status`` / les messages de timeout.
    """

    # Surchargés par les sous-classes.
    _provider_label: str = "LLM"
    _api_url: str = ""
    _supports_json_object: bool = False

    def __init__(self, api_key: str, model: str, timeout: int) -> None:
        self._api_key = api_key
        self._model = model
        self._timeout = timeout

    # --- Spécificités surchargeables ----------------------------------------

    def _headers(self) -> dict[str, str]:
        return {
            "Authorization": f"Bearer {self._api_key}",
            "Content-Type": "application/json",
        }

    def _first_token_timeout_message(self) -> str:
        return (
            f"Erreur {self._provider_label} : aucun contenu produit en "
            f"{int(_FIRST_TOKEN_TIMEOUT_SECONDS)}s — le modèle est probablement en "
            "file d'attente / saturé. Réessayez plus tard ou choisissez un autre modèle."
        )

    def _generation_timeout_message(self) -> str:
        return (
            f"Erreur {self._provider_label} : génération non terminée en {self._timeout}s. "
            "Réduisez la taille des morceaux d'import, augmentez le timeout, ou changez de modèle."
        )

    def _error_for_status(self, status_code: int, detail: str) -> LLMProviderError:
        """Erreur de domaine pour une réponse HTTP >= 400 (détail déjà lu)."""
        return LLMProviderError(
            f"Erreur {self._provider_label} (HTTP {status_code})"
            + (f" : {detail[:500]}" if detail else "")
        )

    # --- API publique (ports) -----------------------------------------------

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

    async def stream_chat(
        self,
        messages: list[ChatMessage],
        *,
        system_prompt: str | None = None,
        temperature: float | None = None,
    ) -> AsyncIterator[str]:
        async for token in self._stream(messages, system_prompt, temperature):
            yield token

    # --- Mécanique partagée -------------------------------------------------

    async def _collect_with_timeouts(
        self,
        messages: list[ChatMessage],
        temperature: float | None,
        output_format: str | None,
    ) -> str:
        """Collecte le stream avec DEUX garde-fous au temps écoulé :
        - 1er token borné (`_FIRST_TOKEN_TIMEOUT_SECONDS`) : détecte un modèle bloqué
          en file d'attente (que des keep-alive, aucun contenu) → échec rapide ;
        - ceiling global (`self._timeout`) : génération qui ne se termine jamais.
        """
        async def _collect() -> str:
            chunks: list[str] = []
            agen = self._stream(messages, None, temperature, output_format)
            try:
                while True:
                    # Borne SEULEMENT l'attente du 1er token ; ensuite on laisse
                    # générer (le ceiling global couvre le reste).
                    first = _FIRST_TOKEN_TIMEOUT_SECONDS if not chunks else None
                    try:
                        token = await asyncio.wait_for(agen.__anext__(), timeout=first)
                    except StopAsyncIteration:
                        break
                    except asyncio.TimeoutError:
                        raise LLMProviderError(self._first_token_timeout_message())
                    chunks.append(token)
            finally:
                await agen.aclose()
            return "".join(chunks)

        try:
            return await asyncio.wait_for(_collect(), timeout=self._timeout)
        except asyncio.TimeoutError as exc:
            raise LLMGenerationTimeout(self._generation_timeout_message()) from exc

    def _build_body(
        self,
        messages: list[ChatMessage],
        system_prompt: str | None,
        temperature: float | None,
        output_format: str | None,
    ) -> dict[str, object]:
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
        # Mode JSON natif : supprime les fences ```json et le JSON invalide (retours
        # à la ligne bruts), principale cause de morceaux d'import ignorés. Un SCHÉMA
        # (dict) est traduit en json_object — suffisant, les grands modèles cloud
        # respectent la structure demandée par le prompt. Désactivé pour les
        # providers/modèles gratuits qui ne le supportent pas (réponse vide).
        if self._supports_json_object and output_format is not None:
            body["response_format"] = {"type": "json_object"}
        return body

    async def _stream(
        self,
        messages: list[ChatMessage],
        system_prompt: str | None,
        temperature: float | None,
        output_format: str | None = None,
    ) -> AsyncIterator[str]:
        body = self._build_body(messages, system_prompt, temperature, output_format)
        async with httpx.AsyncClient(timeout=self._timeout) as client:
            try:
                async with client.stream(
                    "POST", self._api_url, headers=self._headers(), json=body
                ) as response:
                    if response.status_code >= 400:
                        # En streaming le corps n'est pas lu automatiquement : on le
                        # lit pour exposer le détail du provider (le 429 précise le
                        # type de quota, le 401 la clé invalide…), sinon on n'a que
                        # le code HTTP nu et le diagnostic est impossible.
                        detail = (await response.aread()).decode("utf-8", "replace").strip()
                        raise self._error_for_status(response.status_code, detail)
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
                f"Erreur {self._provider_label} : délai dépassé (timeout {self._timeout}s). "
                "Le modèle a mis trop de temps — réduis la taille des morceaux d'import ou "
                "augmente le timeout."
            )
        detail = str(exc) or exc.__class__.__name__
        return f"Erreur {self._provider_label} ({exc.__class__.__name__}) : {detail}"
