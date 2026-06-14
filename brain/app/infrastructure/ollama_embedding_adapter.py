"""Adapter d'embeddings Ollama (local) — endpoint /api/embed.

Gratuit et illimité (tourne sur la machine). Nécessite d'avoir pullé le modèle
d'embedding (ex. `ollama pull nomic-embed-text`).
"""
from __future__ import annotations

import httpx

from app.application.embeddings import EmbeddingError
from app.core.config import Settings


# Préfixes de tâche des modèles nomic-embed : le modèle est ENTRAÎNÉ avec
# (search_document pour le corpus, search_query pour la question). Sans eux,
# la pertinence du retrieval est mesurablement dégradée. Ne s'applique qu'aux
# modèles nomic — les autres (mxbai, bge…) ont leurs propres conventions ou
# aucune ; on reste neutre pour eux.
_NOMIC_PREFIXES = {"document": "search_document: ", "query": "search_query: "}


class OllamaEmbeddingProvider:
    """Implémente EmbeddingProvider via Ollama /api/embed (batch)."""

    def __init__(self, settings: Settings) -> None:
        self._base_url = settings.ollama_base_url
        self._model = settings.ollama_embedding_model
        self._timeout = settings.llm_timeout_seconds

    def _prepare(self, texts: list[str], kind: str) -> list[str]:
        """Applique le préfixe de tâche si le modèle est de la famille nomic-embed.

        NB : les sources indexées AVANT l'introduction des préfixes doivent être
        ré-uploadées pour que documents et questions vivent dans le même espace.
        """
        if "nomic-embed" not in self._model:
            return texts
        prefix = _NOMIC_PREFIXES.get(kind, _NOMIC_PREFIXES["document"])
        return [prefix + t for t in texts]

    async def embed(self, texts: list[str], kind: str = "document") -> list[list[float]]:
        if not texts:
            return []
        url = f"{self._base_url}/api/embed"
        payload = {"model": self._model, "input": self._prepare(texts, kind)}
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
