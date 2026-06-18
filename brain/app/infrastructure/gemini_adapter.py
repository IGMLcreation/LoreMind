"""Adapter Google Gemini — implémente les ports LLMProvider / LLMChatProvider.

Gemini expose un endpoint COMPATIBLE OpenAI
(POST {base}/openai/chat/completions, SSE) : client "OpenAI-compatible" qui hérite
de BaseOpenAICompatibleAdapter et ne fournit que ses spécificités (dont un message
dédié quand Google refuse la clé en 401/403).

Tier GRATUIT : clé API sur aistudio.google.com (sans CB). Atout majeur pour
l'extraction de PDF : un CONTEXTE de ~1M tokens → un livre entier tient en 1-2
appels. Modèle conseillé : `gemini-2.0-flash` (rapide, gros contexte, fidèle).
"""
from __future__ import annotations

from app.core.config import Settings
from app.domain.ports import LLMProviderError
from app.infrastructure.base_openai_adapter import (
    _FIRST_TOKEN_TIMEOUT_SECONDS,
    BaseOpenAICompatibleAdapter,
)


class GeminiLLMProvider(BaseOpenAICompatibleAdapter):
    """Adapter Gemini (OpenAI-compatible) — satisfait LLMProvider et LLMChatProvider."""

    _provider_label = "Gemini"
    _api_url = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
    _supports_json_object = True

    def __init__(self, settings: Settings) -> None:
        if not settings.gemini_api_key:
            raise LLMProviderError(
                "Clé API Gemini manquante. Configure-la depuis l'écran Paramètres "
                "(clé gratuite sur aistudio.google.com)."
            )
        super().__init__(
            settings.gemini_api_key, settings.gemini_model, settings.llm_timeout_seconds
        )

    def _headers(self) -> dict[str, str]:
        return {**super()._headers(), "Accept": "application/json"}

    def _first_token_timeout_message(self) -> str:
        return (
            f"Erreur Gemini : aucun contenu produit en "
            f"{int(_FIRST_TOKEN_TIMEOUT_SECONDS)}s. Réessayez ou vérifiez "
            "votre quota gratuit."
        )

    def _generation_timeout_message(self) -> str:
        return (
            f"Erreur Gemini : génération non terminée en {self._timeout}s. Réduisez la "
            "taille des morceaux d'import ou augmentez le timeout."
        )

    def _error_for_status(self, status_code: int, detail: str) -> LLMProviderError:
        # 401/403 = clé rejetée par GOOGLE (pas un problème LoreMind) : message
        # actionnable plutôt que le JSON brut de l'API.
        if status_code in (401, 403):
            return LLMProviderError(
                "Erreur Gemini : clé API refusée par Google "
                f"(HTTP {status_code}). Vérifiez que la clé vient bien "
                "de aistudio.google.com (« Get API key ») et qu'elle n'a pas de "
                "restrictions (API ou adresse IP) dans la Google Cloud Console. "
                f"Détail : {detail[:300]}"
            )
        return super()._error_for_status(status_code, detail)
