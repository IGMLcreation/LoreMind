"""Tests des parseurs robustes des use cases d'import (méthodes statiques).

_parse_payload (campagne), _parse_sections / _parse_anchors (règles) : transforment
la réponse brute du LLM en structure exploitable + un drapeau « tronqué » qui
déclenche le re-découpage.
"""
from __future__ import annotations

from app.application.import_campaign import ImportCampaignUseCase
from app.application.import_rules import ImportRulesUseCase

_parse_payload = ImportCampaignUseCase._parse_payload
_parse_sections = ImportRulesUseCase._parse_sections
_parse_anchors = ImportRulesUseCase._parse_anchors


# --- campagne : _parse_payload -------------------------------------------------

def test_parse_payload_valid():
    payload, truncated = _parse_payload('{"arcs":[{"name":"A"}],"npcs":[{"name":"N"}]}', index=0)
    assert truncated is False
    assert payload == {"arcs": [{"name": "A"}], "npcs": [{"name": "N"}]}


def test_parse_payload_truncated_flags_recut():
    payload, truncated = _parse_payload('{"arcs":[{"name":"A"', index=0)
    assert truncated is True
    assert payload == {"arcs": [], "npcs": []}


def test_parse_payload_prose_is_empty_not_truncated():
    payload, truncated = _parse_payload('juste de la prose sans json', index=0)
    assert truncated is False
    assert payload == {"arcs": [], "npcs": []}


def test_parse_payload_recovers_truncated_array():
    raw = '{"arcs":[{"name":"A"},{"name":"B"},{"name":'
    payload, truncated = _parse_payload(raw, index=0)
    assert truncated is True
    assert payload == {"arcs": [{"name": "A"}, {"name": "B"}], "npcs": []}


def test_parse_payload_coerces_non_list_fields():
    payload, _ = _parse_payload('{"arcs":"oops","npcs":null}', index=0)
    assert payload == {"arcs": [], "npcs": []}


# --- règles : _parse_sections --------------------------------------------------

def test_parse_sections_valid_and_normalized():
    sections, truncated = _parse_sections('{"sections":{"Combat":"texte"}}', index=0)
    assert truncated is False
    assert sections == {"Combat": "texte"}


def test_parse_sections_truncated():
    sections, truncated = _parse_sections('{"Combat":"texte non termin', index=0)
    assert truncated is True
    assert sections == {}


def test_parse_sections_prose_is_empty():
    sections, truncated = _parse_sections('pas de json ici', index=0)
    assert truncated is False
    assert sections == {}


# --- règles (mode segmentation) : _parse_anchors -------------------------------

def test_parse_anchors_locates_and_splits_text():
    text = "Préambule.\nLE COMBAT commence ici, brutal.\nLA MAGIE ensuite, subtile."
    raw = ('{"sections":[{"titre":"Combat","debut":"LE COMBAT commence"},'
           '{"titre":"Magie","debut":"LA MAGIE ensuite"}]}')
    sections, truncated = _parse_anchors(raw, text, index=0)
    assert truncated is False
    assert "Combat" in sections and "Magie" in sections
    # La 1re section absorbe le préambule (avant la 1re ancre).
    assert "Préambule." in sections["Combat"]
    assert "LE COMBAT commence ici, brutal." in sections["Combat"]
    assert "LA MAGIE ensuite, subtile." in sections["Magie"]


def test_parse_anchors_unparseable_returns_empty():
    sections, truncated = _parse_anchors("pas du json", "texte", index=0)
    assert sections == {}
    assert truncated is False


def test_parse_anchors_anchor_not_found_is_dropped():
    text = "Seulement ce paragraphe existe."
    raw = '{"sections":[{"titre":"Fantôme","debut":"ancre absente du texte"}]}'
    sections, _ = _parse_anchors(raw, text, index=0)
    # Aucune ancre localisée → aucune section.
    assert sections == {}
