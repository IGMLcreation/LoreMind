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

import asyncio
import logging
from typing import AsyncIterator

import tiktoken

from app.application.llm_retry import generate_with_retry
from app.application.prompts import notebook as prompts
from app.application.query_rewrite import standalone_question
from app.core.language import DEFAULT as _DEFAULT_LANG, language_name
from app.domain.models import ChatMessage
from app.domain.ports import LLMChatProvider, LLMProvider, LLMProviderError
from app.infrastructure import vector_store

logger = logging.getLogger(__name__)

_NO_MATCH = "RAS"
_MAP_TEMPERATURE = 0.2

# --- Index de résumés (pré-filtrage des lots) --------------------------------
# Sans index : CHAQUE question relit TOUT le document (1 appel LLM par lot).
# Avec : les résumés de lots (construits UNE fois, cache disque) sont comparés
# à la question par embedding, et seuls les lots plausiblement pertinents sont
# relus. Sélection volontairement CONSERVATRICE (on préfère relire un lot de
# trop que rater une mention) ; désactivable via deep_summary_filter=False.

# Un lot est gardé si son score est proche du meilleur (marge) OU bon dans
# l'absolu ; et on garde toujours au moins _MIN_KEPT lots.
_SELECT_MARGIN = 0.10
_SELECT_FLOOR = 0.5
_MIN_KEPT = 3


class NotebookDeepUseCase:
    def __init__(
        self,
        llm: LLMProvider,
        batch_tokens: int = 10000,
        map_concurrency: int = 1,
        embedder=None,
        summary_filter: bool = True,
    ) -> None:
        self._llm = llm
        self._batch_tokens = max(2000, batch_tokens)
        # Lots MAP traités par vagues de cette taille (parallélisme LLM).
        self._map_concurrency = max(1, map_concurrency)
        # EmbeddingProvider (duck typing) pour l'index de résumés ; None = pas
        # de pré-filtrage (plein scan, comportement historique).
        self._embedder = embedder
        self._summary_filter = summary_filter

    async def stream(
        self,
        source_ids: list[str],
        messages: list[ChatMessage],
        context: str = "",
        history_limit: int = 8,
        language: str = _DEFAULT_LANG,
    ) -> AsyncIterator[dict]:
        """Yield des évènements : {type:'progress',current,total}, {type:'token',token},
        {type:'done'}. (Les erreurs LLM des lots sont tolérées : lot ignoré.)

        La dernière question utilisateur sert à la LECTURE du document (map) ; la
        SYNTHÈSE (reduce) reçoit les `history_limit` derniers messages → les relances
        conversationnelles (« et pour les autres ? ») fonctionnent aussi en approfondi.
        """
        # Question autonome : la phase MAP lit chaque lot avec LA question — sur
        # une relance conversationnelle, il faut y résoudre les références
        # implicites, sinon les lots sont filtrés sur un texte sans sujet.
        question = await standalone_question(self._llm, messages)
        # Lots PAR SOURCE (l'index de résumés est caché par source).
        per_source: list[tuple[str, list[dict]]] = []
        for sid in source_ids:
            chunks = vector_store.all_chunks(sid)
            for batch in self._group(chunks):
                per_source.append((sid, batch))
        if not per_source:
            yield {"type": "token", "token": "Aucune source indexée à analyser."}
            yield {"type": "done"}
            return

        # Pré-filtrage par index de résumés (best-effort : tout échec → plein scan).
        selected: set[int] | None = None
        if self._summary_filter and self._embedder is not None:
            try:
                async for ev_or_result in self._select_batches(per_source, question):
                    if isinstance(ev_or_result, dict):
                        yield ev_or_result  # progress de construction de l'index
                    else:
                        selected = ev_or_result
            except Exception as exc:  # noqa: BLE001 — le filtre ne doit jamais bloquer
                logger.warning("Index de résumés ignoré (échec) : %s", exc)
                selected = None
        if selected is not None:
            logger.info(
                "Analyse approfondie : %s/%s lot(s) retenus via l'index de résumés.",
                len(selected), len(per_source))

        indices = sorted(selected) if selected is not None else list(range(len(per_source)))
        total = len(indices)
        notes: list[str] = []
        # Lots traités par VAGUES parallèles ; les notes restent dans l'ordre du
        # document (gather préserve l'ordre des tâches de la vague).
        for start in range(0, total, self._map_concurrency):
            yield {"type": "progress", "current": start, "total": total}
            wave = indices[start:start + self._map_concurrency]
            results = await asyncio.gather(
                *(self._map_batch(question, per_source[i][1]) for i in wave),
                return_exceptions=True)
            for j, res in enumerate(results):
                if isinstance(res, LLMProviderError):
                    logger.warning(
                        "Analyse approfondie : lot %s/%s ignoré : %s", start + j + 1, total, res)
                elif isinstance(res, BaseException):
                    raise res  # bug inattendu : ne pas l'avaler
                elif res:
                    notes.append(res)
        yield {"type": "progress", "current": total, "total": total}

        notes_block = "\n\n".join(notes) if notes else "(aucune information pertinente trouvée dans le document)"
        context_block = (
            f"--- TA CAMPAGNE (structure, PNJ, univers) ---\n{context.strip()}\n--- FIN CAMPAGNE ---\n\n"
            if context.strip() else ""
        )
        system_prompt = prompts.REDUCE_SYSTEM.format(
            context_block=context_block, notes_block=notes_block,
            language_name=language_name(language))
        # Historique récent pour la cohérence des relances ; on garantit que le
        # dernier message est bien la question courante.
        reduce_messages = messages[-history_limit:] if messages else [ChatMessage(role="user", content=question)]
        llm_chat: LLMChatProvider = self._llm  # type: ignore[assignment]
        produced = False
        async for token in llm_chat.stream_chat(reduce_messages, system_prompt=system_prompt):
            if token:
                produced = True
                yield {"type": "token", "token": token}
        if not produced:
            # Jamais de bulle vide : message de repli + orientation vers le mode rapide,
            # mieux adapté aux demandes créatives (et qui propose des cartes d'action).
            yield {"type": "token", "token": (
                "Je n'ai pas trouvé d'éléments pertinents dans le document pour cette demande "
                "(elle porte sans doute sur des éléments que tu as inventés). Pour une "
                "**adaptation créative** — proposer des arcs, chapitres, scènes ou PNJ — "
                "utilise plutôt le bouton **« Envoyer »** (mode rapide) : il est conversationnel, "
                "voit ta campagne, et te propose des cartes « Créer dans la campagne »."
            )}
        yield {"type": "done"}

    # --- Index de résumés ------------------------------------------------------

    async def _select_batches(self, per_source: list[tuple[str, list[dict]]], question: str):
        """Générateur : yield des évènements `progress` pendant la construction de
        l'index (1ère analyse d'une source), puis le set des indices retenus —
        ou None si le filtre n'apporte rien (tous retenus)."""
        # 1. Charge/construit les résumés par source (cache disque).
        by_sid: dict[str, list[int]] = {}
        for i, (sid, _) in enumerate(per_source):
            by_sid.setdefault(sid, []).append(i)
        vectors: list[list[float] | None] = [None] * len(per_source)

        to_build = []
        for sid, idxs in by_sid.items():
            cached = vector_store.load_summaries(sid, self._batch_tokens)
            if cached is not None and len(cached) == len(idxs):
                for i, entry in zip(idxs, cached):
                    vectors[i] = entry.get("vector")
            else:
                to_build.append((sid, idxs))

        total_build = sum(len(idxs) for _, idxs in to_build)
        done_build = 0
        for sid, idxs in to_build:
            summaries: list[str] = []
            for start in range(0, len(idxs), self._map_concurrency):
                yield {"type": "progress", "current": done_build, "total": total_build}
                wave = idxs[start:start + self._map_concurrency]
                results = await asyncio.gather(
                    *(self._summarize_batch(per_source[i][1]) for i in wave))
                summaries.extend(results)
                done_build += len(wave)
            vecs = await self._embedder.embed(summaries, kind="document")
            entries = [{"summary": s, "vector": v} for s, v in zip(summaries, vecs)]
            vector_store.save_summaries(sid, self._batch_tokens, entries)
            for i, entry in zip(idxs, entries):
                vectors[i] = entry["vector"]

        # 2. Score de chaque lot face à la question, sélection conservatrice.
        qv = (await self._embedder.embed([question], kind="query"))[0]
        scores = [
            vector_store.cosine_similarity(qv, v) if v else 0.0
            for v in vectors
        ]
        best = max(scores)
        keep = {i for i, s in enumerate(scores) if s >= best - _SELECT_MARGIN or s >= _SELECT_FLOOR}
        floor = min(_MIN_KEPT, len(scores))
        if len(keep) < floor:
            keep = set(sorted(range(len(scores)), key=lambda i: -scores[i])[:floor])
        yield keep if len(keep) < len(scores) else None

    async def _summarize_batch(self, batch: list[dict]) -> str:
        excerpt = "\n\n".join(c.get("text", "").strip() for c in batch)
        raw = await generate_with_retry(
            self._llm, prompts.SUMMARY_PROMPT.format(excerpt=excerpt), temperature=_MAP_TEMPERATURE)
        return (raw or "").strip()

    async def _map_batch(self, question: str, batch: list[dict]) -> str:
        """Phase MAP d'un lot : extrait les infos pertinentes ('' si RAS)."""
        excerpt = "\n\n".join(
            f"(p. {c['page']}) {c['text'].strip()}" if c.get("page") else c["text"].strip()
            for c in batch
        )
        prompt = prompts.MAP_PROMPT.format(no_match=_NO_MATCH, question=question, excerpt=excerpt)
        raw = await generate_with_retry(self._llm, prompt, temperature=_MAP_TEMPERATURE)
        answer = raw.strip()
        if answer and answer.upper().rstrip(".") != _NO_MATCH:
            return answer
        return ""

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
