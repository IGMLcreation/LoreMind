"""Tests du use case de génération de page (app.application.generate_page)."""
from __future__ import annotations

import pytest

from app.application.generate_page import GeneratePageUseCase
from app.domain.models import PageGenerationContext
from app.domain.ports import LLMProviderError

_CTX = PageGenerationContext(
    lore_name="Eldoria",
    folder_name="PNJ",
    template_name="Personnage",
    template_fields=["apparence", "histoire"],
    page_title="Aragorn",
    lore_description="un monde sombre",
)


def test_build_prompt_includes_context_and_fields():
    p = GeneratePageUseCase._build_prompt(_CTX, "fr")
    assert "Eldoria" in p
    assert "Aragorn" in p
    assert '"apparence"' in p
    assert "un monde sombre" in p


def test_build_prompt_omits_lore_description_when_absent():
    ctx = PageGenerationContext("L", "F", "T", ["a"], "Titre", None)
    assert "Description de l'univers" not in GeneratePageUseCase._build_prompt(ctx)


def test_parse_values_keeps_only_expected_fields():
    out = GeneratePageUseCase._parse_values(
        '{"apparence":"grand","histoire":"longue","extra":"ignoré"}',
        ["apparence", "histoire"])
    assert out == {"apparence": "grand", "histoire": "longue"}


def test_parse_values_missing_field_becomes_empty_string():
    out = GeneratePageUseCase._parse_values('{"apparence":"grand"}', ["apparence", "histoire"])
    assert out == {"apparence": "grand", "histoire": ""}


def test_parse_values_casts_to_str_and_strips():
    out = GeneratePageUseCase._parse_values('{"n": 42, "s": "  x  "}', ["n", "s"])
    assert out == {"n": "42", "s": "x"}


def test_parse_values_bad_json_raises():
    with pytest.raises(LLMProviderError):
        GeneratePageUseCase._parse_values("pas du json", ["a"])


def test_parse_values_non_object_raises():
    with pytest.raises(LLMProviderError):
        GeneratePageUseCase._parse_values("[1, 2]", ["a"])


async def test_execute_returns_filtered_result():
    class FakeLLM:
        async def generate(self, prompt, *, output_format=None, temperature=None):
            return '{"apparence":"grand","histoire":"épique","parasite":"x"}'
    result = await GeneratePageUseCase(FakeLLM()).execute(_CTX)
    assert result.values == {"apparence": "grand", "histoire": "épique"}
