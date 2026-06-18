"""Tests du découpage de texte (app.application.chunking).

Vérifie le découpage par paragraphes vers une cible de tokens, le découpage des
paragraphes géants, le recouvrement (overlap), et le split_in_half du repli
anti-troncature. tiktoken (cl100k_base) est déterministe → assertions stables.
"""
from __future__ import annotations

from app.application.chunking import chunk_text, split_in_half


def test_empty_text_returns_no_chunk():
    assert chunk_text("") == []
    assert chunk_text("   \n\n  ") == []


def test_short_text_stays_single_chunk():
    chunks = chunk_text("Paragraphe un.\n\nParagraphe deux.", target_tokens=1000)
    assert len(chunks) == 1
    assert "Paragraphe un." in chunks[0]
    assert "Paragraphe deux." in chunks[0]


def test_splits_into_several_chunks_when_exceeding_target():
    paras = [f"Paragraphe numero {i} avec un peu de contenu." for i in range(20)]
    full = "\n\n".join(paras)
    chunks = chunk_text(full, target_tokens=20)
    assert len(chunks) > 1
    # Aucun paragraphe perdu : tous présents quelque part.
    joined = "\n\n".join(chunks)
    for p in paras:
        assert p in joined


def test_oversized_single_paragraph_is_split():
    # Un seul paragraphe (aucun "\n\n") plus gros que la cible → plusieurs sous-blocs.
    huge = "mot " * 500
    chunks = chunk_text(huge, target_tokens=50)
    assert len(chunks) > 1


def test_overlap_repeats_content_without_losing_paragraphs():
    paras = [f"Bloc {i} de texte distinct." for i in range(12)]
    full = "\n\n".join(paras)
    chunks = chunk_text(full, target_tokens=20, overlap_tokens=10)
    assert len(chunks) > 1
    joined = "\n\n".join(chunks)
    for p in paras:
        assert p in joined


# --- split_in_half -------------------------------------------------------------

def test_split_in_half_too_short_returns_empty():
    assert split_in_half("court") == ("", "")


def test_split_in_half_splits_on_newline_near_middle():
    text = "A" * 300 + "\n" + "B" * 300
    left, right = split_in_half(text)
    assert left and right
    assert left.startswith("A")
    assert right.startswith("B")


def test_split_in_half_halves_cover_all_content():
    text = "\n".join(f"ligne {i} " + "x" * 20 for i in range(40))
    left, right = split_in_half(text)
    assert left and right
    # Le découpage ne perd rien : la concaténation contient début et fin.
    assert "ligne 0" in left
    assert "ligne 39" in right
