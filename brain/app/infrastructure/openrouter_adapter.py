"""Adapter OpenRouter — implémente les ports LLMProvider / LLMChatProvider.

OpenRouter expose l'API OpenAI standard (POST {base}/chat/completions, SSE), donc
cet adapter est un client "OpenAI-compatible" : il hérite de toute la mécanique de
BaseOpenAICompatibleAdapter et ne fournit que ses spécificités (URL, en-têtes
d'attribution, messages, lecture de config).

Modèles GRATUITS : utiliser un id finissant par `:free` (ex.
`meta-llama/llama-3.3-70b-instruct:free`) ou le routeur `openrouter/free` (défaut)
qui choisit automatiquement un modèle gratuit — aucun crédit consommé.

NB : on n'impose PAS `response_format=json_object` (`_supports_json_object=False`).
Beaucoup de modèles/providers GRATUITS ne le supportent pas et renvoient une
réponse VIDE. On laisse le modèle répondre librement ; l'extraction JSON en aval
(load_json_object + nettoyage du raisonnement) récupère le JSON dans la prose.
"""
from __future__ import annotations

from app.core.config import Settings
from app.domain.ports import LLMProviderError
from app.infrastructure.base_openai_adapter import (
    _FIRST_TOKEN_TIMEOUT_SECONDS,
    BaseOpenAICompatibleAdapter,
)


class OpenRouterLLMProvider(BaseOpenAICompatibleAdapter):
    """Adapter OpenRouter (OpenAI-compatible) — satisfait LLMProvider et LLMChatProvider."""

    _provider_label = "OpenRouter"
    _api_url = "https://openrouter.ai/api/v1/chat/completions"
    _supports_json_object = False

    def __init__(self, settings: Settings) -> None:
        if not settings.openrouter_api_key:
            raise LLMProviderError(
                "Clé API OpenRouter manquante. Configure-la depuis l'écran Paramètres."
            )
        super().__init__(
            settings.openrouter_api_key, settings.openrouter_model, settings.llm_timeout_seconds
        )

    def _headers(self) -> dict[str, str]:
        return {
            **super()._headers(),
            # Attribution facultative (classement OpenRouter) — sans impact fonctionnel.
            "HTTP-Referer": "https://loremind.app",
            "X-Title": "LoreMind",
        }

    def _first_token_timeout_message(self) -> str:
        return (
            f"Erreur OpenRouter : aucun contenu produit en "
            f"{int(_FIRST_TOKEN_TIMEOUT_SECONDS)}s — le modèle gratuit est "
            "probablement en file d'attente / saturé. Réessayez plus tard ou "
            "choisissez un autre modèle (1min.ai, ou payant)."
        )
