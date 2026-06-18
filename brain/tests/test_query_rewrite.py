"""Tests de la réécriture de question autonome (app.application.query_rewrite)."""
from __future__ import annotations

from app.application.query_rewrite import standalone_question
from app.domain.models import ChatMessage


class FakeLLM:
    def __init__(self, response: str | None = None, exc: Exception | None = None) -> None:
        self.response = response
        self.exc = exc
        self.called = False

    async def generate(self, prompt, *, temperature=None, output_format=None) -> str:
        self.called = True
        if self.exc:
            raise self.exc
        return self.response


async def test_single_turn_returns_last_user_without_calling_llm():
    llm = FakeLLM()
    q = await standalone_question(llm, [ChatMessage(role="user", content="Qui est Strahd ?")])
    assert q == "Qui est Strahd ?"
    assert llm.called is False


async def test_multi_turn_uses_llm_rewrite_and_strips_quotes():
    llm = FakeLLM(response='"Quelles sont les faiblesses de Strahd ?"')
    msgs = [
        ChatMessage(role="user", content="Qui est Strahd ?"),
        ChatMessage(role="assistant", content="Un vampire."),
        ChatMessage(role="user", content="Et ses faiblesses ?"),
    ]
    assert await standalone_question(llm, msgs) == "Quelles sont les faiblesses de Strahd ?"
    assert llm.called is True


async def test_llm_failure_falls_back_to_last_user():
    llm = FakeLLM(exc=RuntimeError("LLM HS"))
    msgs = [ChatMessage(role="user", content="A"), ChatMessage(role="user", content="B")]
    assert await standalone_question(llm, msgs) == "B"


async def test_suspiciously_long_rewrite_falls_back():
    llm = FakeLLM(response="x" * 500)
    msgs = [ChatMessage(role="user", content="A"), ChatMessage(role="user", content="B")]
    assert await standalone_question(llm, msgs) == "B"


async def test_empty_messages_returns_empty_string():
    llm = FakeLLM()
    assert await standalone_question(llm, []) == ""
    assert llm.called is False
