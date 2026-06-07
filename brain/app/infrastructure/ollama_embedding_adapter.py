"""Adapter d'embeddings Ollama (local) — endpoint /api/embed.

Gratuit et illimité (tourne sur la machine). Nécessite d'avoir pullé le modèle
d'embedding (ex. `ollama pull nomic-embed-text`).
"""
from __future__ import annotations

import httpx

from app.application.embeddings import EmbeddingError
from app.core.config import Settings


class OllamaEmbeddingProvider:
    """Implémente EmbeddingProvider via Ollama /api/embed (batch)."""

    def __init__(self, settings: Settings) -> None:
        self._base_url = settings.ollama_base_url
        self._model = settings.ollama_embedding_model
        self._timeout = settings.llm_timeout_seconds

    async def embed(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            return []
        url = f"{self._base_url}/api/embed"
        payload = {"model": self._model, "input": texts}
        async with httpx.AsyncClient(timeout=self._timeout) as client:
            try:
                response = await client.post(url, json=payload)
                if response.status_code >= 400:
                    body = response.text
                    raise EmbeddingError(
                        f"Ollama embeddings HTTP {response.status_code} : {body.strip()[:300]}. "
                        f"Le modèle '{self._model}' est-il installé ? (ollama pull {self._model})"
                    )
                data = response.json()
            except httpx.HTTPError as exc:
                raise EmbeddingError(f"Erreur Ollama embeddings : {exc}") from exc

        vectors = data.get("embeddings")
        if not isinstance(vectors, list) or len(vectors) != len(texts):
            raise EmbeddingError("Réponse d'embeddings Ollama inattendue (taille incohérente).")
        return [[float(x) for x in v] for v in vectors]
