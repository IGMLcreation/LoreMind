"""Tests des helpers de l'import de règles (app.application.import_rules) :
_SectionMerger, _normalize_sections, _coerce_markdown, _find_anchor, _combine_sections.
"""
from __future__ import annotations

from app.application.import_rules import (
    _SectionMerger,
    _coerce_markdown,
    _combine_sections,
    _find_anchor,
    _normalize_sections,
)


# --- _SectionMerger ------------------------------------------------------------

def test_section_merger_case_insensitive_and_joins():
    m = _SectionMerger()
    touched = m.add({"Combat": "règle A", "combat": "règle B"})
    assert touched == ["Combat"]            # clé canonique = 1re vue
    res = m.result()
    assert list(res.keys()) == ["Combat"]
    assert res["Combat"] == "règle A\n\nrègle B"


def test_section_merger_skips_empty_title_or_content():
    m = _SectionMerger()
    touched = m.add({"": "x", "Titre": "   ", "Vrai": "contenu"})
    assert touched == ["Vrai"]
    assert m.result() == {"Vrai": "contenu"}


def test_section_merger_accumulates_across_chunks():
    m = _SectionMerger()
    m.add({"Combat": "p1"})
    m.add({"Combat": "p2", "Magie": "sorts"})
    res = m.result()
    assert res["Combat"] == "p1\n\np2"
    assert res["Magie"] == "sorts"


# --- _normalize_sections -------------------------------------------------------

def test_normalize_unwraps_known_envelope():
    assert _normalize_sections({"sections": {"Combat": "x"}}) == {"Combat": "x"}
    assert _normalize_sections({"règles": {"A": "y"}}) == {"A": "y"}


def test_normalize_title_content_schema():
    assert _normalize_sections({"title": "Combat", "content": "texte"}) == {"Combat": "texte"}


def test_normalize_strips_meta_keys():
    assert _normalize_sections({"Combat": "x", "thought": "bla", "notes": "y"}) == {"Combat": "x"}


def test_normalize_passthrough_plain_sections():
    assert _normalize_sections({"A": "1", "B": "2"}) == {"A": "1", "B": "2"}


# --- _coerce_markdown ----------------------------------------------------------

def test_coerce_markdown_string_passthrough():
    assert _coerce_markdown("texte") == "texte"


def test_coerce_markdown_none_is_empty():
    assert _coerce_markdown(None) == ""


def test_coerce_markdown_list_joined():
    assert _coerce_markdown(["a", "b"]) == "a\n\nb"


def test_coerce_markdown_dict_flattened():
    out = _coerce_markdown({"Sous-titre": "contenu"})
    assert "Sous-titre" in out
    assert "contenu" in out


# --- _find_anchor --------------------------------------------------------------

def test_find_anchor_exact():
    text = "Chapitre 1. Le héros entre."
    assert _find_anchor(text, "Le héros entre", 0) == text.index("Le héros entre")


def test_find_anchor_whitespace_flexible():
    text = "Le   héros\nentre dans la taverne."
    # Espaces multiples / saut de ligne dans le texte source, anchor normalisé.
    assert _find_anchor(text, "Le héros entre dans la taverne", 0) is not None


def test_find_anchor_case_insensitive():
    assert _find_anchor("LE DONJON s'ouvre", "le donjon", 0) is not None


def test_find_anchor_not_found():
    assert _find_anchor("texte quelconque", "introuvable xyz", 0) is None


# --- _combine_sections ---------------------------------------------------------

def test_combine_sections_case_insensitive_concat():
    out = _combine_sections({"Combat": "p1"}, {"combat": "p2", "Magie": "sorts"})
    assert out["Combat"] == "p1\n\np2"
    assert out["Magie"] == "sorts"
