"""Tests du canal de statut d'import (app.application.import_status)."""
from __future__ import annotations

import asyncio

from app.application import import_status


def test_notify_is_noop_without_queue():
    # Hors import (aucune queue installée) : ne lève pas, ne fait rien.
    import_status.notify_status("personne n'écoute")  # ne doit pas lever


def test_notify_publishes_when_queue_installed():
    queue: asyncio.Queue = asyncio.Queue()
    token = import_status.set_status_queue(queue)
    try:
        import_status.notify_status("morceau re-découpé")
        assert queue.get_nowait() == "morceau re-découpé"
    finally:
        import_status.reset_status_queue(token)


def test_reset_restores_noop():
    queue: asyncio.Queue = asyncio.Queue()
    token = import_status.set_status_queue(queue)
    import_status.reset_status_queue(token)
    # Après reset : plus de queue active → no-op, la queue reste vide.
    import_status.notify_status("ignoré")
    assert queue.empty()
