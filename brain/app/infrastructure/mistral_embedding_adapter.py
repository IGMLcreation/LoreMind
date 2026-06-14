"""Adapter d'embeddings Mistral (cloud, EU) — POST /v1/embeddings.

Soumis au rate limit du tier gratuit : pour indexer un gros document on envoie
les textes par lots (et l'appelant peut espacer les appels si besoin).
"""
from __future__ import annotations

import httpx

from app.application.embeddings import EmbeddingError
from app.core.config import Settings

_API_URL = "https://api.mistral.ai/v1/embeddings"
# Lot raisonnable pour ne pas envoyer un payload géant d'un coup.
_BATCH_SIZE = 64


class MistralEmbeddingProvider:
    """Implémente EmbeddingProvider via l'API Mistral embeddings."""

    def __init__(self, settings: Settings) -> None:
        if not settings.mistral_api_key:
            raise EmbeddingError(
                "Clé API Mistral manquante (requise pour les embeddings Mistral). "
                "Configure-la dans les Paramètres ou choisis Ollama pour les embeddings."
            )
        self._api_key = settings.mistral_api_key
        self._model = settings.mistral_embedding_model
        self._timeout = settings.llm_timeout_seconds

    async def embed(self, texts: list[str], kind: str = "document") -> list[list[float]]:
        # `kind` ignoré : mistral-embed n'utilise pas de préfixe de tâche.
        if not texts:
            return []
        out: list[list[float]] = []
        headers = {
            "Authorization": f"Bearer {self._api_key}",
            "Content-Type": "application/json",
        }
        async with httpx.AsyncClient(timeout=self._timeout) as client:
            for start in range(0, len(texts), _BATCH_SIZE):
                batch = texts[start:start + _BATCH_SIZE]
                try:
                    response = await client.post(
                        _API_URL, headers=headers, json={"model": self._model, "input": batch})
                    if response.status_code >= 400:
                        raise EmbeddingError(
                            f"Mistral embeddings HTTP {response.status_code} : "
                            f"{response.text.strip()[:300]}")
                    data = response.json()
                except httpx.HTTPError as exc:
                    raise EmbeddingError(f"Erreur Mistral embeddings : {exc}") from exc

                items = data.get("data")
                if not isinstance(items, list) or len(items) != len(batch):
                    raise EmbeddingError("Réponse d'embeddings Mistral inattendue (taille incohérente).")
                for item in items:
                    out.append([float(x) for x in item.get("embedding", [])])
        return out
