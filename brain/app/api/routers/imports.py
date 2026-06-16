"""Endpoints d'import/adaptation de PDF (règles, campagne) — REST + flux SSE."""
import json
import logging
from typing import Annotated, AsyncIterator

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

from app.api.common import MAX_PDF_BYTES, pdf_upload_error, sse_event
from app.api.deps import (
    get_adapt_campaign_use_case,
    get_import_campaign_use_case,
    get_import_rules_use_case,
)
from app.application.adapt_campaign import AdaptCampaignUseCase
from app.application.import_campaign import ImportCampaignUseCase
from app.application.import_rules import ImportRulesUseCase
from app.core.language import get_user_language
from app.domain.models import ChatMessage
from app.domain.ports import LLMProviderError, PdfExtractionError

logger = logging.getLogger(__name__)

router = APIRouter()


class RulesImportResponseDTO(BaseModel):
    """Proposition de sections de règles extraites d'un PDF.

    `sections` = {titre → contenu markdown}. C'est une PROPOSITION : le Core
    et l'UI laissent l'utilisateur réviser/éditer avant toute persistance.
    `ocr_page_count` permet d'indiquer si le PDF était un scan (OCR utilisé).
    """

    sections: dict[str, str]
    page_count: int
    ocr_page_count: int


@router.post("/import/rules", response_model=RulesImportResponseDTO)
async def import_rules(
    use_case: Annotated[ImportRulesUseCase, Depends(get_import_rules_use_case)],
    language: Annotated[str, Depends(get_user_language)],
    file: UploadFile = File(...),
) -> RulesImportResponseDTO:
    """Import d'un PDF de règles → sections markdown structurées (proposition).

    Extrait le texte (couche texte + repli OCR par page pour les scans), découpe,
    et demande au LLM de répartir les règles en sections thématiques. Ne persiste
    rien : renvoie la proposition au Core, qui la présente pour révision.
    """
    content = await file.read()
    if not content:
        raise HTTPException(status_code=422, detail="Fichier PDF vide.")
    if len(content) > MAX_PDF_BYTES:
        raise HTTPException(
            status_code=413,
            detail=f"PDF trop volumineux (> {MAX_PDF_BYTES // (1024 * 1024)} Mo).",
        )

    try:
        result = await use_case.execute(content, language=language)
    except PdfExtractionError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except LLMProviderError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc

    return RulesImportResponseDTO(
        sections=result.sections,
        page_count=result.page_count,
        ocr_page_count=result.ocr_page_count,
    )


@router.post("/import/rules/stream")
async def import_rules_stream(
    use_case: Annotated[ImportRulesUseCase, Depends(get_import_rules_use_case)],
    language: Annotated[str, Depends(get_user_language)],
    file: UploadFile = File(...),
) -> StreamingResponse:
    """Import streamé : émet l'avancement (SSE) puis le résultat final.

    Évènements SSE :
      - `event: extracting`  → data: {}                    (extraction en cours)
      - `event: start`       → data: {page_count, ocr_page_count, total}
      - `event: progress`    → data: {current, total, new_sections:[...]}
      - `event: done`        → data: {sections, page_count, ocr_page_count}
      - `event: error`       → data: {message}
    """
    content = await file.read()

    async def event_stream() -> AsyncIterator[str]:
        upload_error = pdf_upload_error(content)
        if upload_error:
            yield sse_event("error", {"message": upload_error})
            return
        try:
            async for ev in use_case.stream(content, language=language):
                event_type = ev.pop("type")
                yield sse_event(event_type, ev)
        except PdfExtractionError as exc:
            yield sse_event("error", {"message": str(exc)})
        except LLMProviderError as exc:
            yield sse_event("error", {"message": str(exc)})
        except Exception as exc:  # noqa: BLE001 — filet : une erreur inattendue ne doit
            # PAS casser le flux SSE brutalement (sinon le Core n'a qu'un message générique
            # sans détail). On la transforme en évènement `error` propre + log avec trace.
            logger.exception("Import règles : erreur inattendue dans le flux.")
            yield sse_event("error", {"message": f"Erreur inattendue du Brain : {type(exc).__name__} : {exc}"})

    return StreamingResponse(event_stream(), media_type="text/event-stream")


@router.post("/import/campaign/stream")
async def import_campaign_stream(
    use_case: Annotated[ImportCampaignUseCase, Depends(get_import_campaign_use_case)],
    file: UploadFile = File(...),
) -> StreamingResponse:
    """Import streamé d'un PDF de campagne → arbre arc→chapitre→scène (SSE).

    Évènements : `extracting`, `start` {page_count, ocr_page_count, total},
    `progress` {current, total, arc_count, chapter_count, scene_count},
    `done` {arcs:[...], page_count, ocr_page_count}, `error` {message}.
    """
    content = await file.read()

    async def event_stream() -> AsyncIterator[str]:
        upload_error = pdf_upload_error(content)
        if upload_error:
            yield sse_event("error", {"message": upload_error})
            return
        try:
            async for ev in use_case.stream(content):
                event_type = ev.pop("type")
                yield sse_event(event_type, ev)
        except PdfExtractionError as exc:
            yield sse_event("error", {"message": str(exc)})
        except LLMProviderError as exc:
            yield sse_event("error", {"message": str(exc)})
        except Exception as exc:  # noqa: BLE001 — voir import règles : on ne laisse pas
            # une erreur inattendue casser le flux sans détail.
            logger.exception("Import campagne : erreur inattendue dans le flux.")
            yield sse_event("error", {"message": f"Erreur inattendue du Brain : {type(exc).__name__} : {exc}"})

    return StreamingResponse(event_stream(), media_type="text/event-stream")


@router.post("/adapt/campaign/stream")
async def adapt_campaign_stream(
    use_case: Annotated[AdaptCampaignUseCase, Depends(get_adapt_campaign_use_case)],
    language: Annotated[str, Depends(get_user_language)],
    file: UploadFile = File(...),
    brief: str = Form(""),
    messages: str = Form("[]"),
) -> StreamingResponse:
    """Adaptation CONVERSATIONNELLE d'un PDF à une campagne (SSE markdown).

    `brief` = description de la campagne (Core). `messages` = JSON de l'échange
    ([{role, content}, …]) ; vide au 1er tour. Évènements : `token`, `done`, `error`.
    """
    content = await file.read()

    try:
        raw_messages = json.loads(messages) if messages else []
    except json.JSONDecodeError:
        raw_messages = []
    convo = [
        ChatMessage(role=str(m.get("role", "user")), content=str(m.get("content", "")))
        for m in raw_messages
        if isinstance(m, dict) and str(m.get("content", "")).strip()
    ]

    async def event_stream() -> AsyncIterator[str]:
        upload_error = pdf_upload_error(content)
        if upload_error:
            yield sse_event("error", {"message": upload_error})
            return
        try:
            async for token in use_case.stream(content, brief, convo, language=language):
                yield sse_event("token", {"token": token})
            yield sse_event("done", {})
        except PdfExtractionError as exc:
            yield sse_event("error", {"message": str(exc)})
        except LLMProviderError as exc:
            yield sse_event("error", {"message": str(exc)})

    return StreamingResponse(event_stream(), media_type="text/event-stream")
