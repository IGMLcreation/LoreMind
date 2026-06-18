"""Tests du reranking LLM des passages RAG (app.application.rerank)."""
from __future__ import annotations

from app.application.rerank import pool_size, rerank


class FakeLLM:
    def __init__(self, response: str | None = None, exc: Exception | None = None) -> None:
        self.response = response
        self.exc = exc

    async def generate(self, prompt, *, temperature=None, output_format=None) -> str:
        if self.exc:
            raise self.exc
        return self.response


def test_pool_size():
    assert pool_size(8) == 24          # min(max(24, 8), 24)
    assert pool_size(4) == 12          # 4 * 3
    assert pool_size(10) == 24         # plafonné à POOL_MAX
    assert pool_size(1) == 3


async def test_rerank_skips_when_pool_not_larger_than_top_k():
    passages = [{"text": "a"}, {"text": "b"}]
    # len <= top_k → renvoyé tel quel, sans appel LLM.
    assert await rerank(FakeLLM(exc=AssertionError("ne doit pas être appelé")),
                        "q", passages, top_k=3) == passages


async def test_rerank_reorders_by_llm_scores():
    passages = [{"text": "a"}, {"text": "b"}, {"text": "c"}]
    out = await rerank(FakeLLM(response='{"scores":[1, 9, 5]}'), "q", passages, top_k=2)
    assert [p["text"] for p in out] == ["b", "c"]


async def test_rerank_stable_on_score_ties():
    passages = [{"text": "a"}, {"text": "b"}, {"text": "c"}]
    # Notes égales → ordre cosinus d'origine préservé.
    out = await rerank(FakeLLM(response='{"scores":[5, 5, 5]}'), "q", passages, top_k=2)
    assert [p["text"] for p in out] == ["a", "b"]


async def test_rerank_llm_failure_falls_back_to_cosine_order():
    passages = [{"text": "a"}, {"text": "b"}, {"text": "c"}]
    out = await rerank(FakeLLM(exc=RuntimeError("LLM HS")), "q", passages, top_k=2)
    assert [p["text"] for p in out] == ["a", "b"]


async def test_rerank_wrong_score_count_falls_back():
    passages = [{"text": "a"}, {"text": "b"}, {"text": "c"}]
    out = await rerank(FakeLLM(response='{"scores":[1, 2]}'), "q", passages, top_k=2)
    assert [p["text"] for p in out] == ["a", "b"]


async def test_rerank_non_numeric_scores_fall_back():
    passages = [{"text": "a"}, {"text": "b"}, {"text": "c"}]
    out = await rerank(FakeLLM(response='{"scores":["x","y","z"]}'), "q", passages, top_k=2)
    assert [p["text"] for p in out] == ["a", "b"]
