# -*- coding: utf-8 -*-
"""Sanity check temporaire : overlap du chunking + recherche hybride du vector store."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.application.chunking import chunk_text

# --- 1. Chunking avec overlap ---
paras = [f"Paragraphe {i} : " + ("lorem ipsum dolor sit amet " * 8) for i in range(12)]
text = "\n\n".join(paras)

no_overlap = chunk_text(text, target_tokens=200)
with_overlap = chunk_text(text, target_tokens=200, overlap_tokens=40)

assert len(with_overlap) >= len(no_overlap), "l'overlap ne doit pas réduire le nb de chunks"
# Chaque chunk (sauf le 1er) doit commencer par la fin du précédent
overlapped = 0
for prev, cur in zip(with_overlap, with_overlap[1:]):
    first_para = cur.split("\n\n")[0]
    if first_para in prev:
        overlapped += 1
assert overlapped >= len(with_overlap) - 2, f"overlap absent: {overlapped}/{len(with_overlap)-1}"
# Pas de chunk composé uniquement de l'overlap (dernier chunk dupliqué)
assert with_overlap[-1] != with_overlap[-2], "dernier chunk = pur overlap (dupliqué)"
# overlap_tokens=0 → comportement identique à l'ancien
assert no_overlap == chunk_text(text, target_tokens=200, overlap_tokens=0)
print(f"[OK] chunking : {len(no_overlap)} chunks sans overlap, {len(with_overlap)} avec ({overlapped} recouvrements)")

# --- Paragraphe géant ---
giant = "mot " * 2000
sub = chunk_text(giant, target_tokens=300, overlap_tokens=50)
assert len(sub) > 1
print(f"[OK] paragraphe géant coupé en {len(sub)} sous-blocs")

# --- 2. Vector store : hybride + seuil + cache ---
import tempfile, os
from app.infrastructure import vector_store

with tempfile.TemporaryDirectory() as tmp:
    vector_store._STORE_DIR = Path(tmp)
    chunks = [
        "Strahd von Zarovich règne sur la sombre vallée de Barovia depuis son château.",
        "Les règles de combat utilisent un d20 plus le modificateur de caractéristique.",
        "La taverne du village sert un ragoût de navets aux voyageurs fatigués.",
    ]
    # Vecteurs factices : chunk 0 et 1 proches de la query, chunk 2 orthogonal
    vectors = [[1.0, 0.1, 0.0], [0.9, 0.4, 0.1], [0.0, 0.0, 1.0]]
    vector_store.save("src1", chunks, vectors, pages=[10, 20, 30])

    q = [1.0, 0.2, 0.0]
    # Sans seuil ni texte : 3 résultats, ordre cosinus
    r = vector_store.search(["src1"], q, top_k=10)
    assert len(r) == 3 and r[0]["page"] == 10

    # Avec seuil : le chunk orthogonal (cos~0) est écarté
    r = vector_store.search(["src1"], q, top_k=10, min_score=0.30)
    assert len(r) == 2, f"seuil non appliqué: {len(r)}"
    print(f"[OK] seuil : 2/3 extraits gardés (orthogonal écarté)")

    # Bonus lexical : la query mentionne « Strahd Barovia » → chunk 0 doit dominer
    r = vector_store.search(["src1"], q, top_k=10, query_text="Parle-moi de Strahd et de Barovia", min_score=0.30)
    assert r[0]["text"].startswith("Strahd"), r[0]["text"]
    assert r[0]["score"] > vector_store._cosine(q, vectors[0]), "bonus lexical absent"
    print(f"[OK] hybride : bonus lexical appliqué (score={r[0]['score']:.3f})")

    # Le set "_words" mémoïsé ne doit PAS fuiter dans les résultats
    assert all("_words" not in res for res in r)

    # Cache : 2e recherche sert depuis la mémoire (même objet liste)
    c1 = vector_store._load("src1")
    c2 = vector_store._load("src1")
    assert c1 is c2, "cache mtime inopérant"
    # save() invalide le cache
    vector_store.save("src1", chunks[:1], vectors[:1], pages=[10])
    c3 = vector_store._load("src1")
    assert len(c3) == 1, "cache non invalidé après save"
    # delete() purge cache + fichier
    vector_store.delete("src1")
    assert vector_store._load("src1") == []
    print("[OK] cache mémoire : hit, invalidation save, purge delete")

print("\nTous les sanity checks passent.")
