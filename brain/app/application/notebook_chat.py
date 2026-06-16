"""Use case : chat ANCRÉ sur les sources d'un notebook (RAG).

À chaque message, on retrouve les passages pertinents des sources (via le RAG) et
on les injecte dans le prompt système, en plus du contexte de campagne. Le modèle
répond donc en s'appuyant sur la/les source(s) — pas sur ses connaissances générales.
"""
from __future__ import annotations

from typing import AsyncIterator

from app.application.notebook_rag import NotebookRagUseCase
from app.application.prompts import notebook as prompts
from app.application.query_rewrite import standalone_question
from app.application.rerank import pool_size, rerank
from app.core.language import DEFAULT as _DEFAULT_LANG, language_name
from app.domain.models import ChatMessage
from app.domain.ports import LLMChatProvider


class NotebookChatUseCase:
    def __init__(
        self, rag: NotebookRagUseCase, llm: LLMChatProvider, rerank_enabled: bool = False
    ) -> None:
        self._rag = rag
        self._llm = llm
        # Reranking LLM d'un pool élargi avant injection (voir app.application.rerank).
        self._rerank_enabled = rerank_enabled

    async def stream(
        self,
        source_ids: list[str],
        messages: list[ChatMessage],
        context: str = "",
        top_k: int = 6,
        language: str = _DEFAULT_LANG,
    ) -> AsyncIterator[dict]:
        """Yield des évènements : {type:'sources', sources:[…]} (une fois, avant la
        réponse — transparence sur les passages utilisés), puis {type:'token', token}."""
        # Question AUTONOME pour la recherche : sur une relance (« et ses
        # faiblesses ? »), l'embedding du dernier message seul ne contient pas
        # le sujet → on le résout depuis l'historique (best-effort, 1 appel léger,
        # uniquement à partir du 2e tour). La réponse, elle, voit tout l'historique.
        search_query = await standalone_question(self._llm, messages)
        if self._rerank_enabled:
            # Pool élargi → notation LLM → top_k final (meilleure précision sur
            # les questions ambiguës, au prix d'un appel avant le premier token).
            pool = await self._rag.retrieve(
                source_ids, search_query, top_k=pool_size(top_k))
            passages = await rerank(self._llm, search_query, pool, top_k)
        else:
            passages = await self._rag.retrieve(source_ids, search_query, top_k=top_k)
        # Évènement 'sources' AVANT le premier token : l'UI peut afficher les
        # pages utilisées (« 📖 p. 12, 47 ») dès le début de la réponse.
        yield {"type": "sources", "sources": [
            {
                "source_id": p.get("source_id"),
                "page": p.get("page"),
                "score": round(float(p.get("score") or 0.0), 3),
            }
            for p in passages
        ]}
        sources_block = (
            "\n\n".join(self._format_passage(p) for p in passages)
            if passages else "(aucun passage pertinent trouvé dans les sources)"
        )
        context_block = (
            f"--- TA CAMPAGNE ---\n{context.strip()}\n--- FIN CAMPAGNE ---\n\n"
            if context.strip() else "--- TA CAMPAGNE ---\n(aucune donnée de campagne)\n--- FIN CAMPAGNE ---\n\n"
        )
        system_prompt = prompts.CHAT_SYSTEM.format(
            context_block=context_block, sources_block=sources_block,
            language_name=language_name(language))
        async for token in self._llm.stream_chat(messages, system_prompt=system_prompt):
            yield {"type": "token", "token": token}

    @staticmethod
    def _format_passage(p: dict) -> str:
        page = p.get("page")
        prefix = f"(p. {page}) " if page else ""
        return f"• {prefix}{p['text'].strip()}"
