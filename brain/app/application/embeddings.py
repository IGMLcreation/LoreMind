"""Port d'embeddings (RAG des notebooks).

Abstraction du calcul de vecteurs : un texte → une liste de floats. Les adapters
concrets (Ollama local, Mistral cloud) la satisfont par duck typing, comme pour
les LLMProvider. Le RAG n'en dépend que via cette interface.
"""
from __future__ import annotations

from typing import Protocol


class EmbeddingError(Exception):
    """Échec du calcul d'embeddings (modèle indisponible, réseau, quota…)."""


class EmbeddingProvider(Protocol):
    """Calcule les vecteurs d'une liste de textes (ordre préservé).

    `kind` distingue les DOCUMENTS indexés ("document") de la QUESTION posée
    ("query") : certains modèles (nomic-embed-text) sont entraînés avec des
    préfixes de tâche distincts et perdent en pertinence sans eux. Les adapters
    qui n'en ont pas besoin (mistral-embed) ignorent simplement le paramètre.
    """

    async def embed(self, texts: list[str], kind: str = "document") -> list[list[float]]:
        ...
