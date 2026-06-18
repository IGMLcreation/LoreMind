"""Tests des use cases d'import via FAKES (ports LLM + extracteur PDF).

Exerce la chaîne map-reduce complète (extraction → chunking → MAP → REDUCE →
streaming d'événements) SANS réseau ni vrai PDF. `chunk_text` est monkeypatché
pour un découpage déterministe (le chunking est testé à part). `asyncio.sleep`
est neutralisé pour que les backoffs de retry n'imposent aucune attente.
"""
from __future__ import annotations

import pytest

from app.application.import_campaign import ImportCampaignUseCase
from app.application.import_rules import ImportRulesUseCase
from app.domain.models import ExtractedDocument, ExtractedPage
from app.domain.ports import LLMProviderError


# --- fakes ---------------------------------------------------------------------

class FakeExtractor:
    def __init__(self, doc: ExtractedDocument) -> None:
        self._doc = doc

    def extract(self, pdf_bytes: bytes) -> ExtractedDocument:
        return self._doc


class ScriptedLLM:
    """Rejoue une réponse par appel (la dernière est répétée si on dépasse)."""

    def __init__(self, responses: list) -> None:
        self._responses = list(responses)
        self.calls = 0

    async def generate(self, prompt: str, *, output_format=None, temperature=None) -> str:
        r = self._responses[min(self.calls, len(self._responses) - 1)]
        self.calls += 1
        if isinstance(r, Exception):
            raise r
        return r


class ContentLLM:
    """Répond selon le CONTENU du prompt (chunk) : (sous-chaîne → réponse/exception)."""

    def __init__(self, rules: list) -> None:
        self._rules = rules

    async def generate(self, prompt: str, *, output_format=None, temperature=None) -> str:
        for sub, r in self._rules:
            if sub in prompt:
                if isinstance(r, Exception):
                    raise r
                return r
        raise AssertionError(f"aucune règle ContentLLM ne matche : {prompt[:60]!r}")


def _doc(text: str = "Texte du PDF.", *, ocr: bool = False) -> ExtractedDocument:
    return ExtractedDocument(pages=[ExtractedPage(index=0, text=text, used_ocr=ocr)])


@pytest.fixture
def no_sleep(monkeypatch):
    async def _noop(_d):
        return None
    monkeypatch.setattr("asyncio.sleep", _noop)


@pytest.fixture
def one_chunk(monkeypatch):
    monkeypatch.setattr("app.application.import_rules.chunk_text", lambda *a, **k: ["chunk"])
    monkeypatch.setattr("app.application.import_campaign.chunk_text", lambda *a, **k: ["chunk"])


# --- import de règles ----------------------------------------------------------

async def test_rules_execute_returns_merged_sections(one_chunk):
    llm = ScriptedLLM(['{"Combat":"## Combat\\nrègles de combat"}'])
    uc = ImportRulesUseCase(llm, FakeExtractor(_doc(ocr=True)))
    result = await uc.execute(b"pdf")
    assert result.sections == {"Combat": "## Combat\nrègles de combat"}
    assert result.page_count == 1
    assert result.ocr_page_count == 1


async def test_rules_stream_emits_extracting_start_progress_done(one_chunk):
    llm = ScriptedLLM(['{"Magie":"sorts"}'])
    uc = ImportRulesUseCase(llm, FakeExtractor(_doc()))
    events = [e async for e in uc.stream(b"pdf")]
    types = [e["type"] for e in events]
    assert types[0] == "extracting"
    assert types[1] == "start"
    assert "progress" in types
    done = events[-1]
    assert done["type"] == "done"
    assert done["sections"] == {"Magie": "sorts"}


async def test_rules_stream_skips_failed_chunk_but_continues(monkeypatch, no_sleep):
    monkeypatch.setattr("app.application.import_rules.chunk_text",
                        lambda *a, **k: ["AAA premier", "BBB second"])
    llm = ContentLLM([
        ("AAA premier", LLMProviderError("HTTP 503 saturé")),
        ("BBB second", '{"Magie":"sorts"}'),
    ])
    uc = ImportRulesUseCase(llm, FakeExtractor(_doc()))
    events = [e async for e in uc.stream(b"pdf")]
    types = [e["type"] for e in events]
    assert "chunk_failed" in types
    done = events[-1]
    assert done["type"] == "done"
    assert done["sections"] == {"Magie": "sorts"}
    assert done["skipped"] == 1


async def test_rules_stream_all_chunks_fail_emits_error(one_chunk, no_sleep):
    llm = ScriptedLLM([LLMProviderError("HTTP 500 panne")])
    uc = ImportRulesUseCase(llm, FakeExtractor(_doc()))
    events = [e async for e in uc.stream(b"pdf")]
    assert events[-1]["type"] == "error"
    assert "échoué" in events[-1]["message"]


# --- import de campagne --------------------------------------------------------

_TREE = ('{"arcs":[{"name":"Acte I","description":"intro",'
         '"chapters":[{"name":"Ch1","scenes":[{"name":"Sc1"}]}]}],'
         '"npcs":[{"name":"Gandalf","description":"magicien"}]}')


async def test_campaign_execute_builds_tree_and_npcs(one_chunk):
    uc = ImportCampaignUseCase(ScriptedLLM([_TREE]), FakeExtractor(_doc()))
    result = await uc.execute(b"pdf")
    assert result.counts() == (1, 1, 1)
    assert result.arcs[0].name == "Acte I"
    assert result.arcs[0].chapters[0].scenes[0].name == "Sc1"
    assert [n.name for n in result.npcs] == ["Gandalf"]


async def test_campaign_stream_emits_done_with_serialized_tree(one_chunk):
    uc = ImportCampaignUseCase(ScriptedLLM([_TREE]), FakeExtractor(_doc()))
    events = [e async for e in uc.stream(b"pdf")]
    types = [e["type"] for e in events]
    assert types[0] == "extracting"
    assert types[1] == "start"
    assert "progress" in types
    done = events[-1]
    assert done["type"] == "done"
    assert done["arcs"][0]["name"] == "Acte I"
    assert done["arcs"][0]["chapters"][0]["scenes"][0]["name"] == "Sc1"
    assert done["npcs"] == [{"name": "Gandalf", "description": "magicien"}]


async def test_campaign_stream_all_fail_emits_error(one_chunk, no_sleep):
    uc = ImportCampaignUseCase(ScriptedLLM([LLMProviderError("502")]), FakeExtractor(_doc()))
    events = [e async for e in uc.stream(b"pdf")]
    assert events[-1]["type"] == "error"
