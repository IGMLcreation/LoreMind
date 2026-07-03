"""Prompt d'ébauche de scènes pour un chapitre (Pilier A — capacité « create »).

Le co-MJ propose plusieurs scènes cohérentes pour PEUPLER un chapitre vide (ou en manque).
JSON structuré, une liste de scènes ; l'humain révise et ne crée que celles qu'il retient.
"""
from app.core.language import language_name


def scene_drafts_prompt(context: str, instruction: str, count: int, language: str) -> str:
    instruction_block = (
        f"\nConsigne particulière du MJ : {instruction.strip()}\n"
        if instruction and instruction.strip() else ""
    )
    return (
        f"Tu es un co-Maître de Jeu. Propose {count} SCÈNES de jeu de rôle pour PEUPLER ce "
        "chapitre, cohérentes entre elles et avec le contexte.\n\n"
        f"{context.strip()}\n"
        f"{instruction_block}\n"
        "Règles IMPÉRATIVES :\n"
        "- Réponds UNIQUEMENT par un objet JSON valide, sans texte autour.\n"
        '- Format exact : {"scenes": [{"name": "...", "description": "...", "playerNarration": "..."}]}\n'
        f"- Propose AU PLUS {count} scènes, distinctes et complémentaires (une progression du chapitre).\n"
        "- 'name' : titre court et évocateur (OBLIGATOIRE).\n"
        "- 'description' : un résumé bref (une phrase).\n"
        "- 'playerNarration' : 2-3 phrases de mise en scène lues aux joueurs.\n"
        "- Ne DUPLIQUE pas les scènes déjà présentes ; reste cohérent avec le chapitre et la campagne "
        "(n'invente pas d'élément qui les contredit).\n"
        f"- Rédige en {language_name(language)}.\n"
        "Renvoie maintenant le JSON."
    )
