"""Découpage d'un long texte en morceaux qui tiennent dans la fenêtre LLM.

Partagé par les imports (règles, campagne) : un livre dépasse la fenêtre de
contexte, on le découpe par paragraphes jusqu'à une cible de tokens, en coupant
les paragraphes géants si besoin. Dimensionnement via tiktoken (cl100k_base),
approximation suffisante (±10% vs tokenizer natif).
"""
from __future__ import annotations

# Cible conservatrice : tient dans une fenêtre Ollama (num_ctx 16384) en laissant
# la place au prompt + à la sortie JSON. Les providers à grand contexte (1min.ai)
# le supportent largement.
CHUNK_TARGET_TOKENS = 6000


def chunk_text(full_text: str, target_tokens: int = CHUNK_TARGET_TOKENS) -> list[str]:
    """Découpe `full_text` en morceaux ~`target_tokens` tokens (frontières de §)."""
    if not full_text.strip():
        return []

    import tiktoken

    enc = tiktoken.get_encoding("cl100k_base")
    paragraphs = [p for p in full_text.split("\n\n") if p.strip()]

    chunks: list[str] = []
    current: list[str] = []
    current_tokens = 0
    for para in paragraphs:
        para_tokens = len(enc.encode(para))
        # Un paragraphe seul plus gros que la cible : on le coupe en sous-blocs.
        if para_tokens > target_tokens:
            if current:
                chunks.append("\n\n".join(current))
                current, current_tokens = [], 0
            chunks.extend(_split_oversized(para, enc, target_tokens))
            continue
        if current_tokens + para_tokens > target_tokens and current:
            chunks.append("\n\n".join(current))
            current, current_tokens = [], 0
        current.append(para)
        current_tokens += para_tokens

    if current:
        chunks.append("\n\n".join(current))
    return chunks


def _split_oversized(paragraph: str, enc, target_tokens: int) -> list[str]:
    """Coupe un paragraphe géant en sous-blocs ~`target_tokens` tokens."""
    tokens = enc.encode(paragraph)
    out: list[str] = []
    for i in range(0, len(tokens), target_tokens):
        out.append(enc.decode(tokens[i : i + target_tokens]))
    return out
