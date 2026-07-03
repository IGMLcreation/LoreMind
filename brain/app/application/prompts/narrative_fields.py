"""Prompt d'étoffage des champs d'une entité narrative (arc / chapitre / scène) — Pilier A.

Générique : le Core (Java) est la SOURCE DE VÉRITÉ des champs (clé + libellé) et les
passe en entrée ; ce module ne fait que formuler le prompt. Le modèle ne renvoie que les
clés fournies et OMET celles pour lesquelles il n'a rien de pertinent (pas de remplissage forcé).
"""
from app.core.language import language_name

# Étiquette lisible du type d'entité, pour la formulation du prompt.
ENTITY_LABEL: dict[str, str] = {
    "arc": "cet arc narratif",
    "chapter": "ce chapitre",
    "scene": "cette scène",
}


def narrative_fields_prompt(entity_type: str, entity_context: str, instruction: str,
                            fields: list[dict], language: str) -> str:
    """Construit le prompt d'étoffage. `fields` = [{key, label}] (whitelist du Core)."""
    label = ENTITY_LABEL.get(entity_type or "", "cette entité narrative")
    lines = []
    for f in fields or []:
        key = str(f.get("key") or "").strip()
        if not key:
            continue
        flabel = str(f.get("label") or key).strip()
        lines.append(f'- "{key}" : {flabel}')
    fields_list = "\n".join(lines)
    instruction_block = (
        f"\nConsigne particulière du MJ : {instruction.strip()}\n"
        if instruction and instruction.strip() else ""
    )
    return (
        f"Tu es un co-Maître de Jeu. On te donne l'état ACTUEL d'{label} de jeu de rôle. "
        "Propose des valeurs pour l'ÉTOFFER, cohérentes avec ce qui existe déjà.\n\n"
        f"{entity_context.strip()}\n"
        f"{instruction_block}\n"
        "Champs que tu peux remplir (n'utilise QUE ces clés) :\n"
        f"{fields_list}\n\n"
        "Règles IMPÉRATIVES :\n"
        "- Réponds UNIQUEMENT par un objet JSON valide, sans texte autour.\n"
        '- Format exact : {"fields": {"cle": "valeur proposée", ...}}\n'
        "- N'inclus QUE des clés de la liste ci-dessus. N'invente AUCUNE autre clé.\n"
        "- Si un champ est déjà bien rempli ou si tu n'as rien de pertinent, OMETS-le "
        "(ne le renvoie pas) plutôt que de le remplir de force.\n"
        "- Reste cohérent avec le contexte : n'invente pas d'élément qui contredit "
        "l'entité ou la campagne.\n"
        f"- Rédige les valeurs en {language_name(language)}.\n"
        "Renvoie maintenant le JSON."
    )
