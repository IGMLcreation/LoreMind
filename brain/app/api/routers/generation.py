"""Endpoints de génération « simple » : prompt libre, page de Lore, auto-titre."""
from typing import Annotated, Literal

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field

from app.api.deps import get_generate_page_use_case, get_llm_provider
from app.application.generate_page import GeneratePageUseCase
from app.application.llm_json import load_json_object
from app.application.llm_retry import generate_with_retry
from app.application.prompts import conversation_title as title_prompts
from app.application.prompts import narrative_fields as narrative_fields_prompts
from app.application.prompts import scene_drafts as scene_drafts_prompts
from app.application.prompts import session_recap as session_recap_prompts
from app.core.config import Settings, get_settings
from app.core.language import get_user_language
from app.domain.models import PageGenerationContext
from app.domain.ports import LLMProvider, LLMProviderError

router = APIRouter()


class GenerateRequest(BaseModel):
    prompt: str


class GenerateResponse(BaseModel):
    model: str
    response: str


@router.post("/generate", response_model=GenerateResponse)
async def generate(
    body: GenerateRequest,
    settings: Annotated[Settings, Depends(get_settings)],
    llm: Annotated[LLMProvider, Depends(get_llm_provider)],
) -> GenerateResponse:
    """Endpoint libre : prompt → texte brut. Utile pour debug et exploration."""
    try:
        text = await llm.generate(body.prompt)
    except LLMProviderError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc

    return GenerateResponse(model=settings.llm_model, response=text)


class GeneratePageRequestDTO(BaseModel):
    """Contexte envoyé par le Core Java pour remplir une page via le LLM."""

    lore_name: str
    folder_name: str
    template_name: str
    template_fields: list[str] = Field(min_length=1)
    page_title: str
    lore_description: str | None = None


class GeneratePageResponseDTO(BaseModel):
    """Retour : une valeur textuelle par champ du template (clé = field name)."""

    values: dict[str, str]


@router.post("/generate-page", response_model=GeneratePageResponseDTO)
async def generate_page(
    body: GeneratePageRequestDTO,
    use_case: Annotated[
        GeneratePageUseCase, Depends(get_generate_page_use_case)
    ],
    language: Annotated[str, Depends(get_user_language)],
) -> GeneratePageResponseDTO:
    """Endpoint métier : contexte LoreMind → valeurs structurées par champ.

    Branche tout le use case `GeneratePageUseCase`. Ce controller ne fait
    que le mapping DTO ↔ dataclass et la traduction d'erreur domaine → HTTP.
    """
    context = PageGenerationContext(
        lore_name=body.lore_name,
        lore_description=body.lore_description,
        folder_name=body.folder_name,
        template_name=body.template_name,
        template_fields=body.template_fields,
        page_title=body.page_title,
    )

    try:
        result = await use_case.execute(context, language=language)
    except LLMProviderError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc

    return GeneratePageResponseDTO(values=result.values)


# --- Auto-titre d'une conversation persistee --------------------------------


class SummarizeTitleMessageDTO(BaseModel):
    role: Literal["user", "assistant", "system"]
    content: str


class SummarizeTitleRequestDTO(BaseModel):
    """Premiers messages d'une conversation pour auto-generer un titre court."""

    messages: list[SummarizeTitleMessageDTO] = Field(default_factory=list)


class SummarizeTitleResponseDTO(BaseModel):
    title: str


@router.post("/summarize/conversation-title", response_model=SummarizeTitleResponseDTO)
async def summarize_conversation_title(
    body: SummarizeTitleRequestDTO,
    llm: Annotated[LLMProvider, Depends(get_llm_provider)],
    language: Annotated[str, Depends(get_user_language)],
) -> SummarizeTitleResponseDTO:
    """Genere un titre court a partir des premiers echanges de la conversation.

    Appele par le core apres le 1er couple user/assistant, pour remplacer le
    titre provisoire "Nouvelle conversation" par quelque chose de parlant.
    """
    if not body.messages:
        raise HTTPException(status_code=422, detail="Au moins un message requis")

    transcript = "\n".join(f"{m.role.upper()}: {m.content}" for m in body.messages[:6])
    prompt = f"{title_prompts.title_system_prompt(language)}\n\nConversation :\n{transcript}\n\nTitre :"
    try:
        raw = await llm.generate(prompt)
    except LLMProviderError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc

    title = raw.strip().splitlines()[0].strip().strip('"').strip("'").rstrip(".")
    if len(title) > 80:
        title = title[:80].rstrip()
    if not title:
        title = title_prompts.TITLE_FALLBACK.get(language, title_prompts.TITLE_FALLBACK["fr"])
    return SummarizeTitleResponseDTO(title=title)


# --- Étoffer une entité narrative (Pilier A : co-MJ propose → l'humain valide) ----------


class NarrativeFieldSpecDTO(BaseModel):
    """Un champ autorisé : clé technique + libellé lisible (fourni par le Core)."""

    key: str
    label: str = Field(default="")


class NarrativeFieldsRequestDTO(BaseModel):
    """Contexte envoyé par le Core pour proposer des valeurs de champs (arc/chapitre/scène)."""

    entity_type: str = Field(default="")
    context: str = Field(default="")
    instruction: str = Field(default="")
    # Whitelist (clé + libellé) fournie par le Core, source de vérité.
    fields: list[NarrativeFieldSpecDTO] = Field(default_factory=list)


class NarrativeFieldsResponseDTO(BaseModel):
    """Retour : une valeur proposée par clé (uniquement des clés autorisées, non vides)."""

    fields: dict[str, str]


@router.post("/generate/narrative-fields", response_model=NarrativeFieldsResponseDTO)
async def generate_narrative_fields(
    body: NarrativeFieldsRequestDTO,
    llm: Annotated[LLMProvider, Depends(get_llm_provider)],
    language: Annotated[str, Depends(get_user_language)],
) -> NarrativeFieldsResponseDTO:
    """Propose des valeurs pour ÉTOFFER une entité narrative (patch champ par champ, non appliqué).

    Whitelist stricte : on ne retient que les clés autorisées, non vides. Un objet vide
    est une réponse VALIDE (le modèle n'a rien de pertinent à proposer — l'entité est
    peut-être déjà complète) ; seule une sortie non-JSON est une erreur.
    """
    allowed = {f.key for f in body.fields if f.key}
    prompt = narrative_fields_prompts.narrative_fields_prompt(
        body.entity_type, body.context, body.instruction,
        [{"key": f.key, "label": f.label} for f in body.fields], language)
    try:
        raw = await generate_with_retry(llm, prompt, output_format="json", temperature=0.7)
    except LLMProviderError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc

    parsed, _ = load_json_object(raw)
    if not isinstance(parsed, dict):
        raise HTTPException(status_code=502, detail="Le modèle n'a pas renvoyé de champs exploitables.")

    out: dict[str, str] = {}
    raw_fields = parsed.get("fields")
    if isinstance(raw_fields, dict):
        for key, value in raw_fields.items():
            if key not in allowed:
                continue
            if not isinstance(value, (str, int, float)):
                continue
            text = str(value).strip()
            if text:
                out[str(key)] = text
    return NarrativeFieldsResponseDTO(fields=out)


# --- Peupler un chapitre en scènes (Pilier A : capacité « create ») ----------


class SceneDraftsRequestDTO(BaseModel):
    """Contexte envoyé par le Core pour ébaucher des scènes d'un chapitre."""

    context: str = Field(default="")
    instruction: str = Field(default="")
    count: int = Field(default=4)


class SceneDraftDTO(BaseModel):
    name: str
    description: str = Field(default="")
    playerNarration: str = Field(default="")


class SceneDraftsResponseDTO(BaseModel):
    scenes: list[SceneDraftDTO]


@router.post("/generate/scene-drafts", response_model=SceneDraftsResponseDTO)
async def generate_scene_drafts(
    body: SceneDraftsRequestDTO,
    llm: Annotated[LLMProvider, Depends(get_llm_provider)],
    language: Annotated[str, Depends(get_user_language)],
) -> SceneDraftsResponseDTO:
    """Propose des ébauches de scènes pour un chapitre (non créées). Un titre par scène
    est obligatoire ; on borne le nombre. Seule une sortie non-JSON est une erreur."""
    n = max(1, min(8, body.count))
    prompt = scene_drafts_prompts.scene_drafts_prompt(body.context, body.instruction, n, language)
    try:
        raw = await generate_with_retry(llm, prompt, output_format="json", temperature=0.8)
    except LLMProviderError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc

    parsed, _ = load_json_object(raw)
    if not isinstance(parsed, dict):
        raise HTTPException(status_code=502, detail="Le modèle n'a pas renvoyé de scènes exploitables.")

    scenes: list[SceneDraftDTO] = []
    for s in (parsed.get("scenes") or [])[:n]:
        if not isinstance(s, dict):
            continue
        name = str(s.get("name") or "").strip()
        if not name:
            continue
        scenes.append(SceneDraftDTO(
            name=name[:200],
            description=str(s.get("description") or "").strip(),
            playerNarration=str(s.get("playerNarration") or "").strip(),
        ))
    return SceneDraftsResponseDTO(scenes=scenes)


# --- Récap « précédemment… » d'une séance (mode cockpit) ---------------------


class SessionRecapRequestDTO(BaseModel):
    """Journal chronologique de la séance précédente + méta courte."""

    transcript: str
    context: str = Field(default="")


class SessionRecapResponseDTO(BaseModel):
    recap: str


@router.post("/generate/session-recap", response_model=SessionRecapResponseDTO)
async def generate_session_recap(
    body: SessionRecapRequestDTO,
    llm: Annotated[LLMProvider, Depends(get_llm_provider)],
    language: Annotated[str, Depends(get_user_language)],
) -> SessionRecapResponseDTO:
    """Rédige le récap « Précédemment… » à lire aux joueurs (texte libre, pas de JSON)."""
    if not body.transcript.strip():
        raise HTTPException(status_code=422, detail="Journal vide : rien à résumer.")
    prompt = session_recap_prompts.session_recap_prompt(body.transcript, body.context, language)
    try:
        raw = await generate_with_retry(llm, prompt, temperature=0.7)
    except LLMProviderError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    recap = raw.strip()
    if not recap:
        raise HTTPException(status_code=502, detail="Le modèle n'a renvoyé aucun récit.")
    return SessionRecapResponseDTO(recap=recap)
