"""Prompts des outils de table (tables aléatoires, improvisation, catalogues).

Ces prompts étaient auparavant construits en ligne dans le router `tables.py` ;
isolés ici pour garder la frontière HTTP fine. Le router calcule les plages de
dés et passe les champs bruts ; ces fonctions façonnent le texte.
"""
from app.core.language import language_name


def random_table_prompt(description: str, dice_formula: str, lo: int, hi: int,
                        context: str, language: str) -> str:
    """Prompt de génération d'une table aléatoire couvrant lo..hi."""
    context_block = f"\nContexte de la campagne :\n{context.strip()}\n" if context.strip() else ""
    return (
        "Tu es un assistant de jeu de rôle. Génère une TABLE ALÉATOIRE évocatrice.\n"
        f"Dé : {dice_formula} (résultats possibles de {lo} à {hi}).\n"
        f"Sujet : {description.strip()}\n"
        f"{context_block}\n"
        "Règles IMPÉRATIVES :\n"
        "- Réponds UNIQUEMENT par un objet JSON valide, sans texte autour.\n"
        '- Format : {"name": "...", "description": "...", "entries": '
        '[{"min_roll": N, "max_roll": M, "label": "résultat court", "detail": "1-2 phrases"}]}\n'
        f"- Les plages (min_roll..max_roll) doivent COUVRIR EXACTEMENT {lo}..{hi}, "
        "sans trou ni chevauchement, dans l'ordre croissant.\n"
        "- Des résultats variés, cohérents avec le sujet (et le contexte s'il est fourni).\n"
        f"- En {language_name(language)}. 'label' = résultat bref ; 'detail' = description/effet concret.\n"
        "Renvoie maintenant le JSON."
    )


def improvise_roll_prompt(table_name: str, result_label: str, result_detail: str,
                         context: str, language: str) -> str:
    """Prompt de narration brodée sur un résultat tiré."""
    detail = f" ({result_detail.strip()})" if result_detail.strip() else ""
    context_block = f"\nContexte : {context.strip()}" if context.strip() else ""
    return (
        "Tu es le Maître du Jeu. Les joueurs viennent de tirer sur la table "
        f"« {table_name.strip()} » et ont obtenu : « {result_label.strip()} »{detail}."
        f"{context_block}\n\n"
        "Décris en 2-3 phrases vivantes et immédiates ce qui se passe, pour lancer la scène. "
        f"Pas de méta, pas d'options : juste la narration, en {language_name(language)}."
    )


def item_catalog_prompt(description: str, context: str, language: str) -> str:
    """Prompt de génération d'un catalogue d'objets (boutique, butin…)."""
    context_block = f"\nContexte de la campagne :\n{context.strip()}\n" if context.strip() else ""
    return (
        "Tu es un assistant de jeu de rôle. Génère un CATALOGUE D'OBJETS (boutique, butin, trésor…).\n"
        f"Sujet : {description.strip()}\n"
        f"{context_block}\n"
        "Règles IMPÉRATIVES :\n"
        "- Réponds UNIQUEMENT par un objet JSON valide, sans texte autour.\n"
        '- Format : {"name": "...", "description": "...", "items": '
        '[{"name": "Objet", "price": "ex. 50 po", "category": "ex. Armes", "description": "effet/détails"}]}\n'
        "- Des objets variés et cohérents avec le sujet (et le contexte s'il est fourni).\n"
        "- 'price' = prix court dans la monnaie du jeu ; 'category' = regroupement (Armes, Potions…) ; "
        f"'description' = effet/détails en une phrase. En {language_name(language)}.\n"
        "Renvoie maintenant le JSON."
    )
