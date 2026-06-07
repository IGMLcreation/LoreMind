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
    """Calcule les vecteurs d'une liste de textes (ordre préservé)."""

    async def embed(self, texts: list[str]) -> list[list[float]]:
        ...
