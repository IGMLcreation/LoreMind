"""Heartbeats pour garder un flux SSE 'vivant' pendant une coroutine longue.

Problème résolu : pendant un appel LLM lent (import sur provider gratuit), le
Brain ne produit AUCUN évènement SSE. Le Core (WebClient) ne 'voit aucun item'
et coupe la connexion sur timeout d'inactivité :

    ReactiveException: Did not observe any item or terminal signal within Nms

C'est le piège classique du SSE long. La parade standard = envoyer un keep-alive
périodique. `with_heartbeat` exécute une coroutine en émettant un évènement
'heartbeat' toutes les `interval` secondes tant qu'elle tourne, puis son résultat
('result', valeur). Le Core remet son chrono à zéro sur n'importe quel évènement
reçu (même inconnu) → plus de coupure, quelle que soit la lenteur du modèle.
"""
from __future__ import annotations

import asyncio
from typing import Any, AsyncIterator, Awaitable

# Bien sous le timeout d'inactivité du Core (600s) ET de tout proxy (nginx ~60s).
HEARTBEAT_INTERVAL_SECONDS = 15.0


async def with_heartbeat(
    coro: Awaitable[Any],
    *,
    interval: float = HEARTBEAT_INTERVAL_SECONDS,
) -> AsyncIterator[tuple[str, Any]]:
    """Exécute `coro` en émettant ('heartbeat', None) toutes les `interval`s tant
    qu'elle n'est pas terminée, puis ('result', valeur).

    L'exception éventuelle de `coro` est propagée (re-levée par `task.result()`),
    donc l'appelant peut l'attraper normalement. Si l'itération est abandonnée
    (client déconnecté), la tâche sous-jacente est annulée.
    """
    task: asyncio.Task = asyncio.ensure_future(coro)
    try:
        while not task.done():
            done, _ = await asyncio.wait({task}, timeout=interval)
            if not done:
                yield ("heartbeat", None)
        yield ("result", task.result())
    finally:
        if not task.done():
            task.cancel()
