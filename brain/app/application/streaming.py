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
    status_queue: "asyncio.Queue | None" = None,
) -> AsyncIterator[tuple[str, Any]]:
    """Exécute `coro` en émettant ('heartbeat', None) toutes les `interval`s tant
    qu'elle n'est pas terminée, puis ('result', valeur).

    Si `status_queue` est fournie, les messages qui y sont publiés pendant
    l'exécution (cf. import_status.notify_status : retry LLM, re-découpage…)
    sont émis AU FIL DE L'EAU sous forme ('status', message) — c'est ce qui
    permet à l'UI d'expliquer une attente au lieu d'une barre figée.

    L'exception éventuelle de `coro` est propagée (re-levée par `task.result()`),
    donc l'appelant peut l'attraper normalement. Si l'itération est abandonnée
    (client déconnecté), la tâche sous-jacente est annulée.
    """
    task: asyncio.Task = asyncio.ensure_future(coro)
    getter: asyncio.Task | None = None
    try:
        while not task.done():
            waiters: set[asyncio.Task] = {task}
            if status_queue is not None and getter is None:
                getter = asyncio.ensure_future(status_queue.get())
            if getter is not None:
                waiters.add(getter)
            done, _ = await asyncio.wait(
                waiters, timeout=interval, return_when=asyncio.FIRST_COMPLETED)
            if getter is not None and getter in done:
                yield ("status", getter.result())
                getter = None  # un nouveau get() sera créé au tour suivant
            if not done:
                yield ("heartbeat", None)
        # Vide les statuts restés en file (publiés juste avant la fin de la tâche).
        if status_queue is not None:
            while not status_queue.empty():
                yield ("status", status_queue.get_nowait())
        yield ("result", task.result())
    finally:
        if getter is not None and not getter.done():
            getter.cancel()
        if not task.done():
            task.cancel()
