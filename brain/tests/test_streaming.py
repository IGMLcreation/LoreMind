"""Tests des heartbeats SSE (app.application.streaming.with_heartbeat)."""
from __future__ import annotations

import asyncio

import pytest

from app.application.streaming import with_heartbeat


async def _collect(agen) -> list[tuple[str, object]]:
    return [ev async for ev in agen]


async def test_fast_coro_emits_only_result():
    async def quick() -> int:
        return 42
    events = await _collect(with_heartbeat(quick(), interval=0.05))
    assert events == [("result", 42)]


async def test_slow_coro_emits_heartbeats_then_result():
    async def slow() -> str:
        await asyncio.sleep(0.06)
        return "fini"
    events = await _collect(with_heartbeat(slow(), interval=0.02))
    assert ("heartbeat", None) in events
    assert events[-1] == ("result", "fini")


async def test_relays_status_messages_from_queue():
    queue: asyncio.Queue = asyncio.Queue()

    async def work() -> str:
        await asyncio.sleep(0.05)
        return "ok"

    queue.put_nowait("fournisseur saturé, nouvel essai")
    events = await _collect(with_heartbeat(work(), interval=0.02, status_queue=queue))
    assert ("status", "fournisseur saturé, nouvel essai") in events
    assert events[-1] == ("result", "ok")


async def test_propagates_coro_exception():
    async def boom() -> None:
        raise ValueError("échec interne")
    with pytest.raises(ValueError, match="échec interne"):
        await _collect(with_heartbeat(boom(), interval=0.05))
