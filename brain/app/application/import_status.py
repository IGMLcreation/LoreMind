"""Canal de statut des imports : remonte à l'UI ce qui n'existait qu'en logs.

Problème résolu : pendant un import, les événements internes (retry parce que
le fournisseur IA est saturé, re-découpage d'un morceau trop gros…) n'étaient
visibles que dans les logs Docker. L'utilisateur voyait une barre de
progression figée sans explication.

Mécanisme : le flux d'import (use case `stream()`) installe une Queue dans une
ContextVar ; les couches profondes (retry LLM, re-découpage) y publient des
messages via `notify_status()` sans connaître le flux SSE. La ContextVar est
propagée automatiquement aux tâches asyncio enfants → chaque import concurrent
a SA queue, sans couplage ni paramètre à faire transiter partout.
"""
from __future__ import annotations

import asyncio
from contextvars import ContextVar, Token

_QUEUE: ContextVar[asyncio.Queue | None] = ContextVar("import_status_queue", default=None)


def set_status_queue(queue: asyncio.Queue | None) -> Token:
    """Installe la queue de statut pour le contexte courant (et ses tâches filles).

    Renvoie le token à passer à `reset_status_queue` en fin d'import.
    """
    return _QUEUE.set(queue)


def reset_status_queue(token: Token) -> None:
    _QUEUE.reset(token)


def notify_status(message: str) -> None:
    """Publie un message de statut si un import écoute. No-op sinon (appels
    LLM hors import : chat, génération de page…)."""
    queue = _QUEUE.get()
    if queue is not None:
        queue.put_nowait(message)
