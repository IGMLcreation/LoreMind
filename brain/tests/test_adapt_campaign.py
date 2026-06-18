"""Tests du use case de conseils d'adaptation (app.application.adapt_campaign)."""
from __future__ import annotations

import pytest

from app.application.adapt_campaign import AdaptCampaignUseCase
from app.domain.models import ChatMessage, ExtractedDocument, ExtractedPage
from app.domain.ports import PdfExtractionError


class FakeExtractor:
    def __init__(self, doc: ExtractedDocument) -> None:
        self._doc = doc

    def extract(self, pdf_bytes: bytes) -> ExtractedDocument:
        return self._doc


class FakeChatLLM:
    def __init__(self, tokens: list[str]) -> None:
        self._tokens = tokens
        self.system_prompt: str | None = None
        self.messages: list[ChatMessage] | None = None

    async def stream_chat(self, messages, *, system_prompt=None, temperature=None):
        self.messages = messages
        self.system_prompt = system_prompt
        for t in self._tokens:
            yield t


def _doc(text: str) -> ExtractedDocument:
    return ExtractedDocument(pages=[ExtractedPage(index=0, text=text, used_ocr=False)])


async def test_stream_yields_tokens_and_builds_context():
    llm = FakeChatLLM(["con", "seil"])
    uc = AdaptCampaignUseCase(llm, FakeExtractor(_doc("contenu du pdf")))
    out = [t async for t in uc.stream(b"x", "mon brief de campagne",
                                      [ChatMessage(role="user", content="aide")])]
    assert out == ["con", "seil"]
    assert "mon brief de campagne" in llm.system_prompt
    assert "contenu du pdf" in llm.system_prompt


async def test_stream_empty_pdf_text_raises():
    uc = AdaptCampaignUseCase(FakeChatLLM([]), FakeExtractor(_doc("   ")))
    with pytest.raises(PdfExtractionError):
        [t async for t in uc.stream(b"x", "brief", [])]


async def test_stream_injects_default_request_when_no_messages():
    llm = FakeChatLLM(["ok"])
    uc = AdaptCampaignUseCase(llm, FakeExtractor(_doc("texte du pdf")))
    _ = [t async for t in uc.stream(b"x", "", [])]
    assert llm.messages[0].role == "user"
    assert "campagne" in llm.messages[0].content.lower()


def test_fit_pdf_short_text_not_truncated():
    uc = AdaptCampaignUseCase(None, None, max_input_tokens=10000)
    text, truncated = uc._fit_pdf_to_budget("court texte", "brief")
    assert truncated is False
    assert text == "court texte"


def test_fit_pdf_long_text_is_truncated():
    uc = AdaptCampaignUseCase(None, None, max_input_tokens=2100)
    long_text = "mot " * 5000
    text, truncated = uc._fit_pdf_to_budget(long_text, "")
    assert truncated is True
    assert len(text) < len(long_text)
