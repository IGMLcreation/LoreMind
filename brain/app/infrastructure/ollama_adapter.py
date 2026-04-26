"""Adapter Ollama — implémentation concrète des ports LLMProvider et LLMChatProvider.

Isole le reste de l'application des spécificités du protocole Ollama
(URL /api/generate, /api/chat, payload, parsing). Pour swap vers OpenAI
demain, on écrit un nouvel adapter sans toucher au reste du code.
"""
import json
from typing import AsyncIterator

import httpx

from app.core.config import Settings
from app.domain.models import ChatMessage
from app.domain.ports import LLMProviderError


class OllamaLLMProvider:
    """Implémentation des ports LLM — appelle un serveur Ollama via HTTP.

    Satisfait implicitement (duck typing) à la fois `LLMProvider` (endpoint
    /api/generate, appel unique) et `LLMChatProvider` (endpoint /api/chat,
    streaming token par token).
    """

    def __init__(self, settings: Settings) -> None:
        self._base_url = settings.ollama_base_url
        self._model = settings.llm_model
        self._timeout = settings.llm_timeout_seconds
        self._num_ctx = settings.llm_num_ctx

    def _build_options(self, temperature: float | None) -> dict[str, object]:
        """Construit le dict `options` attendu par Ollama (hyperparamètres).

        `num_ctx` est TOUJOURS envoyé — sinon Ollama retombe sur son défaut
        2048 et tronque silencieusement les gros prompts (Structural Context
        du Lore enrichi depuis b9). `temperature` n'est ajoutée que si
        fournie par le use case (sinon Ollama utilise son défaut).
        """
        options: dict[str, object] = {"num_ctx": self._num_ctx}
        if temperature is not None:
            options["temperature"] = temperature
        return options

    async def generate(
        self,
        prompt: str,
        *,
        output_format: str | None = None,
        temperature: float | None = None,
    ) -> str:
        url = f"{self._base_url}/api/generate"
        payload: dict[str, object] = {
            "model": self._model,
            "prompt": prompt,
            "stream": False,
            "options": self._build_options(temperature),
        }
        if output_format is not None:
            payload["format"] = output_format

        async with httpx.AsyncClient(timeout=self._timeout) as client:
            try:
                response = await client.post(url, json=payload)
                if response.status_code >= 400:
                    body = response.text
                    try:
                        err_obj = json.loads(body)
                        err_msg = err_obj.get("error") or body
                    except json.JSONDecodeError:
                        err_msg = body
                    raise LLMProviderError(
                        f"Ollama HTTP {response.status_code} : {err_msg.strip()[:500]}"
                    )
            except httpx.HTTPError as exc:
                raise LLMProviderError(
                    f"Erreur lors de l'appel à Ollama : {exc}"
                ) from exc

        return response.json()["response"]

    async def stream_chat(
        self,
        messages: list[ChatMessage],
        *,
        system_prompt: str | None = None,
        temperature: float | None = None,
    ) -> AsyncIterator[str]:
        """Streame depuis Ollama /api/chat. Parse le NDJSON ligne par ligne.

        Ollama renvoie un JSON par ligne au fil de la génération :
          - étapes intermédiaires : `{"message": {"content": "token"}, "done": false}`
          - étape finale           : `{"done": true, ...}`

        On yield chaque token non-vide au consommateur, qui se charge du
        formatage SSE (c'est la responsabilité du controller HTTP, pas
        de l'adapter LLM).
        """
        url = f"{self._base_url}/api/chat"

        payload_messages: list[dict[str, str]] = []
        if system_prompt:
            payload_messages.append({"role": "system", "content": system_prompt})
        payload_messages.extend(
            {"role": m.role, "content": m.content} for m in messages
        )

        payload: dict[str, object] = {
            "model": self._model,
            "messages": payload_messages,
            "stream": True,
            "options": self._build_options(temperature),
        }

        async with httpx.AsyncClient(timeout=self._timeout) as client:
            try:
                async with client.stream("POST", url, json=payload) as response:
                    if response.status_code >= 400:
                        # On lit le body d'erreur pour le remonter a l'utilisateur,
                        # sinon on ne voit que "500 Internal Server Error" sans
                        # savoir POURQUOI Ollama refuse (modele introuvable, OOM,
                        # num_ctx trop grand pour la VRAM, etc.).
                        body = (await response.aread()).decode("utf-8", errors="replace")
                        try:
                            err_obj = json.loads(body)
                            err_msg = err_obj.get("error") or body
                        except json.JSONDecodeError:
                            err_msg = body
                        raise LLMProviderError(
                            f"Ollama HTTP {response.status_code} : {err_msg.strip()[:500]}"
                        )
                    async for line in response.aiter_lines():
                        if not line.strip():
                            continue
                        chunk = json.loads(line)
                        if chunk.get("done"):
                            break
                        token = chunk.get("message", {}).get("content", "")
                        if token:
                            yield token
            except httpx.HTTPError as exc:
                raise LLMProviderError(
                    f"Erreur lors du streaming Ollama : {exc}"
                ) from exc
