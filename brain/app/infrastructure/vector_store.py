"""Stockage vectoriel fichier (RAG des notebooks) — sans dépendance lourde.

Chaque SOURCE est persistée en un fichier JSON sur le volume `data/` du Brain :
  data/notebooks/{source_id}.json = {"dim": N, "chunks": [{"text":..., "vector":[...]}]}

À l'échelle d'un livre (quelques centaines d'extraits), une recherche cosinus en
Python pur est instantanée — inutile d'ajouter numpy/pgvector/une base vectorielle.
"""
from __future__ import annotations

import json
import math
import re
from pathlib import Path

_STORE_DIR = Path("data/notebooks")
_SAFE_ID = re.compile(r"[^A-Za-z0-9_-]")


def _path(source_id: str) -> Path:
    safe = _SAFE_ID.sub("_", str(source_id))
    return _STORE_DIR / f"{safe}.json"


def save(
    source_id: str,
    chunks: list[str],
    vectors: list[list[float]],
    pages: list[int] | None = None,
) -> int:
    """Persiste les (chunk, vecteur[, page]) d'une source. Renvoie le nb d'extraits."""
    if len(chunks) != len(vectors):
        raise ValueError("chunks et vectors de tailles différentes")
    if pages is not None and len(pages) != len(chunks):
        raise ValueError("pages et chunks de tailles différentes")
    _STORE_DIR.mkdir(parents=True, exist_ok=True)
    items = []
    for i, (c, v) in enumerate(zip(chunks, vectors)):
        item = {"text": c, "vector": v}
        if pages is not None:
            item["page"] = pages[i]
        items.append(item)
    payload = {"dim": len(vectors[0]) if vectors else 0, "chunks": items}
    _path(source_id).write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
    return len(chunks)


def exists(source_id: str) -> bool:
    return _path(source_id).exists()


def delete(source_id: str) -> None:
    _path(source_id).unlink(missing_ok=True)


def _load(source_id: str) -> list[dict]:
    p = _path(source_id)
    if not p.exists():
        return []
    try:
        data = json.loads(p.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return []
    return data.get("chunks", []) if isinstance(data, dict) else []


def all_chunks(source_id: str) -> list[dict]:
    """Tous les extraits d'une source (texte + page), sans vecteurs — pour le mode
    « analyse approfondie » (map-reduce sur tout le document)."""
    return [{"text": c.get("text", ""), "page": c.get("page")} for c in _load(source_id)]


def _cosine(a: list[float], b: list[float]) -> float:
    if not a or not b or len(a) != len(b):
        return 0.0
    dot = 0.0
    na = 0.0
    nb = 0.0
    for x, y in zip(a, b):
        dot += x * y
        na += x * x
        nb += y * y
    if na == 0.0 or nb == 0.0:
        return 0.0
    return dot / (math.sqrt(na) * math.sqrt(nb))


def search(
    source_ids: list[str],
    query_vector: list[float],
    top_k: int = 6,
) -> list[dict]:
    """Renvoie les `top_k` extraits les plus proches, toutes sources confondues.

    Chaque résultat : {"text": str, "score": float, "source_id": str}.
    """
    scored: list[dict] = []
    for sid in source_ids:
        for chunk in _load(sid):
            vector = chunk.get("vector") or []
            score = _cosine(query_vector, vector)
            scored.append({
                "text": chunk.get("text", ""),
                "score": score,
                "source_id": sid,
                "page": chunk.get("page"),
            })
    scored.sort(key=lambda c: c["score"], reverse=True)
    return scored[:top_k]
