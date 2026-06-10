"""Endpoints « outils de table » : tables aléatoires, improvisation, catalogues d'objets."""
import re
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field

from app.api.deps import get_llm_provider
from app.application.llm_json import load_json_object
from app.application.llm_retry import generate_with_retry
from app.domain.ports import LLMProvider, LLMProviderError

router = APIRouter()

_DICE_FORMULA_RE = re.compile(r"^\s*(\d*)\s*[dD]\s*(\d+)\s*$")


def _dice_total_range(formula: str) -> tuple[int, int] | None:
    """(min, max) des totaux possibles d'une formule NdM, ou None si invalide."""
    match = _DICE_FORMULA_RE.match(formula or "")
    if not match:
        return None
    count = int(match.group(1)) if match.group(1) else 1
    faces = int(match.group(2))
    if count < 1 or count > 100 or faces < 2 or faces > 10000:
        return None
    return count, count * faces


class GenerateTableRequestDTO(BaseModel):
    description: str
    dice_formula: str = Field(default="1d20")
    # Contexte libre assemblé par le Core (nom de campagne, système, ambiance…).
    context: str = Field(default="")


class GeneratedTableEntryDTO(BaseModel):
    min_roll: int
    max_roll: int
    label: str
    detail: str = ""


class GenerateTableResponseDTO(BaseModel):
    name: str
    description: str = ""
    entries: list[GeneratedTableEntryDTO]


@router.post("/generate/random-table", response_model=GenerateTableResponseDTO)
async def generate_random_table(
    body: GenerateTableRequestDTO,
    llm: Annotated[LLMProvider, Depends(get_llm_provider)],
) -> GenerateTableResponseDTO:
    """Génère une table aléatoire (entrées par plage) couvrant la formule de dé."""
    rng = _dice_total_range(body.dice_formula)
    if rng is None:
        raise HTTPException(status_code=422, detail="Formule de dé invalide (ex. 1d20, 2d6, d100).")
    lo, hi = rng
    context_block = f"\nContexte de la campagne :\n{body.context.strip()}\n" if body.context.strip() else ""
    prompt = (
        "Tu es un assistant de jeu de rôle. Génère une TABLE ALÉATOIRE évocatrice.\n"
        f"Dé : {body.dice_formula} (résultats possibles de {lo} à {hi}).\n"
        f"Sujet : {body.description.strip()}\n"
        f"{context_block}\n"
        "Règles IMPÉRATIVES :\n"
        "- Réponds UNIQUEMENT par un objet JSON valide, sans texte autour.\n"
        '- Format : {"name": "...", "description": "...", "entries": '
        '[{"min_roll": N, "max_roll": M, "label": "résultat court", "detail": "1-2 phrases"}]}\n'
        f"- Les plages (min_roll..max_roll) doivent COUVRIR EXACTEMENT {lo}..{hi}, "
        "sans trou ni chevauchement, dans l'ordre croissant.\n"
        "- Des résultats variés, cohérents avec le sujet (et le contexte s'il est fourni).\n"
        "- En français. 'label' = résultat bref ; 'detail' = description/effet concret.\n"
        "Renvoie maintenant le JSON."
    )
    try:
        raw = await generate_with_retry(llm, prompt, output_format="json", temperature=0.7)
    except LLMProviderError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc

    parsed, _ = load_json_object(raw)
    if not isinstance(parsed, dict):
        raise HTTPException(status_code=502, detail="Le modèle n'a pas renvoyé de table exploitable.")

    entries: list[GeneratedTableEntryDTO] = []
    for e in parsed.get("entries", []) or []:
        if not isinstance(e, dict):
            continue
        try:
            mn = int(e["min_roll"])
            mx = int(e["max_roll"])
        except (KeyError, TypeError, ValueError):
            continue
        label = str(e.get("label") or "").strip()
        if not label:
            continue
        entries.append(GeneratedTableEntryDTO(
            min_roll=mn, max_roll=max(mn, mx), label=label[:200],
            detail=str(e.get("detail") or "").strip(),
        ))
    if not entries:
        raise HTTPException(status_code=502, detail="Aucune entrée générée — réessaie ou reformule.")

    name = str(parsed.get("name") or body.description).strip()[:120] or "Table générée"
    return GenerateTableResponseDTO(
        name=name,
        description=str(parsed.get("description") or "").strip(),
        entries=entries,
    )


class ImproviseRollRequestDTO(BaseModel):
    table_name: str
    result_label: str
    result_detail: str = Field(default="")
    context: str = Field(default="")


class ImproviseRollResponseDTO(BaseModel):
    narration: str


@router.post("/improvise/table-roll", response_model=ImproviseRollResponseDTO)
async def improvise_table_roll(
    body: ImproviseRollRequestDTO,
    llm: Annotated[LLMProvider, Depends(get_llm_provider)],
) -> ImproviseRollResponseDTO:
    """Brode un court récit (2-3 phrases) sur un résultat tiré, pour lancer la scène."""
    detail = f" ({body.result_detail.strip()})" if body.result_detail.strip() else ""
    context_block = f"\nContexte : {body.context.strip()}" if body.context.strip() else ""
    prompt = (
        "Tu es le Maître du Jeu. Les joueurs viennent de tirer sur la table "
        f"« {body.table_name.strip()} » et ont obtenu : « {body.result_label.strip()} »{detail}."
        f"{context_block}\n\n"
        "Décris en 2-3 phrases vivantes et immédiates ce qui se passe, pour lancer la scène. "
        "Pas de méta, pas d'options : juste la narration, en français."
    )
    try:
        raw = await llm.generate(prompt, temperature=0.8)
    except LLMProviderError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    return ImproviseRollResponseDTO(narration=raw.strip())


# --- Catalogues d'objets (boutiques) : génération IA -------------------------


class GenerateCatalogRequestDTO(BaseModel):
    description: str
    context: str = Field(default="")


class GeneratedCatalogItemDTO(BaseModel):
    name: str
    price: str = ""
    category: str = ""
    description: str = ""


class GenerateCatalogResponseDTO(BaseModel):
    name: str
    description: str = ""
    items: list[GeneratedCatalogItemDTO]


@router.post("/generate/item-catalog", response_model=GenerateCatalogResponseDTO)
async def generate_item_catalog(
    body: GenerateCatalogRequestDTO,
    llm: Annotated[LLMProvider, Depends(get_llm_provider)],
) -> GenerateCatalogResponseDTO:
    """Génère un catalogue d'objets (boutique, butin…) — nom, prix, catégorie, description."""
    context_block = f"\nContexte de la campagne :\n{body.context.strip()}\n" if body.context.strip() else ""
    prompt = (
        "Tu es un assistant de jeu de rôle. Génère un CATALOGUE D'OBJETS (boutique, butin, trésor…).\n"
        f"Sujet : {body.description.strip()}\n"
        f"{context_block}\n"
        "Règles IMPÉRATIVES :\n"
        "- Réponds UNIQUEMENT par un objet JSON valide, sans texte autour.\n"
        '- Format : {"name": "...", "description": "...", "items": '
        '[{"name": "Objet", "price": "ex. 50 po", "category": "ex. Armes", "description": "effet/détails"}]}\n'
        "- Des objets variés et cohérents avec le sujet (et le contexte s'il est fourni).\n"
        "- 'price' = prix court dans la monnaie du jeu ; 'category' = regroupement (Armes, Potions…) ; "
        "'description' = effet/détails en une phrase. En français.\n"
        "Renvoie maintenant le JSON."
    )
    try:
        raw = await generate_with_retry(llm, prompt, output_format="json", temperature=0.7)
    except LLMProviderError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc

    parsed, _ = load_json_object(raw)
    if not isinstance(parsed, dict):
        raise HTTPException(status_code=502, detail="Le modèle n'a pas renvoyé de catalogue exploitable.")

    items: list[GeneratedCatalogItemDTO] = []
    for it in parsed.get("items", []) or []:
        if not isinstance(it, dict):
            continue
        name = str(it.get("name") or "").strip()
        if not name:
            continue
        items.append(GeneratedCatalogItemDTO(
            name=name[:200],
            price=str(it.get("price") or "").strip(),
            category=str(it.get("category") or "").strip(),
            description=str(it.get("description") or "").strip(),
        ))
    if not items:
        raise HTTPException(status_code=502, detail="Aucun objet généré — réessaie ou reformule.")

    name = str(parsed.get("name") or body.description).strip()[:120] or "Catalogue généré"
    return GenerateCatalogResponseDTO(
        name=name,
        description=str(parsed.get("description") or "").strip(),
        items=items,
    )
