"""Retry avec backoff pour les appels LLM one-shot (imports).

Les imports enchaînent de nombreux appels en série ; un échec TRANSITOIRE sur un
seul morceau (503/502 surcharge serveur, 504/524 passerelle, timeout réseau) ne
doit pas faire échouer tout l'import. On réessaie quelques fois avec une attente
croissante. Après épuisement, on relaie l'erreur (problème durable : quota, panne).

Réservé aux appels `generate` (one-shot, bufferisé) : réessayer est propre, sans
risque de doublons. À NE PAS utiliser sur le streaming (re-jouerait des tokens).
"""
from __future__ import annotations

import asyncio
import logging

from app.domain.ports import LLMProvider, LLMProviderError

logger = logging.getLogger(__name__)

_ATTEMPTS = 3
_BASE_DELAY_SECONDS = 2.0


async def generate_with_retry(
    llm: LLMProvider,
    prompt: str,
    *,
    output_format: str | None = None,
    temperature: float | None = None,
) -> str:
    """Comme `llm.generate`, mais réessaie les erreurs transitoires (backoff x2)."""
    delay = _BASE_DELAY_SECONDS
    last_error: LLMProviderError | None = None
    for attempt in range(_ATTEMPTS):
        try:
            return await llm.generate(prompt, output_format=output_format, temperature=temperature)
        except LLMProviderError as exc:
            last_error = exc
            if attempt < _ATTEMPTS - 1:
                logger.warning(
                    "Appel LLM échoué (tentative %s/%s) : %s — nouvelle tentative dans %ss.",
                    attempt + 1, _ATTEMPTS, exc, delay,
                )
                await asyncio.sleep(delay)
                delay *= 2
    assert last_error is not None
    raise last_error
