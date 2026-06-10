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

from app.application.chunking import CHUNK_TARGET_TOKENS, chunk_text, split_in_half
from app.application.llm_json import load_json_object, looks_like_truncated_json
from app.application.llm_retry import generate_with_retry
from app.application.streaming import with_heartbeat

# Repli anti-troncature : si la SORTIE d'un morceau est coupée (le modèle ne peut
# pas tout réécrire en une réponse), on retraite ce morceau en 2 moitiés. Borné en
# profondeur pour éviter une récursion infinie (3 niveaux => jusqu'à 8 sous-blocs ;
# 1-2 niveaux suffisent en pratique, le reste est un garde-fou).
_MAX_SPLIT_DEPTH = 3
from app.domain.models import RulesImportResult
from app.domain.ports import (
    LLMGenerationTimeout,
    LLMProvider,
    LLMProviderError,
    PdfTextExtractor,
)

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


def _coerce_markdown(value: object) -> str:
    """Convertit une valeur de section renvoyée par le LLM en markdown plat.

    Malgré la consigne « valeurs = markdown », certains modèles nichent des
    sous-sections ({titre: {sous-titre: contenu}}) ou des listes. Un `str(v)`
    naïf produirait du repr Python ({'k': 'v'}) ; on aplatit récursivement à la
    place pour ne perdre aucun contenu.
    """
    if isinstance(value, str):
        return value
    if isinstance(value, dict):
        parts = []
        for k, v in value.items():
            content = _coerce_markdown(v)
            # Clé = sous-titre (cas normal) ; si la "valeur" est vide, la clé
            # elle-même porte le contenu (dérive observée sur certains modèles).
            parts.append(f"{k}\n\n{content}".strip() if content else str(k))
        return "\n\n".join(parts)
    if isinstance(value, list):
        return "\n\n".join(_coerce_markdown(v) for v in value)
    return "" if value is None else str(value)


def _combine_sections(a: dict[str, str], b: dict[str, str]) -> dict[str, str]:
    """Fusionne deux dicts de sections (issus des 2 moitiés d'un morceau re-découpé).

    Titres insensibles à la casse : un même titre présent des deux côtés (une section
    coupée par le re-découpage) voit ses contenus concaténés au lieu d'être écrasés.
    """
    out = dict(a)
    by_lower = {k.lower(): k for k in out}
    for title, content in b.items():
        key = by_lower.get(title.lower())
        if key is not None:
            out[key] = f"{out[key]}\n\n{content}".strip()
        else:
            out[title] = content
            by_lower[title.lower()] = title
    return out


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
        skipped = 0
        last_error: str | None = None
        for i, chunk in enumerate(chunks):
            # RÉSILIENCE : un morceau qui échoue est SAUTÉ, l'import continue.
            # Abandon seulement si AUCUN morceau ne passe (cf. après la boucle).
            # HEARTBEAT : on émet des keep-alive pendant l'appel LLM (long sur un
            # provider lent) pour que le flux SSE ne soit jamais coupé par le Core.
            new_titles: list[str] = []
            try:
                sections: dict[str, str] | None = None
                async for kind, payload in with_heartbeat(
                    self._map_chunk(chunk, index=i, total=total)
                ):
                    if kind == "heartbeat":
                        yield {"type": "heartbeat", "current": i + 1, "total": total}
                    else:
                        sections = payload
                new_titles = merger.add(sections or {})
            except LLMProviderError as exc:
                skipped += 1
                last_error = str(exc)
                logger.warning("Morceau %s/%s ignoré (échec LLM) : %s", i + 1, total, exc)
                yield {"type": "chunk_failed", "current": i + 1, "total": total,
                       "message": str(exc)[:300]}
            yield {
                "type": "progress",
                "current": i + 1,
                "total": total,
                "new_sections": new_titles,
                "skipped": skipped,
            }

        if total > 0 and skipped == total:
            yield {"type": "error",
                   "message": "Tous les morceaux ont échoué auprès du fournisseur IA. "
                              f"Dernier message : {last_error or 'inconnu'}"}
            return

        yield {
            "type": "done",
            "sections": merger.result(),
            "page_count": doc.page_count,
            "ocr_page_count": doc.ocr_page_count,
            "skipped": skipped,
        }

    # --- MAP : un morceau → sections -----------------------------------------

    async def _map_chunk(self, chunk: str, *, index: int, total: int) -> dict[str, str]:
        return await self._extract_sections(chunk, index=index, total=total, depth=0)

    async def _extract_sections(
        self, text: str, *, index: int, total: int, depth: int
    ) -> dict[str, str]:
        """Extrait les sections d'un texte. Si la SORTIE est tronquée, retraite le
        texte en DEUX moitiés (chacune produit une réponse complète) et fusionne —
        ainsi aucune section n'est perdue, quel que soit le plafond de sortie."""
        prompt = (
            _MAP_SYSTEM.format(
                canonical="\n".join(f"  - {s}" for s in _CANONICAL_SECTIONS)
            )
            + f"\n\n--- EXTRAIT {index + 1}/{total} ---\n{text}\n\n"
            "Renvoie maintenant le JSON des sections."
        )
        try:
            raw = await generate_with_retry(
                self._llm, prompt, output_format="json", temperature=_TEMPERATURE)
        except LLMGenerationTimeout:
            # Le modèle générait mais trop lentement pour réécrire tout le morceau
            # dans le temps imparti (fréquent sur tier gratuit + gros morceaux).
            # Même remède que la troncature : deux moitiés → sortie 2× plus courte.
            if depth >= _MAX_SPLIT_DEPTH:
                raise
            left, right = split_in_half(text)
            if not left or not right:
                raise
            logger.info(
                "Morceau %s : timeout de génération → re-découpage en 2 moitiés (niveau %s).",
                index, depth + 1)
            a = await self._extract_sections(left, index=index, total=total, depth=depth + 1)
            b = await self._extract_sections(right, index=index, total=total, depth=depth + 1)
            return _combine_sections(a, b)
        sections, truncated = self._parse_sections(raw, index=index)

        if truncated and depth < _MAX_SPLIT_DEPTH:
            left, right = split_in_half(text)
            if left and right:
                logger.info(
                    "Morceau %s : sortie tronquée → re-découpage en 2 moitiés (niveau %s).",
                    index, depth + 1)
                a = await self._extract_sections(left, index=index, total=total, depth=depth + 1)
                b = await self._extract_sections(right, index=index, total=total, depth=depth + 1)
                return _combine_sections(a, b)
        if truncated:
            logger.warning(
                "Morceau %s : sortie tronquée, profondeur max atteinte — partiel conservé.", index)
        return sections

    @staticmethod
    def _parse_sections(raw: str, *, index: int) -> tuple[dict[str, str], bool]:
        """Parse robuste → (sections, tronqué). `tronqué`=True si récupération partielle."""
        parsed, recovered = load_json_object(raw)
        if parsed is None:
            # Rien d'exploitable : soit prose (échec), soit JSON coupé avant toute
            # structure complète (→ on signalera 'tronqué' pour re-découper).
            truncated = looks_like_truncated_json(raw)
            if not truncated:
                logger.warning(
                    "Morceau %s : aucun objet JSON exploitable, ignoré. "
                    "Début de la réponse du modèle : %r",
                    index, (raw or "").strip()[:300] or "(réponse VIDE)")
            return {}, truncated
        if not isinstance(parsed, dict):
            logger.warning("Morceau %s : le LLM n'a pas renvoyé un objet, ignoré.", index)
            return {}, False
        return {str(k): _coerce_markdown(v) for k, v in parsed.items()}, recovered
