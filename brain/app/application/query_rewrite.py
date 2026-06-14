"""Réécriture de la question courante en question AUTONOME (chat des ateliers).

Problème : le retrieval (embedding) et la phase MAP de l'analyse approfondie ne
voient que le DERNIER message. Une relance comme « et ses faiblesses ? » ne
contient pas le sujet (Strahd) → recherche aveugle. La parade standard
(conversational query rewriting) : un appel LLM léger condense la conversation
en une question autonome, utilisée UNIQUEMENT pour la recherche — la réponse
finale, elle, voit toujours l'historique complet.
"""
from __future__ import annotations

import logging

from app.domain.models import ChatMessage

logger = logging.getLogger(__name__)

# Nombre de messages récents fournis au réécrivain (assez pour résoudre les
# pronoms, pas plus — la latence de cet appel doit rester négligeable).
_MAX_HISTORY = 6

# Garde-fou : une « question » réécrite anormalement longue est suspecte (le
# modèle a divagué) → on retombe sur la question brute.
_MAX_REWRITE_CHARS = 400

_REWRITE_PROMPT = """Voici la fin d'une conversation entre un Maître de Jeu et son assistant.
Réécris le DERNIER message de l'utilisateur en une question AUTONOME et complète :
remplace les pronoms et références implicites (« il », « ses », « ce lieu », « et pour
les autres ? ») par ce qu'ils désignent dans la conversation.

Règles :
- Réponds UNIQUEMENT par la question réécrite, sans guillemets ni préfixe.
- Conserve la langue et l'intention d'origine. N'ajoute RIEN qui n'est pas demandé.
- Si le dernier message est déjà autonome, recopie-le tel quel.

--- CONVERSATION ---
{conversation}
--- FIN ---

Question autonome :"""


async def standalone_question(llm, messages: list[ChatMessage]) -> str:
    """Condense `messages` en une question autonome pour la RECHERCHE.

    Best-effort : premier message de la conversation, échec LLM ou réponse
    suspecte → on renvoie simplement la dernière question brute (comportement
    historique). `llm` doit exposer `generate()` (duck typing des adapters).
    """
    last_user = next((m.content for m in reversed(messages) if m.role == "user"), "")
    user_turns = sum(1 for m in messages if m.role == "user" and m.content.strip())
    if user_turns <= 1 or not last_user.strip():
        return last_user  # pas d'historique à résoudre → appel LLM inutile

    recent = [m for m in messages if m.content.strip()][-_MAX_HISTORY:]
    conversation = "\n".join(f"{m.role.upper()}: {m.content.strip()}" for m in recent)
    try:
        raw = await llm.generate(
            _REWRITE_PROMPT.format(conversation=conversation), temperature=0.0)
    except Exception as exc:  # noqa: BLE001 — la recherche dégradée vaut mieux que pas de réponse
        logger.warning("Réécriture de question ignorée (échec LLM) : %s", exc)
        return last_user
    rewritten = (raw or "").strip().strip('"').strip()
    if not rewritten or len(rewritten) > _MAX_REWRITE_CHARS:
        return last_user
    return rewritten
