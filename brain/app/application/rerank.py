"""Reranking LLM des passages RAG (chat des ateliers).

Le cosinus classe par similarité de SURFACE ; sur les questions ambiguës, des
passages proches lexicalement mais inutiles passent devant l'extrait qui répond
vraiment. Le reranking récupère un POOL élargi (ex. 3× top_k), fait noter la
pertinence de chaque extrait par le LLM en UN appel, et garde les top_k mieux
notés. Coût : ~1 appel LLM avant le premier token — opt-in via RAG_RERANK.
"""
from __future__ import annotations

import logging

from app.application.llm_json import load_json_object
from app.application.prompts import rerank as prompts

logger = logging.getLogger(__name__)

# Taille du pool élargi : multiple du top_k demandé, plafonné (le prompt de
# notation doit rester raisonnable même avec rag_top_k élevé).
POOL_FACTOR = 3
POOL_MAX = 24

# Un extrait long n'a pas besoin d'être noté en entier : tronquer borne le
# prompt sans changer le jugement de pertinence.
_EXCERPT_CHARS = 600


def pool_size(top_k: int) -> int:
    """Taille du pool à récupérer avant reranking."""
    return min(max(top_k * POOL_FACTOR, top_k), POOL_MAX)


async def rerank(llm, question: str, passages: list[dict], top_k: int) -> list[dict]:
    """Renvoie les `top_k` passages les mieux notés par le LLM (tri stable :
    à note égale, l'ordre cosinus d'origine est préservé).

    BEST-EFFORT : échec LLM, JSON invalide ou nombre de notes incohérent →
    on renvoie simplement les `top_k` premiers du classement cosinus.
    """
    if len(passages) <= top_k:
        return passages
    numbered = "\n\n".join(
        f"--- EXTRAIT {i + 1} ---\n{(p.get('text') or '')[:_EXCERPT_CHARS]}"
        for i, p in enumerate(passages)
    )
    prompt = prompts.RERANK_PROMPT.format(
        question=question, passages=numbered, count=len(passages))
    try:
        raw = await llm.generate(prompt, temperature=0.0)
    except Exception as exc:  # noqa: BLE001 — un chat dégradé vaut mieux que pas de chat
        logger.warning("Reranking ignoré (échec LLM) : %s", exc)
        return passages[:top_k]
    parsed, _ = load_json_object(raw)
    scores = parsed.get("scores") if isinstance(parsed, dict) else None
    if not isinstance(scores, list) or len(scores) != len(passages):
        logger.warning("Reranking ignoré (notes inexploitables).")
        return passages[:top_k]
    try:
        scored = [(float(s), i) for i, s in enumerate(scores)]
    except (TypeError, ValueError):
        logger.warning("Reranking ignoré (notes non numériques).")
        return passages[:top_k]
    order = sorted(range(len(passages)), key=lambda i: (-scored[i][0], i))
    return [passages[i] for i in order[:top_k]]
