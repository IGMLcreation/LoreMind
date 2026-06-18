"""Tests du stockage vectoriel fichier + recherche hybride (app.infrastructure.vector_store).

Le répertoire de stockage est redirigé vers un tmp_path et le cache mémoire est
vidé avant chaque test pour une isolation totale.
"""
from __future__ import annotations

import pytest

from app.infrastructure import vector_store


@pytest.fixture(autouse=True)
def isolated_store(tmp_path, monkeypatch):
    monkeypatch.setattr(vector_store, "_STORE_DIR", tmp_path)
    vector_store._CACHE.clear()
    yield
    vector_store._CACHE.clear()


# --- cosinus -------------------------------------------------------------------

def test_cosine_identical_is_one():
    assert vector_store._cosine([1.0, 0.0], [2.0, 0.0]) == pytest.approx(1.0)


def test_cosine_orthogonal_is_zero():
    assert vector_store._cosine([1.0, 0.0], [0.0, 1.0]) == 0.0


def test_cosine_mismatched_or_zero_is_zero():
    assert vector_store._cosine([1.0], [1.0, 2.0]) == 0.0
    assert vector_store._cosine([0.0, 0.0], [1.0, 1.0]) == 0.0
    assert vector_store.cosine_similarity([], [1.0]) == 0.0  # alias public


# --- mots significatifs --------------------------------------------------------

def test_significant_words_filters_stopwords_and_short():
    words = vector_store._significant_words("Le dragon DORT dans la caverne avec les gobelins")
    assert "dragon" in words
    assert "caverne" in words
    assert "gobelins" in words
    assert "les" not in words and "avec" not in words and "la" not in words


# --- save / exists / delete ----------------------------------------------------

def test_save_then_exists_and_delete():
    vector_store.save("src1", ["chunk a"], [[1.0, 0.0]])
    assert vector_store.exists("src1") is True
    vector_store.delete("src1")
    assert vector_store.exists("src1") is False


def test_save_rejects_mismatched_lengths():
    with pytest.raises(ValueError):
        vector_store.save("s", ["a", "b"], [[1.0]])
    with pytest.raises(ValueError):
        vector_store.save("s", ["a"], [[1.0]], pages=[1, 2])


def test_all_chunks_returns_text_and_page():
    vector_store.save("s", ["t1", "t2"], [[1.0], [2.0]], pages=[3, 7])
    chunks = vector_store.all_chunks("s")
    assert chunks == [{"text": "t1", "page": 3}, {"text": "t2", "page": 7}]


# --- recherche -----------------------------------------------------------------

def test_search_ranks_by_cosine():
    vector_store.save("s", ["proche", "loin"], [[1.0, 0.0], [0.0, 1.0]])
    results = vector_store.search(["s"], [1.0, 0.0], top_k=2)
    assert [r["text"] for r in results] == ["proche", "loin"]
    assert results[0]["score"] > results[1]["score"]


def test_search_respects_top_k():
    vector_store.save("s", ["a", "b", "c"], [[1.0], [0.9], [0.8]])
    assert len(vector_store.search(["s"], [1.0], top_k=2)) == 2


def test_search_min_score_filters_out_weak_matches():
    vector_store.save("s", ["proche", "orthogonal"], [[1.0, 0.0], [0.0, 1.0]])
    results = vector_store.search(["s"], [1.0, 0.0], top_k=5, min_score=0.5)
    assert [r["text"] for r in results] == ["proche"]


def test_search_lexical_bonus_promotes_exact_term_match():
    # Deux extraits de cosinus IDENTIQUE : le bonus lexical départage celui qui
    # contient le mot exact de la question.
    vector_store.save(
        "s",
        ["Strahd règne sur Barovia", "un texte neutre sans rapport"],
        [[1.0, 0.0], [1.0, 0.0]],
    )
    results = vector_store.search(["s"], [1.0, 0.0], top_k=2, query_text="Strahd")
    assert results[0]["text"] == "Strahd règne sur Barovia"
    assert results[0]["score"] > results[1]["score"]


def test_search_includes_source_id_and_page():
    vector_store.save("livre", ["extrait"], [[1.0]], pages=[42])
    [res] = vector_store.search(["livre"], [1.0], top_k=1)
    assert res["source_id"] == "livre"
    assert res["page"] == 42


# --- résumés (analyse approfondie) ---------------------------------------------

def test_summaries_roundtrip_keyed_by_batch_tokens():
    vector_store.save_summaries("s", 1000, [{"summary": "résumé", "vector": [1.0]}])
    assert vector_store.load_summaries("s", 1000) == [{"summary": "résumé", "vector": [1.0]}]
    # Taille de lot différente → invalidé (le découpage ne correspondrait plus).
    assert vector_store.load_summaries("s", 2000) is None


def test_load_summaries_absent_returns_none():
    assert vector_store.load_summaries("inconnu", 1000) is None
