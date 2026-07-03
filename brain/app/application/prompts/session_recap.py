"""Prompt du récap « précédemment dans… » (mode séance).

Le Core envoie le journal chronologique de la séance PRÉCÉDENTE ; le modèle rédige un
récapitulatif court, à lire aux joueurs à l'ouverture de la séance suivante. Texte libre
(pas de JSON) : c'est de la narration.
"""
from app.core.language import language_name


def session_recap_prompt(transcript: str, context: str, language: str) -> str:
    context_block = f"\n{context.strip()}\n" if context and context.strip() else ""
    return (
        "Tu es le Maître du Jeu. Voici le journal de la SÉANCE PRÉCÉDENTE de ta table "
        "(entrées chronologiques : notes, évènements, jets de dés, actions des joueurs).\n"
        f"{context_block}\n"
        "Journal :\n"
        f"{transcript.strip()}\n\n"
        "Rédige un récapitulatif « Précédemment… » à LIRE AUX JOUEURS pour ouvrir la "
        "nouvelle séance :\n"
        "- 4 à 8 phrases, ton narratif et vivant, au passé.\n"
        "- Uniquement ce qui s'est réellement passé dans le journal — n'invente RIEN, "
        "ne révèle aucun secret du MJ.\n"
        "- Termine sur la situation où les joueurs se sont arrêtés (le « cliffhanger »).\n"
        f"- Rédige en {language_name(language)}. Pas de préambule ni de méta : juste le récit."
    )
