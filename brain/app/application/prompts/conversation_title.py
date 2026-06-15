"""Prompt & repli de l'auto-titre de conversation (cf. router generation.py)."""
from app.core.language import language_name

# Titre de repli (LLM injoignable / réponse vide), localisé selon la langue UI.
TITLE_FALLBACK = {"fr": "Nouvelle conversation", "en": "New conversation"}


def title_system_prompt(language: str) -> str:
    """Consigne d'auto-titre, avec la langue du titre pilotée par l'utilisateur."""
    return (
        "Tu generes un titre court (4 a 7 mots max) qui resume le sujet de la "
        "conversation ci-dessous. Reponds UNIQUEMENT par le titre, sans guillemets, "
        "sans ponctuation finale, sans prefixe type 'Titre :'. Le titre doit etre "
        f"en {language_name(language)} et capturer le sujet metier (pas 'Conversation IA')."
    )
