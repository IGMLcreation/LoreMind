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


def chunk_text(
    full_text: str,
    target_tokens: int = CHUNK_TARGET_TOKENS,
    overlap_tokens: int = 0,
) -> list[str]:
    """Découpe `full_text` en morceaux ~`target_tokens` tokens (frontières de §).

    `overlap_tokens` > 0 : chaque morceau reprend la fin du précédent (les derniers
    paragraphes, jusqu'à ~`overlap_tokens` tokens). Utile pour le RAG : une phrase-clé
    à cheval sur deux morceaux reste retrouvable dans au moins l'un des deux. À
    laisser à 0 pour les imports (recopie) : un overlap y DUPLIQUERAIT du texte.
    Un morceau peut légèrement dépasser la cible (jusqu'à target + overlap).
    """
    if not full_text.strip():
        return []

    import tiktoken

    enc = tiktoken.get_encoding("cl100k_base")
    paragraphs = [p for p in full_text.split("\n\n") if p.strip()]

    chunks: list[str] = []
    current: list[str] = []
    current_tokens = 0
    fresh = False  # `current` contient-il du contenu pas encore émis ? (évite de
    # ré-émettre un morceau composé uniquement de l'overlap en fin de texte)
    for para in paragraphs:
        para_tokens = len(enc.encode(para))
        # Un paragraphe seul plus gros que la cible : on le coupe en sous-blocs.
        if para_tokens > target_tokens:
            if current and fresh:
                chunks.append("\n\n".join(current))
            current, current_tokens, fresh = [], 0, False
            chunks.extend(_split_oversized(para, enc, target_tokens, overlap_tokens))
            continue
        if current_tokens + para_tokens > target_tokens and current:
            if fresh:
                chunks.append("\n\n".join(current))
            current, current_tokens = _overlap_tail(current, enc, overlap_tokens)
            fresh = False
        current.append(para)
        current_tokens += para_tokens
        fresh = True

    if current and fresh:
        chunks.append("\n\n".join(current))
    return chunks


def _overlap_tail(parts: list[str], enc, overlap_tokens: int) -> tuple[list[str], int]:
    """Derniers paragraphes de `parts` totalisant au plus `overlap_tokens` tokens —
    le « rappel » recopié en tête du morceau suivant."""
    if overlap_tokens <= 0 or not parts:
        return [], 0
    tail: list[str] = []
    total = 0
    for para in reversed(parts):
        para_tokens = len(enc.encode(para))
        if total + para_tokens > overlap_tokens:
            break
        tail.insert(0, para)
        total += para_tokens
    if not tail:
        # Aucun paragraphe entier ne tient dans le budget (paragraphes longs) :
        # on reprend la FIN du dernier paragraphe pour garantir le recouvrement.
        tokens = enc.encode(parts[-1])
        tail = [enc.decode(tokens[-overlap_tokens:])]
        total = min(overlap_tokens, len(tokens))
    return tail, total


def _split_oversized(paragraph: str, enc, target_tokens: int, overlap_tokens: int = 0) -> list[str]:
    """Coupe un paragraphe géant en sous-blocs ~`target_tokens` tokens (fenêtre
    glissante avec recouvrement si `overlap_tokens` > 0)."""
    tokens = enc.encode(paragraph)
    step = max(1, target_tokens - overlap_tokens)
    out: list[str] = []
    i = 0
    while i < len(tokens):
        out.append(enc.decode(tokens[i : i + target_tokens]))
        if i + target_tokens >= len(tokens):
            break
        i += step
    return out


def split_in_half(text: str) -> tuple[str, str]:
    """Coupe `text` en deux moitiés ~égales, de préférence sur un saut de ligne
    proche du milieu (pour ne pas trancher en plein mot/phrase).

    Sert au repli anti-troncature des imports : quand la SORTIE d'un morceau est
    coupée (le modèle ne peut pas tout réécrire en une réponse), on retraite ce
    morceau en deux moitiés. Renvoie ('', '') si le texte est trop court pour
    être découpé utilement (garde-fou anti-récursion infinie).
    """
    text = text.strip()
    if len(text) < 400:
        return "", ""
    mid = len(text) // 2
    # Cherche un saut de ligne juste avant le milieu, sinon juste après.
    cut = text.rfind("\n", 0, mid)
    if cut < len(text) // 4:
        nxt = text.find("\n", mid)
        cut = nxt if nxt != -1 else mid
    left, right = text[:cut].strip(), text[cut:].strip()
    if not left or not right:
        return "", ""
    return left, right
