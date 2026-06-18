"""Tests de la normalisation de langue (app.core.language)."""
from __future__ import annotations

import pytest

from app.core import language


@pytest.mark.parametrize("raw, expected", [
    ("fr", "fr"),
    ("en", "en"),
    ("EN", "en"),
    ("en-US", "en"),
    ("fr-FR,fr;q=0.9,en;q=0.8", "fr"),
    ("en-GB,en;q=0.9", "en"),
    ("de", "fr"),          # non supporté → défaut
    ("", "fr"),
    (None, "fr"),
    ("  EN-gb ", "en"),    # casse + espaces tolérés
])
def test_normalize(raw, expected):
    assert language.normalize(raw) == expected


def test_language_name_known_and_fallback():
    assert language.language_name("fr") == "français"
    assert language.language_name("en") == "anglais"
    # Code inconnu → nom de la langue par défaut.
    assert language.language_name("xx") == "français"


def test_instruction_mentions_target_language():
    assert "anglais" in language.instruction("en")
    assert "français" in language.instruction("fr")


def test_get_user_language_uses_normalize():
    assert language.get_user_language("en-US") == "en"
    assert language.get_user_language(None) == "fr"
