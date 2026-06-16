"""Endpoints des notebooks (atelier RAG) : indexation des sources + chats ancrés."""
import logging
from typing import Annotated, AsyncIterator

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from app.api.common import MAX_PDF_BYTES, sse_event
from app.api.deps import (
    get_notebook_chat_use_case,
    get_notebook_deep_use_case,
    get_notebook_rag_use_case,
)
from app.application.embeddings import EmbeddingError
from app.application.notebook_chat import NotebookChatUseCase
from app.application.notebook_deep import NotebookDeepUseCase
from app.application.notebook_rag import NotebookRagUseCase
from app.core.config import Settings, get_settings
from app.core.language import get_user_language
from app.domain.models import ChatMessage
from app.domain.ports import LLMProviderError, PdfExtractionError
from app.infrastructure import vector_store

logger = logging.getLogger(__name__)

router = APIRouter()


class IndexSourceResponseDTO(BaseModel):
    chunks: int
    page_count: int
    ocr_page_count: int


@router.post("/index/notebook-source", response_model=IndexSourceResponseDTO)
async def index_notebook_source(
    rag: Annotated[NotebookRagUseCase, Depends(get_notebook_rag_use_case)],
    source_id: str = Form(...),
    file: UploadFile = File(...),
) -> IndexSourceResponseDTO:
    """Indexe une source PDF (extraction + embeddings + stockage vectoriel)."""
    content = await file.read()
    if not content:
        raise HTTPException(status_code=422, detail="Fichier PDF vide.")
    if len(content) > MAX_PDF_BYTES:
        raise HTTPException(
            status_code=413, detail=f"PDF trop volumineux (> {MAX_PDF_BYTES // (1024 * 1024)} Mo).")
    try:
        recap = await rag.index_source(source_id, content)
    except PdfExtractionError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    except EmbeddingError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    return IndexSourceResponseDTO(**recap)


@router.delete("/index/notebook-source/{source_id}")
def delete_notebook_source(source_id: str) -> dict[str, str]:
    """Supprime les vecteurs d'une source (au DELETE d'une source/notebook)."""
    vector_store.delete(source_id)
    return {"status": "deleted", "source_id": source_id}


class NotebookChatMessageDTO(BaseModel):
    role: str
    content: str


class NotebookChatRequestDTO(BaseModel):
    source_ids: list[str] = Field(default_factory=list)
    messages: list[NotebookChatMessageDTO] = Field(default_factory=list)
    context: str = Field(default="")


@router.post("/chat/notebook/stream")
async def chat_notebook_stream(
    body: NotebookChatRequestDTO,
    use_case: Annotated[NotebookChatUseCase, Depends(get_notebook_chat_use_case)],
    settings: Annotated[Settings, Depends(get_settings)],
    language: Annotated[str, Depends(get_user_language)],
) -> StreamingResponse:
    """Chat ANCRÉ sur les sources (RAG) : récupère les passages pertinents puis
    streame la réponse. Évènements SSE : `token` {token}, `done` {}, `error` {message}."""
    messages = [ChatMessage(role=m.role, content=m.content) for m in body.messages]
    top_k = max(1, min(settings.rag_top_k, 200))

    async def event_stream() -> AsyncIterator[str]:
        try:
            async for ev in use_case.stream(body.source_ids, messages, context=body.context, top_k=top_k, language=language):
                if ev["type"] == "token":
                    if ev.get("token"):
                        yield sse_event("token", {"token": ev["token"]})
                else:
                    # 'sources' (et tout futur évènement typé) : relayé tel quel.
                    ev_type = ev.pop("type")
                    yield sse_event(ev_type, ev)
            yield sse_event("done", {})
        except (LLMProviderError, EmbeddingError) as exc:
            yield sse_event("error", {"message": str(exc)})
        except Exception as exc:  # noqa: BLE001 — filet : pas de coupure brutale du flux.
            logger.exception("Chat notebook : erreur inattendue.")
            yield sse_event("error", {"message": f"Erreur inattendue du Brain : {type(exc).__name__} : {exc}"})

    return StreamingResponse(event_stream(), media_type="text/event-stream")


@router.post("/chat/notebook/deep/stream")
async def chat_notebook_deep_stream(
    body: NotebookChatRequestDTO,
    use_case: Annotated[NotebookDeepUseCase, Depends(get_notebook_deep_use_case)],
    language: Annotated[str, Depends(get_user_language)],
) -> StreamingResponse:
    """Analyse APPROFONDIE (map-reduce sur tout le document). Évènements SSE :
    `progress` {current,total} pendant la lecture, puis `token` {token}, puis `done`."""
    messages = [ChatMessage(role=m.role, content=m.content) for m in body.messages]
    question = next((m.content for m in reversed(messages) if m.role == "user"), "")

    async def event_stream() -> AsyncIterator[str]:
        if not question.strip():
            yield sse_event("error", {"message": "Question vide."})
            return
        try:
            async for ev in use_case.stream(body.source_ids, messages, context=body.context, language=language):
                ev_type = ev.pop("type")
                yield sse_event(ev_type, ev)
        except (LLMProviderError, EmbeddingError) as exc:
            yield sse_event("error", {"message": str(exc)})
        except Exception as exc:  # noqa: BLE001 — filet : pas de coupure brutale.
            logger.exception("Analyse approfondie : erreur inattendue.")
            yield sse_event("error", {"message": f"Erreur inattendue du Brain : {type(exc).__name__} : {exc}"})

    return StreamingResponse(event_stream(), media_type="text/event-stream")
