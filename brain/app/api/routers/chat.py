"""Endpoint du chat contextuel (/chat/stream) : Structural Context + jauge tokens."""
import json
from typing import Annotated, AsyncIterator

import tiktoken
from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import StreamingResponse

from app.api.chat_dto import ChatStreamRequestDTO
from app.api.chat_mapping import (
    to_campaign_context,
    to_game_system_context,
    to_lore_context,
    to_narrative_entity,
    to_page_context,
    to_session_context,
)
from app.api.deps import get_chat_use_case
from app.application.chat import ChatUseCase
from app.core.config import get_settings
from app.core.language import get_user_language
from app.domain.models import ChatMessage
from app.domain.ports import LLMProviderError

router = APIRouter()

# Encodeur tiktoken partagé — chargé une fois pour éviter le coût de lookup
# à chaque requête. On utilise cl100k_base (GPT-3.5/4) comme tokenizer
# universel approximatif : ±10% d'écart avec Llama/Gemma mais largement
# suffisant pour une jauge visuelle à l'utilisateur.
_TOKEN_ENCODER: tiktoken.Encoding | None = None


def _count_tokens(text: str | None) -> int:
    """Compte les tokens d'un texte via tiktoken. Null/empty → 0."""
    if not text:
        return 0
    global _TOKEN_ENCODER
    if _TOKEN_ENCODER is None:
        _TOKEN_ENCODER = tiktoken.get_encoding("cl100k_base")
    return len(_TOKEN_ENCODER.encode(text))


@router.post("/chat/stream")
async def chat_stream(
    body: ChatStreamRequestDTO,
    use_case: Annotated[ChatUseCase, Depends(get_chat_use_case)],
    language: Annotated[str, Depends(get_user_language)],
) -> StreamingResponse:
    """Chat streamé (Server-Sent Events) avec Structural Context.

    Accepte jusqu'à 4 contextes optionnels (Lore, Page focalisée, Campagne,
    entité narrative focalisée). Au moins un contexte racine (Lore ou
    Campagne) est requis pour que la requête ait du sens.

    Format de flux :
      - Chaque token : `data: {"token": "..."}\\n\\n`
      - Fin normale  : `event: done\\ndata: {}\\n\\n`
      - Erreur LLM   : `event: error\\ndata: {"message": "..."}\\n\\n`
    """
    if not body.has_scope():
        raise HTTPException(
            status_code=422,
            detail="Au moins un des deux contextes racines (lore_context ou campaign_context) est requis.",
        )

    messages = [ChatMessage(role=m.role, content=m.content) for m in body.messages]
    lore_context = to_lore_context(body.lore_context)
    page_context = to_page_context(body.page_context)
    campaign_context = to_campaign_context(body.campaign_context)
    narrative_entity = to_narrative_entity(body.narrative_entity)
    game_system_context = to_game_system_context(body.game_system_context)
    session_context = to_session_context(body.session_context)

    # --- Comptage tokens pour la jauge de contexte frontend ---
    # On construit le system prompt une fois ici pour le compter — le use case
    # le reconstruira à l'identique en interne (coût négligeable : concat de str).
    # Cette duplication évite de complexifier le contrat stream() avec un
    # paramètre optionnel system_prompt précalculé.
    system_prompt_preview = use_case.build_system_prompt(
        lore_context=lore_context,
        page_context=page_context,
        campaign_context=campaign_context,
        narrative_entity=narrative_entity,
        game_system_context=game_system_context,
        session_context=session_context,
        language=language,
    )
    # Dernier message = "current" (souvent user), le reste = historique accumulé.
    current_msg = messages[-1] if messages else None
    history_msgs = messages[:-1] if messages else []
    settings = get_settings()
    usage_payload = {
        "system": _count_tokens(system_prompt_preview),
        "history": sum(_count_tokens(m.content) for m in history_msgs),
        "current": _count_tokens(current_msg.content) if current_msg else 0,
        # Plafond connu seulement pour Ollama (num_ctx). Pour le cloud (1min/OpenRouter)
        # on ne connaît pas la fenêtre réelle → 0 = "pas de max" (jauge sans dénominateur).
        "max": settings.llm_num_ctx if settings.llm_provider == "ollama" else 0,
    }

    async def event_stream() -> AsyncIterator[str]:
        # Event 'usage' émis en tout premier : le frontend peut afficher la
        # jauge avant même le premier token de réponse.
        yield f"event: usage\ndata: {json.dumps(usage_payload, ensure_ascii=False)}\n\n"
        try:
            async for token in use_case.stream(
                messages,
                lore_context=lore_context,
                page_context=page_context,
                campaign_context=campaign_context,
                narrative_entity=narrative_entity,
                game_system_context=game_system_context,
                session_context=session_context,
                language=language,
            ):
                # json.dumps avec ensure_ascii=False pour préserver les accents
                yield f"data: {json.dumps({'token': token}, ensure_ascii=False)}\n\n"
            yield "event: done\ndata: {}\n\n"
        except LLMProviderError as exc:
            yield f"event: error\ndata: {json.dumps({'message': str(exc)})}\n\n"

    return StreamingResponse(event_stream(), media_type="text/event-stream")
