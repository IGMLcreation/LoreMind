"""Prompt des méthodes guidées (Mentor d'écriture) : à partir de l'idée de départ du MJ,
proposer des réponses aux questions de la méthode (Donjon en 5 salles, Enquête en nœuds,
Plan du méchant…).

Générique : le front décrit la méthode (nom ; principe suivi du rôle de chaque étape, comme
dans l'assistant manuel) et ses questions (clé, libellé,
aide, étape, choix possibles) ; ce module ne fait que formuler le prompt. Les réponses
déjà écrites par le MJ sont fournies pour rester cohérent et ne jamais être contredites.
"""
from app.core.language import language_name


def _field_line(field: dict) -> str:
    key = str(field.get("key") or "").strip()
    label = str(field.get("label") or key).strip()
    hint = str(field.get("hint") or "").strip()
    step = str(field.get("step") or "").strip()
    line = f'- "{key}"'
    if step:
        line += f" [{step}]"
    line += f" : {label}"
    if hint:
        line += f" — {hint}"
    options = field.get("options") or []
    if options:
        values = ", ".join(f'"{o.get("value")}" ({o.get("label")})' for o in options if o.get("value"))
        line += f" — UNE valeur parmi : {values}"
    return line


def guided_answers_prompt(method_name: str, method_principle: str, idea: str, context: str,
                          fields: list[dict], existing: dict[str, str], language: str) -> str:
    """Construit le prompt. `fields` = [{key, label, hint, step, options}] ; `existing` = réponses du MJ."""
    fields_list = "\n".join(_field_line(f) for f in fields or [] if f.get("key"))
    existing_lines = "\n".join(
        f'- "{k}" : {v.strip()}' for k, v in (existing or {}).items() if isinstance(v, str) and v.strip())
    existing_block = (
        "\nCe que le MJ a DÉJÀ écrit (à respecter, ne le contredis jamais) :\n"
        f"{existing_lines}\n" if existing_lines else ""
    )
    context_block = f"\nContexte de la campagne :\n{context.strip()}\n" if context and context.strip() else ""
    guide = method_principle.strip() if method_principle else ""
    guide_block = (
        "\nLa méthode, telle que le MJ la suit à la main (principe, puis rôle de chaque étape) :\n"
        f"{guide}\n" if guide else ""
    )
    return (
        "Tu es un auteur expérimenté de scénarios de jeu de rôle, qui aide un MJ à écrire son aventure "
        f"avec la méthode « {method_name.strip() or 'guidée'} ».\n"
        f"{guide_block}\n"
        f"Idée de départ du MJ : {idea.strip() or '(aucune : propose une aventure originale)'}\n"
        f"{context_block}{existing_block}\n"
        "Questions de la méthode auxquelles tu dois répondre (n'utilise QUE ces clés) :\n"
        f"{fields_list}\n\n"
        "Règles IMPÉRATIVES :\n"
        "- Réponds UNIQUEMENT par un objet JSON valide, sans texte autour.\n"
        '- Format exact : {"answers": {"cle": "réponse", ...}}\n'
        "- N'inclus QUE des clés de la liste. Pour une question à choix, renvoie EXACTEMENT une des "
        "valeurs proposées.\n"
        "- Pars de l'idée du MJ et développe-la : garde son ton, ses lieux, ses personnages ; n'en change "
        "pas le sujet.\n"
        "- Respecte la méthode : chaque réponse remplit le rôle de SON étape, tel que décrit ci-dessus "
        "(ex. une épreuve qui ne se règle pas par un combat, des pistes qui se recoupent). Ne mélange pas "
        "les étapes.\n"
        "- Toutes les réponses forment UNE aventure cohérente, concrète et jouable "
        "(noms, lieux, motivations précises ; pas de généralités).\n"
        "- Sois concis : un nom en quelques mots, une description en 2 à 4 phrases.\n"
        "- Donne toujours aux joueurs de vrais choix et plusieurs façons de progresser.\n"
        f"- Rédige les réponses en {language_name(language)}.\n"
        "Renvoie maintenant le JSON."
    )
