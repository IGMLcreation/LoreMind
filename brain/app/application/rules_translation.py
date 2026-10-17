"""Règles en deux langues : détection de la langue d'un livre de règles et traduction de
ses sections (titre + contenu markdown) vers l'autre langue de l'application.

L'extraction reste FIDÈLE au livre (langue d'origine) ; la traduction produit la seconde
version. Les noms propres (lieux, personnages, divinités, sorts, objets nommés) sont
conservés tels quels. Un contenu long est traduit par morceaux coupés aux paragraphes,
pour rester sous les limites de sortie des modèles.
"""
from __future__ import annotations

import re

from app.application.llm_json import load_json_object
from app.application.llm_retry import generate_with_retry
from app.core.language import language_name
from app.domain.ports import LLMProvider

LANGUAGES = ("fr", "en")

# Taille maximale d'un morceau de contenu envoyé en traduction (caractères).
CHUNK_CHARS = 6000

_FR_WORDS = {"le", "la", "les", "des", "une", "est", "et", "du", "dans", "pour", "que", "qui",
             "sur", "avec", "vous", "votre", "sont", "pas", "par", "au", "aux", "ce", "cette", "il"}
_EN_WORDS = {"the", "and", "of", "to", "is", "in", "you", "your", "for", "with", "that", "are",
             "on", "as", "be", "this", "it", "an", "by", "or", "can", "if", "each", "when"}


def detect_language(text: str, default: str = "fr") -> str:
    """Langue dominante (« fr » ou « en ») par comptage de mots-outils ; `default` si indécis."""
    words = re.findall(r"[a-zàâçéèêëîïôûùüÿœ']+", (text or "").lower())
    fr = sum(1 for w in words if w in _FR_WORDS)
    en = sum(1 for w in words if w in _EN_WORDS)
    if fr == en:
        return default if default in LANGUAGES else "fr"
    return "fr" if fr > en else "en"


def other_languages(source: str) -> list[str]:
    return [lang for lang in LANGUAGES if lang != source]


def split_markdown(text: str, max_chars: int = CHUNK_CHARS) -> list[str]:
    """Coupe un markdown en morceaux ≤ max_chars, aux limites de paragraphes (puis de lignes)."""
    if len(text) <= max_chars:
        return [text]
    chunks: list[str] = []
    current = ""
    for para in re.split(r"(\n\s*\n)", text):
        if len(current) + len(para) <= max_chars:
            current += para
            continue
        if current.strip():
            chunks.append(current)
        current = ""
        while len(para) > max_chars:  # paragraphe géant : coupe aux lignes, sinon brutalement
            cut = para.rfind("\n", 0, max_chars)
            cut = cut if cut > 0 else max_chars
            chunks.append(para[:cut])
            para = para[cut:]
        current = para
    if current.strip():
        chunks.append(current)
    return chunks


def content_prompt(text: str, source: str, target: str) -> str:
    return (
        f"Traduis cet extrait de livre de règles de jeu de rôle de la langue « {language_name(source)} » "
        f"vers la langue « {language_name(target)} ».\n\n"
        "Règles IMPÉRATIVES :\n"
        "- Conserve EXACTEMENT la mise en forme markdown (titres, listes, tableaux, gras).\n"
        "- Garde TELS QUELS les noms propres : lieux, personnages, divinités, peuples, sorts et "
        "objets nommés.\n"
        "- Traduis les termes de jeu de façon cohérente (une même notion = un même mot).\n"
        "- Ne résume pas, n'ajoute rien, ne commente pas : renvoie UNIQUEMENT la traduction.\n\n"
        "Extrait :\n"
        f"{text}"
    )


def titles_prompt(titles: list[str], source: str, target: str) -> str:
    listing = "\n".join(f"- {t}" for t in titles)
    return (
        f"Traduis ces titres de sections d'un livre de règles de jeu de rôle de la langue "
        f"« {language_name(source)} » vers la langue « {language_name(target)} ». "
        "Garde les noms propres tels quels.\n"
        'Réponds UNIQUEMENT par un objet JSON : {"titles": {"titre d\'origine": "titre traduit", ...}}\n\n'
        f"{listing}"
    )


class RulesTranslator:
    """Traduit des sections de règles {titre: markdown} d'une langue vers une autre."""

    def __init__(self, llm: LLMProvider) -> None:
        self._llm = llm

    async def translate_titles(self, titles: list[str], source: str, target: str) -> dict[str, str]:
        """{titre d'origine: titre traduit} ; un titre non traduit garde son libellé d'origine."""
        if not titles:
            return {}
        raw = await generate_with_retry(self._llm, titles_prompt(titles, source, target),
                                        output_format="json", temperature=0.2)
        parsed, _ = load_json_object(raw)
        mapping = parsed.get("titles") if isinstance(parsed, dict) else None
        out: dict[str, str] = {}
        for title in titles:
            value = mapping.get(title) if isinstance(mapping, dict) else None
            out[title] = str(value).strip() if isinstance(value, str) and value.strip() else title
        return out

    async def translate_content(self, text: str, source: str, target: str) -> str:
        """Traduit un contenu markdown (morceau par morceau s'il est long)."""
        if not text or not text.strip():
            return text
        parts = []
        for chunk in split_markdown(text):
            if not chunk.strip():
                parts.append(chunk)
                continue
            translated = await generate_with_retry(self._llm, content_prompt(chunk, source, target),
                                                   temperature=0.2)
            parts.append(translated.strip())
        return "\n\n".join(p for p in parts if p.strip())


def unique_titles(titles: dict[str, str]) -> dict[str, str]:
    """Évite deux sections traduites sous le même titre (sinon l'une écraserait l'autre)."""
    seen: dict[str, int] = {}
    out: dict[str, str] = {}
    for original, translated in titles.items():
        count = seen.get(translated, 0)
        seen[translated] = count + 1
        out[original] = translated if count == 0 else f"{translated} ({count + 1})"
    return out
