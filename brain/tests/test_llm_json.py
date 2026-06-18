"""Tests de la lecture robuste de JSON depuis une réponse LLM (app.application.llm_json).

Couvre l'extraction du premier objet équilibré (en ignorant les accolades dans
les chaînes), la réparation d'un JSON tronqué, la détection « ça ressemble à du
JSON coupé », et le strip des blocs de raisonnement <think>…</think>.
"""
from __future__ import annotations

from app.application.llm_json import (
    extract_json_object,
    load_json_object,
    looks_like_truncated_json,
    repair_truncated_json,
)


# --- extract_json_object -------------------------------------------------------

def test_extract_simple_object():
    assert extract_json_object('{"a": 1}') == '{"a": 1}'


def test_extract_ignores_surrounding_prose_and_fences():
    raw = 'Voici le JSON :\n```json\n{"a": 1}\n```\nMerci.'
    assert extract_json_object(raw) == '{"a": 1}'


def test_extract_stops_at_first_balanced_object():
    assert extract_json_object('{"a": 1} et puis {"b": 2}') == '{"a": 1}'


def test_extract_keeps_nested_object_whole():
    assert extract_json_object('{"a": {"b": 1}}') == '{"a": {"b": 1}}'


def test_extract_ignores_braces_inside_strings():
    raw = '{"a": "}{ pas du json "}'
    assert extract_json_object(raw) == raw


def test_extract_handles_escaped_quote_in_string():
    raw = '{"a": "x\\"y"}'
    assert extract_json_object(raw) == raw


def test_extract_returns_none_when_unclosed():
    assert extract_json_object('{"a": 1') is None


def test_extract_returns_none_without_brace():
    assert extract_json_object('aucune accolade ici') is None


def test_extract_returns_none_on_empty():
    assert extract_json_object('') is None


# --- load_json_object ----------------------------------------------------------

def test_load_valid_object_not_recovered():
    obj, recovered = load_json_object('{"x": 42}')
    assert obj == {"x": 42}
    assert recovered is False


def test_load_tolerates_raw_control_chars_in_strings():
    # Retour à la ligne BRUT dans une chaîne : invalide en strict, accepté ici.
    obj, recovered = load_json_object('{"a": "ligne1\nligne2"}')
    assert obj == {"a": "ligne1\nligne2"}
    assert recovered is False


def test_load_strips_reasoning_block_before_parsing():
    raw = '<think>je réfléchis { ] [ }</think>{"ok": true}'
    obj, recovered = load_json_object(raw)
    assert obj == {"ok": True}
    assert recovered is False


def test_load_repairs_truncated_array_and_flags_recovered():
    raw = '{"items": [{"a": 1}, {"b": 2}, {"c":'
    obj, recovered = load_json_object(raw)
    assert obj == {"items": [{"a": 1}, {"b": 2}]}
    assert recovered is True


def test_load_returns_none_on_garbage():
    obj, recovered = load_json_object('juste de la prose sans json')
    assert obj is None
    assert recovered is False


# --- looks_like_truncated_json -------------------------------------------------

def test_truncated_detection_no_brace_is_false():
    assert looks_like_truncated_json('rien') is False


def test_truncated_detection_short_object_start_unbalanced_is_true():
    # Démarre par '{' et déséquilibré → coupé net, même très court.
    assert looks_like_truncated_json('{"') is True


def test_truncated_detection_balanced_object_is_false():
    assert looks_like_truncated_json('{"a": 1}') is False


def test_truncated_detection_short_prose_with_braces_is_false():
    assert looks_like_truncated_json('texte { incomplet') is False


def test_truncated_detection_long_prose_unbalanced_is_true():
    raw = 'prose ' * 30 + '{ structure ouverte mais jamais refermée'
    assert len(raw) >= 100
    assert looks_like_truncated_json(raw) is True


# --- repair_truncated_json -----------------------------------------------------

def test_repair_closes_open_containers_after_last_complete_element():
    repaired = repair_truncated_json('{"items": [{"a": 1}, {"b": 2}, {"c":')
    assert repaired == '{"items": [{"a": 1}, {"b": 2}]}'


def test_repair_returns_none_when_nothing_complete():
    assert repair_truncated_json('{"a": "jamais fermé') is None


def test_repair_returns_none_without_brace():
    assert repair_truncated_json('pas de json') is None
