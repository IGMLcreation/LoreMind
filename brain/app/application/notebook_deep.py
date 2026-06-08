"""Use case « Analyse approfondie » d'un notebook : map-reduce sur TOUT le document.

Contrairement au chat RAG (qui ne ramène que les top-k extraits), ce mode lit
l'INTÉGRALITÉ des sources par lots :
  - MAP    : pour chaque lot, le modèle extrait ce qui est pertinent pour la question
             (ou « RAS » si rien) ;
  - REDUCE : il synthétise toutes les notes en une réponse finale (streamée).

→ Répond aux questions globales/exhaustives (« liste tous les… ») quel que soit le
modèle, au prix de plusieurs appels (comme l'import). Le lot est dimensionné par
`batch_tokens` (= taille de morceau d'import) : avec un modèle gros-contexte, peu de
lots ; avec un petit modèle local, plus de lots (mais ça reste exhaustif).
"""
from __future__ import annotations

import logging
from typing import AsyncIterator

import tiktoken

from app.application.llm_retry import generate_with_retry
from app.domain.models import ChatMessage
from app.domain.ports import LLMChatProvider, LLMProvider, LLMProviderError
from app.infrastructure import vector_store

logger = logging.getLogger(__name__)

_NO_MATCH = "RAS"
_MAP_TEMPERATURE = 0.2

_MAP_PROMPT = """Voici un EXTRAIT d'un document. Extrais UNIQUEMENT les informations
pertinentes pour répondre à la question ci-dessous. Conserve les détails utiles et
indique les numéros de page (format « p. X »). Si l'extrait ne contient RIEN de
pertinent, réponds EXACTEMENT « {no_match} » et rien d'autre.

QUESTION : {question}

--- EXTRAIT ---
{excerpt}
--- FIN EXTRAIT ---

Informations pertinentes (ou « {no_match} ») :"""

_REDUCE_SYSTEM = """Tu réponds à la question d'un MJ à partir de NOTES extraites de
l'ENSEMBLE d'un document source (donc tu as une vue COMPLÈTE, pas un simple extrait).
Synthétise ces notes en une réponse claire et structurée, cite les pages (« p. X »),
et n'invente rien qui n'y figure pas. Si une CAMPAGNE est fournie ci-dessous, relie ta
réponse à sa structure / ses PNJ pour des adaptations cohérentes.

{context_block}
--- NOTES EXTRAITES DE TOUT LE DOCUMENT ---
{notes_block}
--- FIN DES NOTES ---

Réponds en français."""


class NotebookDeepUseCase:
    def __init__(self, llm: LLMProvider, batch_tokens: int = 10000) -> None:
        self._llm = llm
        self._batch_tokens = max(2000, batch_tokens)

    async def stream(
        self,
        source_ids: list[str],
        messages: list[ChatMessage],
        context: str = "",
        history_limit: int = 8,
    ) -> AsyncIterator[dict]:
        """Yield des évènements : {type:'progress',current,total}, {type:'token',token},
        {type:'done'}. (Les erreurs LLM des lots sont tolérées : lot ignoré.)

        La dernière question utilisateur sert à la LECTURE du document (map) ; la
        SYNTHÈSE (reduce) reçoit les `history_limit` derniers messages → les relances
        conversationnelles (« et pour les autres ? ») fonctionnent aussi en approfondi.
        """
        question = next((m.content for m in reversed(messages) if m.role == "user"), "")
        chunks: list[dict] = []
        for sid in source_ids:
            chunks.extend(vector_store.all_chunks(sid))
        if not chunks:
            yield {"type": "token", "token": "Aucune source indexée à analyser."}
            yield {"type": "done"}
            return

        batches = self._group(chunks)
        total = len(batches)
        notes: list[str] = []
        for i, batch in enumerate(batches):
            yield {"type": "progress", "current": i, "total": total}
            excerpt = "\n\n".join(
                f"(p. {c['page']}) {c['text'].strip()}" if c.get("page") else c["text"].strip()
                for c in batch
            )
            prompt = _MAP_PROMPT.format(no_match=_NO_MATCH, question=question, excerpt=excerpt)
            try:
                raw = await generate_with_retry(self._llm, prompt, temperature=_MAP_TEMPERATURE)
            except LLMProviderError as exc:
                logger.warning("Analyse approfondie : lot %s/%s ignoré : %s", i + 1, total, exc)
                continue
            answer = raw.strip()
            if answer and answer.upper().rstrip(".") != _NO_MATCH:
                notes.append(answer)
        yield {"type": "progress", "current": total, "total": total}

        notes_block = "\n\n".join(notes) if notes else "(aucune information pertinente trouvée dans le document)"
        context_block = (
            f"--- TA CAMPAGNE (structure, PNJ, univers) ---\n{context.strip()}\n--- FIN CAMPAGNE ---\n\n"
            if context.strip() else ""
        )
        system_prompt = _REDUCE_SYSTEM.format(context_block=context_block, notes_block=notes_block)
        # Historique récent pour la cohérence des relances ; on garantit que le
        # dernier message est bien la question courante.
        reduce_messages = messages[-history_limit:] if messages else [ChatMessage(role="user", content=question)]
        llm_chat: LLMChatProvider = self._llm  # type: ignore[assignment]
        async for token in llm_chat.stream_chat(reduce_messages, system_prompt=system_prompt):
            yield {"type": "token", "token": token}
        yield {"type": "done"}

    def _group(self, chunks: list[dict]) -> list[list[dict]]:
        """Regroupe les extraits en lots ~`batch_tokens` (compte tiktoken)."""
        enc = tiktoken.get_encoding("cl100k_base")
        batches: list[list[dict]] = []
        current: list[dict] = []
        current_tokens = 0
        for c in chunks:
            t = len(enc.encode(c.get("text", "")))
            if current and current_tokens + t > self._batch_tokens:
                batches.append(current)
                current, current_tokens = [], 0
            current.append(c)
            current_tokens += t
        if current:
            batches.append(current)
        return batches
