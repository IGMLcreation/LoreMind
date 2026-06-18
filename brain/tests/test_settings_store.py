"""Tests des overrides runtime persistés (app.core.settings_store).

Le chemin du fichier est redirigé vers un tmp_path pour isoler chaque test.
"""
from __future__ import annotations

import json
from pathlib import Path

import pytest

from app.core import settings_store


@pytest.fixture(autouse=True)
def isolated_store(tmp_path, monkeypatch):
    monkeypatch.setattr(settings_store, "_OVERRIDES_PATH", tmp_path / "settings.json")
    return tmp_path / "settings.json"


def test_load_missing_file_returns_empty():
    assert settings_store.load_overrides() == {}


def test_save_filters_to_allowlist_and_persists(isolated_store):
    result = settings_store.save_overrides({
        "llm_model": "gemma3:12b",
        "internal_shared_secret": "HACK",   # hors allow-list → ignoré
        "champ_inconnu": "x",               # hors allow-list → ignoré
    })
    assert result == {"llm_model": "gemma3:12b"}
    on_disk = json.loads(Path(isolated_store).read_text(encoding="utf-8"))
    assert on_disk == {"llm_model": "gemma3:12b"}


def test_save_merges_with_existing():
    settings_store.save_overrides({"llm_model": "a"})
    merged = settings_store.save_overrides({"llm_provider": "ollama"})
    assert merged == {"llm_model": "a", "llm_provider": "ollama"}


def test_load_ignores_non_allowlisted_keys_on_disk(isolated_store):
    Path(isolated_store).write_text(
        json.dumps({"llm_model": "ok", "internal_shared_secret": "leak"}),
        encoding="utf-8",
    )
    assert settings_store.load_overrides() == {"llm_model": "ok"}


def test_load_corrupted_file_returns_empty(isolated_store):
    Path(isolated_store).write_text("{ pas du json", encoding="utf-8")
    assert settings_store.load_overrides() == {}


def test_load_non_dict_json_returns_empty(isolated_store):
    Path(isolated_store).write_text("[1, 2, 3]", encoding="utf-8")
    assert settings_store.load_overrides() == {}
