"""Adapter Mistral — implémente les ports LLMProvider / LLMChatProvider.

Mistral (La Plateforme) expose l'API OpenAI standard (POST {base}/chat/completions,
SSE) : client "OpenAI-compatible" qui hérite de BaseOpenAICompatibleAdapter et ne
fournit que ses spécificités.

Tier GRATUIT : compte sur console.mistral.ai (tier « Experiment »), clé API à
coller dans l'écran Paramètres. Modèles conseillés pour l'extraction : un grand
contexte fidèle comme `mistral-large-latest` (128k) ou `mistral-small-latest`.

Mode JSON natif : TOUS les modèles Mistral le supportent (`_supports_json_object`).
"""
from __future__ import annotations

from app.core.config import Settings
from app.domain.ports import LLMProviderError
from app.infrastructure.base_openai_adapter import (
    _FIRST_TOKEN_TIMEOUT_SECONDS,
    BaseOpenAICompatibleAdapter,
)


class MistralLLMProvider(BaseOpenAICompatibleAdapter):
    """Adapter Mistral (OpenAI-compatible) — satisfait LLMProvider et LLMChatProvider."""

    _provider_label = "Mistral"
    _api_url = "https://api.mistral.ai/v1/chat/completions"
    _supports_json_object = True

    def __init__(self, settings: Settings) -> None:
        if not settings.mistral_api_key:
            raise LLMProviderError(
                "Clé API Mistral manquante. Configure-la depuis l'écran Paramètres."
            )
        super().__init__(
            settings.mistral_api_key, settings.mistral_model, settings.llm_timeout_seconds
        )

    def _headers(self) -> dict[str, str]:
        return {**super()._headers(), "Accept": "application/json"}

    def _first_token_timeout_message(self) -> str:
        return (
            f"Erreur Mistral : aucun contenu produit en "
            f"{int(_FIRST_TOKEN_TIMEOUT_SECONDS)}s — le modèle est probablement "
            "en file d'attente (tier gratuit, 2 req/min). Réessayez plus tard ou "
            "choisissez un modèle plus disponible."
        )
