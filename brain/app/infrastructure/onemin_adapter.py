"""Adapter 1min.ai — implementation alternative des ports LLMProvider / LLMChatProvider.

API 1min.ai (cf. https://docs.1min.ai/docs/api/chat-with-ai-api) :
  - POST https://api.1min.ai/api/chat-with-ai            (one-shot)
  - POST https://api.1min.ai/api/chat-with-ai?isStreaming=true  (SSE)
  - Auth : header "API-KEY: <cle>"
  - Body : {"type": "UNIFY_CHAT_WITH_AI", "model": "...",
            "promptObject": {"prompt": "..."}}

Le port LoreMind expose une API "messages[]", mais 1min.ai attend un prompt
unique. On aplatit donc l'historique + system prompt en un seul bloc texte,
avec des marqueurs de role lisibles pour le modele.
"""
from __future__ import annotations

import json
from typing import AsyncIterator

import httpx

from app.core.config import Settings
from app.domain.models import ChatMessage
from app.domain.ports import LLMProviderError

_API_BASE = "https://api.1min.ai/api/chat-with-ai"
_PAYLOAD_TYPE = "UNIFY_CHAT_WITH_AI"


class OneMinAiLLMProvider:
    """Adapter 1min.ai — satisfait LLMProvider et LLMChatProvider par duck typing."""

    def __init__(self, settings: Settings) -> None:
        if not settings.onemin_api_key:
            raise LLMProviderError(
                "Cle API 1min.ai manquante. Configure-la depuis l'ecran Parametres."
            )
        self._api_key = settings.onemin_api_key
        self._model = settings.onemin_model
        self._timeout = settings.llm_timeout_seconds

    def _headers(self) -> dict[str, str]:
        return {"API-KEY": self._api_key, "Content-Type": "application/json"}

    def _payload(self, prompt: str) -> dict[str, object]:
        return {
            "type": _PAYLOAD_TYPE,
            "model": self._model,
            "promptObject": {"prompt": prompt},
        }

    async def generate(
        self,
        prompt: str,
        *,
        output_format: str | None = None,  # 1min.ai ne supporte pas format=json
        temperature: float | None = None,  # idem, pas d'hyperparam expose ici
    ) -> str:
        """Appel one-shot : retourne la reponse complete sous forme de string."""
        async with httpx.AsyncClient(timeout=self._timeout) as client:
            try:
                response = await client.post(
                    _API_BASE, headers=self._headers(), json=self._payload(prompt)
                )
                response.raise_for_status()
                data = response.json()
            except httpx.HTTPError as exc:
                raise LLMProviderError(f"Erreur 1min.ai : {exc}") from exc

        return self._extract_result(data)

    async def stream_chat(
        self,
        messages: list[ChatMessage],
        *,
        system_prompt: str | None = None,
        temperature: float | None = None,
    ) -> AsyncIterator[str]:
        """Streame via SSE.

        1min.ai expose deux evenements utiles :
          - `event: content`  → `data: {"content": "..."}`
          - `event: done`     → fin du stream
          - `event: error`    → erreur serveur
        On yield le champ `content` au fil de l'arrivee.
        """
        prompt = self._flatten_messages(messages, system_prompt)
        url = f"{_API_BASE}?isStreaming=true"

        async with httpx.AsyncClient(timeout=self._timeout) as client:
            try:
                async with client.stream(
                    "POST", url, headers=self._headers(), json=self._payload(prompt)
                ) as response:
                    response.raise_for_status()
                    async for token in self._parse_sse(response):
                        yield token
            except httpx.HTTPError as exc:
                raise LLMProviderError(
                    f"Erreur lors du streaming 1min.ai : {exc}"
                ) from exc

    # --- Helpers ------------------------------------------------------------

    @staticmethod
    async def _parse_sse(response: httpx.Response) -> AsyncIterator[str]:
        """Decoupe le flux SSE ligne par ligne et yield les chunks 'content'."""
        current_event: str | None = None
        current_data = ""
        async for line in response.aiter_lines():
            if line == "":
                # Fin d'un evenement SSE : dispatch
                if current_event == "done":
                    return
                if current_event == "error":
                    raise LLMProviderError(f"1min.ai a signale une erreur : {current_data}")
                if current_data and current_event in (None, "content", "message"):
                    token = OneMinAiLLMProvider._extract_content_chunk(current_data)
                    if token:
                        yield token
                current_event = None
                current_data = ""
                continue
            if line.startswith("event:"):
                current_event = line[6:].strip()
            elif line.startswith("data:"):
                chunk = line[5:].lstrip()
                current_data = f"{current_data}\n{chunk}" if current_data else chunk

    @staticmethod
    def _extract_content_chunk(data: str) -> str:
        """Extrait le champ `content` d'un data JSON, avec tolerance si format brut."""
        try:
            obj = json.loads(data)
        except json.JSONDecodeError:
            return data  # filet de securite si le serveur envoie du texte brut
        if isinstance(obj, dict):
            return obj.get("content") or obj.get("token") or ""
        return ""

    @staticmethod
    def _extract_result(payload: dict) -> str:
        """Extrait le texte final d'une reponse non-streamee.

        Schema attendu : `aiRecord.aiRecordDetail.resultObject` (list[str]).
        On concatene par securite (le serveur renvoie habituellement un seul element).
        """
        record = payload.get("aiRecord") or {}
        detail = record.get("aiRecordDetail") or {}
        result = detail.get("resultObject") or []
        if isinstance(result, list):
            return "".join(str(x) for x in result)
        if isinstance(result, str):
            return result
        raise LLMProviderError("Reponse 1min.ai inattendue : resultObject absent.")

    @staticmethod
    def _flatten_messages(
        messages: list[ChatMessage], system_prompt: str | None
    ) -> str:
        """Transforme [system_prompt, history] en un unique prompt textuel.

        1min.ai n'accepte qu'un champ `prompt` : on serialise la conversation
        avec des marqueurs explicites pour que le modele comprenne les tours.
        """
        parts: list[str] = []
        if system_prompt:
            parts.append(f"[SYSTEM]\n{system_prompt}")
        if messages:
            history = "\n\n".join(
                f"[{m.role.upper()}]\n{m.content}" for m in messages
            )
            parts.append(history)
        parts.append("[ASSISTANT]")  # invite le modele a continuer
        return "\n\n".join(parts)
