"""Tests du retry des appels LLM one-shot (app.application.llm_retry).

`asyncio.sleep` est neutralisé (et enregistré) pour que les backoffs n'imposent
aucune attente réelle tout en vérifiant les durées choisies.
"""
from __future__ import annotations

import pytest

from app.application import llm_retry
from app.application.llm_retry import (
    _is_daily_quota,
    _is_rate_limit,
    _suggested_retry_after,
    generate_with_retry,
)
from app.domain.ports import LLMGenerationTimeout, LLMProviderError


class FakeLLM:
    """LLM factice : rejoue une liste de comportements (exception ou texte)."""

    def __init__(self, behaviors: list) -> None:
        self._behaviors = list(behaviors)
        self.calls = 0

    async def generate(self, prompt: str, *, output_format=None, temperature=None) -> str:
        b = self._behaviors[self.calls]
        self.calls += 1
        if isinstance(b, Exception):
            raise b
        return b


@pytest.fixture
def slept(monkeypatch):
    """Neutralise asyncio.sleep et enregistre les durées demandées."""
    recorded: list[float] = []

    async def fake_sleep(d):
        recorded.append(d)

    monkeypatch.setattr("asyncio.sleep", fake_sleep)
    return recorded


# --- helpers de classification -------------------------------------------------

def test_is_rate_limit():
    assert _is_rate_limit(LLMProviderError("HTTP 429 Too Many Requests"))
    assert _is_rate_limit(LLMProviderError("rate limit reached"))
    assert not _is_rate_limit(LLMProviderError("HTTP 500"))


def test_is_daily_quota():
    assert _is_daily_quota(LLMProviderError("free-models-per-day limit"))
    assert _is_daily_quota(LLMProviderError("quota per day exceeded"))
    assert not _is_daily_quota(LLMProviderError("429 per-minute"))


def test_suggested_retry_after():
    assert _suggested_retry_after(LLMProviderError('{"retry_after_seconds": 8}')) == 8.0
    assert _suggested_retry_after(LLMProviderError('Retry-After: 12')) == 12.0
    assert _suggested_retry_after(LLMProviderError("pas de hint")) is None


# --- generate_with_retry -------------------------------------------------------

async def test_returns_on_first_success(slept):
    llm = FakeLLM(["réponse"])
    assert await generate_with_retry(llm, "p") == "réponse"
    assert llm.calls == 1
    assert slept == []


async def test_retries_transient_error_then_succeeds(slept):
    llm = FakeLLM([LLMProviderError("HTTP 503"), "ok"])
    assert await generate_with_retry(llm, "p") == "ok"
    assert llm.calls == 2
    assert slept == [3.0]  # _BASE_DELAY_SECONDS


async def test_timeout_raises_immediately_without_retry(slept):
    llm = FakeLLM([LLMGenerationTimeout("trop lent")])
    with pytest.raises(LLMGenerationTimeout):
        await generate_with_retry(llm, "p")
    assert llm.calls == 1
    assert slept == []


async def test_daily_quota_aborts_immediately(slept):
    llm = FakeLLM([LLMProviderError("free-models-per-day exceeded")])
    with pytest.raises(LLMProviderError):
        await generate_with_retry(llm, "p")
    assert llm.calls == 1
    assert slept == []


async def test_exhausts_attempts_then_raises_last(slept):
    llm = FakeLLM([LLMProviderError("503 a"), LLMProviderError("503 b"), LLMProviderError("503 c")])
    with pytest.raises(LLMProviderError, match="503 c"):
        await generate_with_retry(llm, "p")
    assert llm.calls == 3
    # 2 attentes entre 3 tentatives (backoff exponentiel 3s puis 6s).
    assert slept == [3.0, 6.0]


async def test_rate_limit_respects_suggested_retry_after(slept):
    llm = FakeLLM([LLMProviderError('429 {"retry_after_seconds": 8}'), "ok"])
    assert await generate_with_retry(llm, "p") == "ok"
    # min(8 + 2, 60) = 10
    assert slept == [10.0]
