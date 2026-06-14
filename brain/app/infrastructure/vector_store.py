"""Stockage vectoriel fichier (RAG des notebooks) — sans dépendance lourde.

Chaque SOURCE est persistée en un fichier JSON sur le volume `data/` du Brain :
  data/notebooks/{source_id}.json = {"dim": N, "chunks": [{"text":..., "vector":[...]}]}

À l'échelle d'un livre (quelques centaines d'extraits), une recherche cosinus en
Python pur est instantanée — inutile d'ajouter numpy/pgvector/une base vectorielle.
Les fichiers sont mis en cache mémoire (invalidation par mtime) : le coûteux est
le re-parse JSON des vecteurs, pas le cosinus.

Recherche HYBRIDE : score = cosinus + bonus lexical (mots significatifs de la
question présents dans l'extrait). Sur du JdR, les requêtes sont souvent des noms
propres exacts (« Strahd », « Barovia ») où le lexical bat l'embedding.
"""
from __future__ import annotations

import json
import math
import re
from pathlib import Path

_STORE_DIR = Path("data/notebooks")
_SAFE_ID = re.compile(r"[^A-Za-z0-9_-]")

# Cache mémoire {source_id: (mtime_ns, chunks)} — évite de relire/re-parser le JSON
# (vecteurs = gros) à chaque question. Invalidé si le fichier change (mtime).
_CACHE: dict[str, tuple[int, list[dict]]] = {}
_CACHE_MAX_SOURCES = 32  # garde-fou mémoire : ~10 Mo par gros livre en cache

# Poids du bonus lexical dans le score hybride. Le cosinus reste dominant ; le
# bonus (0..0.15) sert surtout à départager / repêcher les correspondances exactes.
_LEX_WEIGHT = 0.15
_WORD_RE = re.compile(r"[a-z0-9àâäçéèêëîïôöùûüœæ]{3,}")
# Mots-outils FR/EN fréquents (≥3 lettres) : sans eux, le bonus lexical serait
# dominé par « les », « pour », « the »… au lieu des termes porteurs de sens.
_STOPWORDS = frozenset({
    "les", "des", "une", "est", "son", "ses", "aux", "par", "pour", "dans",
    "sur", "avec", "qui", "que", "quoi", "dont", "mais", "comme", "plus",
    "pas", "tout", "tous", "toute", "toutes", "ils", "elles", "leur", "leurs",
    "nous", "vous", "cette", "ces", "cet", "ont", "sont", "fait", "etre",
    "être", "avoir", "peut", "quel", "quelle", "quels", "quelles", "ainsi",
    "the", "and", "for", "with", "this", "that", "are", "was", "has", "have",
    "not", "you", "his", "her", "its", "they", "them", "from", "what", "which",
})


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
    _CACHE.pop(source_id, None)  # le mtime suffirait, mais soyons explicites
    return len(chunks)


def exists(source_id: str) -> bool:
    return _path(source_id).exists()


def delete(source_id: str) -> None:
    _CACHE.pop(source_id, None)
    _path(source_id).unlink(missing_ok=True)
    _summaries_path(source_id).unlink(missing_ok=True)


# --- Index de résumés (analyse approfondie) ----------------------------------
# Cache disque des résumés PAR LOT d'une source : construit paresseusement à la
# première analyse approfondie, réutilisé ensuite pour ne relire que les lots
# pertinents. Invalidé avec la source (delete) et si batch_tokens change.


def _summaries_path(source_id: str) -> Path:
    safe = _SAFE_ID.sub("_", str(source_id))
    return _STORE_DIR / f"{safe}.summaries.json"


def save_summaries(source_id: str, batch_tokens: int, entries: list[dict]) -> None:
    """Persiste les résumés de lots ({"summary": str, "vector": [...]})."""
    _STORE_DIR.mkdir(parents=True, exist_ok=True)
    payload = {"batch_tokens": int(batch_tokens), "entries": entries}
    _summaries_path(source_id).write_text(
        json.dumps(payload, ensure_ascii=False), encoding="utf-8")


def load_summaries(source_id: str, batch_tokens: int) -> list[dict] | None:
    """Résumés de lots d'une source, ou None si absents / construits avec une
    autre taille de lot (le découpage ne correspondrait plus)."""
    p = _summaries_path(source_id)
    if not p.exists():
        return None
    try:
        data = json.loads(p.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None
    if not isinstance(data, dict) or data.get("batch_tokens") != int(batch_tokens):
        return None
    entries = data.get("entries")
    return entries if isinstance(entries, list) else None


def _load(source_id: str) -> list[dict]:
    p = _path(source_id)
    try:
        mtime = p.stat().st_mtime_ns
    except OSError:
        _CACHE.pop(source_id, None)
        return []
    cached = _CACHE.get(source_id)
    if cached is not None and cached[0] == mtime:
        return cached[1]
    try:
        data = json.loads(p.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return []
    chunks = data.get("chunks", []) if isinstance(data, dict) else []
    if len(_CACHE) >= _CACHE_MAX_SOURCES:
        _CACHE.pop(next(iter(_CACHE)))  # éviction FIFO simple
    _CACHE[source_id] = (mtime, chunks)
    return chunks


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


def _significant_words(text: str) -> frozenset[str]:
    """Mots porteurs de sens d'un texte (minuscules, ≥3 lettres, hors mots-outils)."""
    return frozenset(w for w in _WORD_RE.findall(text.lower()) if w not in _STOPWORDS)


def _chunk_words(chunk: dict) -> frozenset[str]:
    """Mots significatifs d'un extrait, mémoïsés sur le dict caché (calculés à la
    1ère recherche, réutilisés tant que la source reste en cache)."""
    words = chunk.get("_words")
    if words is None:
        words = _significant_words(chunk.get("text", ""))
        chunk["_words"] = words
    return words


# Alias public du cosinus (réutilisé par l'index de résumés de l'analyse
# approfondie — même métrique que la recherche).
cosine_similarity = _cosine


def search(
    source_ids: list[str],
    query_vector: list[float],
    top_k: int = 6,
    query_text: str = "",
    min_score: float = 0.0,
) -> list[dict]:
    """Renvoie les `top_k` extraits les plus proches, toutes sources confondues.

    Score HYBRIDE : cosinus + `_LEX_WEIGHT` × (part des mots significatifs de
    `query_text` présents dans l'extrait). Les extraits dont le cosinus est sous
    `min_score` sont écartés (peut donc renvoyer MOINS de `top_k` résultats —
    mieux vaut aucun extrait que du bruit injecté dans le prompt).

    Chaque résultat : {"text": str, "score": float, "source_id": str, "page": int|None}.
    """
    query_words = _significant_words(query_text) if query_text else frozenset()
    scored: list[dict] = []
    for sid in source_ids:
        for chunk in _load(sid):
            vector = chunk.get("vector") or []
            cos = _cosine(query_vector, vector)
            if cos < min_score:
                continue
            score = cos
            if query_words:
                overlap = len(query_words & _chunk_words(chunk)) / len(query_words)
                score += _LEX_WEIGHT * overlap
            scored.append({
                "text": chunk.get("text", ""),
                "score": score,
                "source_id": sid,
                "page": chunk.get("page"),
            })
    scored.sort(key=lambda c: c["score"], reverse=True)
    return scored[:top_k]
