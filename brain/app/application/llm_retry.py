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
import re

from app.domain.ports import LLMGenerationTimeout, LLMProvider, LLMProviderError

logger = logging.getLogger(__name__)

# 3 tentatives : assez pour absorber un hoquet transitoire, sans s'acharner des
# minutes sur un modèle durablement lent/saturé (les heartbeats gardent le flux
# vivant, mais inutile de faire patienter l'utilisateur 15 min pour rien).
_ATTEMPTS = 3
_BASE_DELAY_SECONDS = 3.0
# Un rate limit (429) "par minute" ne se libère pas en 2-3s : on attend plus
# longtemps pour ces erreurs-là (le free tier OpenRouter plafonne ~20 req/min).
_RATE_LIMIT_DELAYS = [10.0, 25.0, 45.0]


def _is_rate_limit(exc: LLMProviderError) -> bool:
    msg = str(exc).lower()
    return "429" in msg or "rate" in msg or "too many requests" in msg


def _is_daily_quota(exc: LLMProviderError) -> bool:
    """Limite PAR JOUR (vs par minute) : réessayer est inutile, elle ne se libère
    qu'au reset quotidien. OpenRouter le précise dans le corps du 429."""
    msg = str(exc).lower()
    return "per-day" in msg or "per day" in msg or "free-models-per-day" in msg


# OpenRouter renvoie souvent le délai conseillé (saturation amont) :
# "retry_after_seconds": 8  ou  "Retry-After": "8". On le respecte plutôt que
# d'attendre une durée fixe arbitraire.
_RETRY_AFTER_RE = re.compile(r'retry[_-]?after(?:_seconds)?"?\s*:\s*"?([0-9]+(?:\.[0-9]+)?)', re.IGNORECASE)


def _suggested_retry_after(exc: LLMProviderError) -> float | None:
    match = _RETRY_AFTER_RE.search(str(exc))
    if not match:
        return None
    try:
        return float(match.group(1))
    except ValueError:
        return None


async def generate_with_retry(
    llm: LLMProvider,
    prompt: str,
    *,
    output_format: str | None = None,
    temperature: float | None = None,
) -> str:
    """Comme `llm.generate`, mais réessaie les erreurs transitoires (backoff).

    Backoff plus long pour les 429 (rate limit) afin de laisser la fenêtre se
    libérer. Nombre de tentatives borné : si le quota est durablement épuisé
    (ex. limite/jour), l'erreur finit par remonter au lieu de boucler sans fin.
    """
    delay = _BASE_DELAY_SECONDS
    last_error: LLMProviderError | None = None
    for attempt in range(_ATTEMPTS):
        try:
            return await llm.generate(prompt, output_format=output_format, temperature=temperature)
        except LLMGenerationTimeout:
            # Timeout de DÉBIT (génération trop lente pour la sortie demandée) :
            # rejouer le même prompt re-timeoutera à l'identique — on a déjà perdu
            # `timeout` secondes. On remonte tout de suite : l'appelant (import)
            # sait re-découper le morceau en deux pour réduire la sortie.
            raise
        except LLMProviderError as exc:
            last_error = exc
            # Quota JOURNALIER épuisé : inutile d'insister, on remonte tout de suite
            # (sinon on enchaîne des attentes longues pour rien, et on spamme l'API).
            if _is_daily_quota(exc):
                logger.warning("Quota journalier du fournisseur épuisé — abandon : %s", exc)
                raise
            if attempt < _ATTEMPTS - 1:
                if _is_rate_limit(exc):
                    suggested = _suggested_retry_after(exc)
                    if suggested is not None:
                        # Indication serveur (saturation amont) + petite marge, plafonnée.
                        wait = min(suggested + 2.0, 60.0)
                    else:
                        wait = _RATE_LIMIT_DELAYS[min(attempt, len(_RATE_LIMIT_DELAYS) - 1)]
                else:
                    wait = delay
                    delay *= 2
                logger.warning(
                    "Appel LLM échoué (tentative %s/%s)%s : %s — nouvelle tentative dans %ss.",
                    attempt + 1, _ATTEMPTS, " [rate limit]" if _is_rate_limit(exc) else "",
                    exc, wait,
                )
                await asyncio.sleep(wait)
    assert last_error is not None
    raise last_error
