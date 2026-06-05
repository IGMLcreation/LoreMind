"""Use case : import d'un PDF de règles → sections markdown structurées.

Couche APPLICATION. Orchestre :
  PDF (bytes) → extraction texte (port PdfTextExtractor)
              → CHUNKING (le texte d'un livre dépasse la fenêtre de contexte)
              → MAP   : chaque morceau → {titre de section → markdown}
              → REDUCE: fusion des sections de même titre entre morceaux
              → RulesImportResult (proposition, NON persistée)

Ne dépend que des abstractions du domaine (ports LLMProvider + PdfTextExtractor)
→ testable avec des fakes, et indépendant du provider concret (Ollama/1min.ai).
"""
from __future__ import annotations

import logging

from app.application.chunking import CHUNK_TARGET_TOKENS, chunk_text
from app.application.llm_json import load_json_object
from app.application.llm_retry import generate_with_retry
from app.domain.models import RulesImportResult
from app.domain.ports import LLMProvider, LLMProviderError, PdfTextExtractor

logger = logging.getLogger(__name__)

# Température basse : tâche de tri/réécriture fidèle, pas de créativité.
# Très basse : structuration = recopie/réorganisation fidèle, pas de créativité.
# Plus la valeur est haute, plus le modèle "brode" (invente du contenu absent).
_TEMPERATURE = 0.1

# Taxonomie canonique suggérée au modèle pour homogénéiser les titres entre
# morceaux (sinon "Combat" / "Le combat" / "Règles de combat" se dispersent).
# Le modèle reste libre d'en créer d'autres si rien ne correspond.
_CANONICAL_SECTIONS = [
    "Règles générales",
    "Création de personnage",
    "Caractéristiques et tests",
    "Compétences",
    "Combat",
    "Magie et sorts",
    "Équipement et objets",
    "États et conditions",
    "Repos et récupération",
    "Progression et niveaux",
    "Conseils au Maître de Jeu",
]

_MAP_SYSTEM = """Tu es un assistant qui réorganise un livre de règles de jeu de rôle.
On te donne un EXTRAIT brut d'un PDF de règles (texte parfois mal coupé par la mise en page).

Ta tâche : répartir le contenu de cet extrait dans des SECTIONS THÉMATIQUES.

Règles impératives :
- Tu réponds UNIQUEMENT par un objet JSON valide, sans markdown ni commentaire autour.
- Les CLÉS sont des titres de section (texte court). Les VALEURS sont le contenu de la règle en markdown.
- Utilise EN PRIORITÉ ces titres canoniques quand le contenu y correspond :
{canonical}
- Si un contenu ne rentre dans aucun, crée un titre clair et concis (en français).
- Reproduis FIDÈLEMENT les règles : tu peux nettoyer la coupure des lignes, recoller les mots coupés
  par un tiret en fin de ligne, retirer les en-têtes/pieds de page et numéros de page parasites.
- N'INVENTE AUCUNE règle, ne résume pas abusivement : tu réorganises, tu ne réécris pas le fond.
- Ignore les pages de garde, sommaires, crédits, pages vides (renvoie {{}} si l'extrait n'a aucune règle)."""


class _SectionMerger:
    """Fusionne les sections issues des différents morceaux, ordre préservé.

    Titres insensibles à la casse ("Combat" / "combat" → une seule clé). Chaque
    `add()` renvoie la liste (dé-dupliquée, ordonnée) des titres touchés par ce
    morceau — sert au flux de progression pour annoncer les sections trouvées.
    """

    def __init__(self) -> None:
        self._merged: dict[str, list[str]] = {}
        self._canonical_key: dict[str, str] = {}

    def add(self, sections: dict[str, str]) -> list[str]:
        touched: list[str] = []
        for title, content in sections.items():
            title = title.strip()
            content = (content or "").strip()
            if not title or not content:
                continue
            key = title.lower()
            if key not in self._canonical_key:
                self._canonical_key[key] = title
                self._merged[title] = []
            canonical = self._canonical_key[key]
            self._merged[canonical].append(content)
            touched.append(canonical)
        # Dé-duplication en préservant l'ordre d'apparition.
        seen: set[str] = set()
        return [t for t in touched if not (t in seen or seen.add(t))]

    def result(self) -> dict[str, str]:
        return {title: "\n\n".join(parts) for title, parts in self._merged.items()}


class ImportRulesUseCase:
    """Transforme un PDF de règles en proposition de sections markdown."""

    def __init__(
        self,
        llm: LLMProvider,
        extractor: PdfTextExtractor,
        chunk_target_tokens: int = CHUNK_TARGET_TOKENS,
    ) -> None:
        self._llm = llm
        self._extractor = extractor
        self._chunk_target_tokens = chunk_target_tokens

    async def execute(self, pdf_bytes: bytes) -> RulesImportResult:
        """Variante non-streamée : traite tout puis renvoie le résultat complet."""
        doc = self._extractor.extract(pdf_bytes)
        chunks = chunk_text(doc.full_text, self._chunk_target_tokens)
        logger.info(
            "Import règles : %s page(s) (%s via OCR), %s morceau(x) à traiter.",
            doc.page_count, doc.ocr_page_count, len(chunks),
        )
        merger = _SectionMerger()
        for i, chunk in enumerate(chunks):
            merger.add(await self._map_chunk(chunk, index=i, total=len(chunks)))
        return RulesImportResult(
            sections=merger.result(),
            page_count=doc.page_count,
            ocr_page_count=doc.ocr_page_count,
        )

    async def stream(self, pdf_bytes: bytes):
        """Variante streamée : yield des évènements d'avancement au fil de l'eau.

        Évènements (dicts) : {"type": "extracting"}, puis
        {"type": "start", page_count, ocr_page_count, total}, puis un
        {"type": "progress", current, total, new_sections:[...]} par morceau,
        et enfin {"type": "done", sections, page_count, ocr_page_count}.
        """
        # Émis AVANT l'extraction (potentiellement lente si OCR) pour que l'UI
        # affiche tout de suite "Extraction…" plutôt qu'un écran figé.
        yield {"type": "extracting"}

        doc = self._extractor.extract(pdf_bytes)
        chunks = chunk_text(doc.full_text, self._chunk_target_tokens)
        total = len(chunks)
        logger.info(
            "Import règles (stream) : %s page(s) (%s via OCR), %s morceau(x).",
            doc.page_count, doc.ocr_page_count, total,
        )
        yield {
            "type": "start",
            "page_count": doc.page_count,
            "ocr_page_count": doc.ocr_page_count,
            "total": total,
        }

        merger = _SectionMerger()
        for i, chunk in enumerate(chunks):
            new_titles = merger.add(await self._map_chunk(chunk, index=i, total=total))
            yield {
                "type": "progress",
                "current": i + 1,
                "total": total,
                "new_sections": new_titles,
            }

        yield {
            "type": "done",
            "sections": merger.result(),
            "page_count": doc.page_count,
            "ocr_page_count": doc.ocr_page_count,
        }

    # --- MAP : un morceau → sections -----------------------------------------

    async def _map_chunk(self, chunk: str, *, index: int, total: int) -> dict[str, str]:
        prompt = (
            _MAP_SYSTEM.format(
                canonical="\n".join(f"  - {s}" for s in _CANONICAL_SECTIONS)
            )
            + f"\n\n--- EXTRAIT {index + 1}/{total} ---\n{chunk}\n\n"
            "Renvoie maintenant le JSON des sections."
        )
        raw = await generate_with_retry(
            self._llm, prompt, output_format="json", temperature=_TEMPERATURE)
        return self._parse_sections(raw, index=index)

    @staticmethod
    def _parse_sections(raw: str, *, index: int) -> dict[str, str]:
        """Parse robuste : objet JSON équilibré, ou récupération partielle si tronqué."""
        parsed, recovered = load_json_object(raw)
        if parsed is None:
            logger.warning("Morceau %s : aucun objet JSON exploitable, ignoré.", index)
            return {}
        if recovered:
            logger.warning(
                "Morceau %s : sortie tronquée — récupération des sections complètes "
                "(envisagez des morceaux plus petits).", index)
        if not isinstance(parsed, dict):
            logger.warning("Morceau %s : le LLM n'a pas renvoyé un objet, ignoré.", index)
            return {}
        return {str(k): str(v) for k, v in parsed.items()}
