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
from app.application.query_rewrite import standalone_question
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

_REDUCE_SYSTEM = """Tu es l'assistant-MJ d'un jeu de rôle. Tu réponds à la demande du MJ en
t'appuyant sur TROIS sources : (1) des NOTES extraites de l'ENSEMBLE du document source (vue
complète — mais POSSIBLEMENT VIDE si rien d'utile n'y figure), (2) le contexte de sa CAMPAGNE,
(3) la conversation ci-dessous.

- Si les notes contiennent des éléments utiles : exploite-les et CITE les pages (« p. X »).
- Si les notes sont VIDES ou pauvres (cas fréquent d'une demande CRÉATIVE portant sur des
  éléments INVENTÉS par le MJ) : ne te bloque surtout PAS. Aide-le quand même en t'appuyant
  sur sa CAMPAGNE, la CONVERSATION et ta connaissance du genre — propose des adaptations
  concrètes (arcs, chapitres, scènes, PNJ), structurées et jouables.
- Sois concret et utile. N'affirme rien de FAUX sur le contenu du document.

{context_block}
--- NOTES EXTRAITES DE TOUT LE DOCUMENT ---
{notes_block}
--- FIN DES NOTES ---

Réponds en français."""


class NotebookDeepUseCase:
    def __init__(
        self, llm: LLMProvider, batch_tokens: int = 10000, map_concurrency: int = 1
    ) -> None:
        self._llm = llm
        self._batch_tokens = max(2000, batch_tokens)
        # Lots MAP traités par vagues de cette taille (parallélisme LLM).
        self._map_concurrency = max(1, map_concurrency)

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
        # Question autonome : la phase MAP lit chaque lot avec LA question — sur
        # une relance conversationnelle, il faut y résoudre les références
        # implicites, sinon les lots sont filtrés sur un texte sans sujet.
        question = await standalone_question(self._llm, messages)
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
        # Lots traités par VAGUES parallèles ; les notes restent dans l'ordre du
        # document (gather préserve l'ordre des tâches de la vague).
        for start in range(0, total, self._map_concurrency):
            yield {"type": "progress", "current": start, "total": total}
            wave = batches[start:start + self._map_concurrency]
            results = await asyncio.gather(
                *(self._map_batch(question, b) for b in wave), return_exceptions=True)
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
        system_prompt = _REDUCE_SYSTEM.format(context_block=context_block, notes_block=notes_block)
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

    async def _map_batch(self, question: str, batch: list[dict]) -> str:
        """Phase MAP d'un lot : extrait les infos pertinentes ('' si RAS)."""
        excerpt = "\n\n".join(
            f"(p. {c['page']}) {c['text'].strip()}" if c.get("page") else c["text"].strip()
            for c in batch
        )
        prompt = _MAP_PROMPT.format(no_match=_NO_MATCH, question=question, excerpt=excerpt)
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
