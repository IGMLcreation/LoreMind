"""Endpoints de génération « simple » : prompt libre, page de Lore, auto-titre."""
from typing import Annotated, Literal

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field

from app.api.deps import get_generate_page_use_case, get_llm_provider
from app.application.generate_page import GeneratePageUseCase
from app.core.config import Settings, get_settings
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
        result = await use_case.execute(context)
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


_TITLE_SYSTEM_PROMPT = (
    "Tu generes un titre court (4 a 7 mots max) qui resume le sujet de la "
    "conversation ci-dessous. Reponds UNIQUEMENT par le titre, sans guillemets, "
    "sans ponctuation finale, sans prefixe type 'Titre :'. Le titre doit etre "
    "en francais et capturer le sujet metier (pas 'Conversation IA')."
)


@router.post("/summarize/conversation-title", response_model=SummarizeTitleResponseDTO)
async def summarize_conversation_title(
    body: SummarizeTitleRequestDTO,
    llm: Annotated[LLMProvider, Depends(get_llm_provider)],
) -> SummarizeTitleResponseDTO:
    """Genere un titre court a partir des premiers echanges de la conversation.

    Appele par le core apres le 1er couple user/assistant, pour remplacer le
    titre provisoire "Nouvelle conversation" par quelque chose de parlant.
    """
    if not body.messages:
        raise HTTPException(status_code=422, detail="Au moins un message requis")

    transcript = "\n".join(f"{m.role.upper()}: {m.content}" for m in body.messages[:6])
    prompt = f"{_TITLE_SYSTEM_PROMPT}\n\nConversation :\n{transcript}\n\nTitre :"
    try:
        raw = await llm.generate(prompt)
    except LLMProviderError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc

    title = raw.strip().splitlines()[0].strip().strip('"').strip("'").rstrip(".")
    if len(title) > 80:
        title = title[:80].rstrip()
    if not title:
        title = "Nouvelle conversation"
    return SummarizeTitleResponseDTO(title=title)
