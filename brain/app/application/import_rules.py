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
import re

import asyncio

from app.application.chunking import CHUNK_TARGET_TOKENS, chunk_text, split_in_half
from app.application.import_status import (
    notify_status,
    reset_status_queue,
    set_status_queue,
)
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

# Schéma de la sortie attendue : objet PLAT {titre: markdown}. Passé tel quel à
# Ollama (structured outputs : la grammaire interdit physiquement les objets
# imbriqués, les clés "thought" à valeur non-string, le bavardage hors JSON…
# indispensable pour les petits modèles locaux qui ne suivent pas les consignes).
# Les adapters cloud le traduisent en mode JSON natif (json_object).
_SECTIONS_SCHEMA: dict = {
    "type": "object",
    "additionalProperties": {"type": "string"},
}

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

Format EXACT attendu — un objet JSON plat {{titre de section: contenu markdown}} :
{{"Combat": "## Initiative\\n\\nChaque participant lance 1d20...", "Magie et sorts": "## Sorts\\n\\n..."}}

Règles impératives :
- Tu réponds UNIQUEMENT par cet objet JSON, sans texte avant ni après.
- Les CLÉS sont des titres de section (texte court). Les VALEURS sont le contenu de la règle en markdown (chaîne de caractères, jamais un objet ou une liste).
- INTERDIT : des clés génériques comme "title", "content", "sections", "thought" ou "notes" ; des objets imbriqués ; tout commentaire sur ta démarche ou ton raisonnement.
- Utilise EN PRIORITÉ ces titres canoniques quand le contenu y correspond :
{canonical}
- Si un contenu ne rentre dans aucun, crée un titre clair et concis (en français).
- Reproduis FIDÈLEMENT les règles : tu peux nettoyer la coupure des lignes, recoller les mots coupés
  par un tiret en fin de ligne, retirer les en-têtes/pieds de page et numéros de page parasites.
- N'INVENTE AUCUNE règle, ne résume pas abusivement : tu réorganises, tu ne réécris pas le fond.
- Ignore les pages de garde, sommaires, crédits, pages vides (renvoie {{}} si l'extrait n'a aucune règle)."""

# --- Mode SEGMENTATION (modèles locaux) --------------------------------------
# Réécrire tout le texte en JSON impose une SORTIE ≈ taille de l'ENTRÉE : à
# ~100 tokens/s en local, un livre = des dizaines de minutes et des troncatures
# en cascade. Ici le modèle ne renvoie que les FRONTIÈRES des sections (titre +
# premiers mots exacts) — ~200 tokens quel que soit le morceau — et c'est NOUS
# qui découpons le texte original. ~50× plus rapide, fidélité parfaite du
# contenu (texte source intact), plus de troncature possible.

_SEGMENT_SYSTEM = """Tu analyses un EXTRAIT brut d'un livre de règles de jeu de rôle.
Ta tâche : repérer où COMMENCENT les sections thématiques. Tu ne réécris RIEN.

Format EXACT attendu :
{{"sections": [{{"titre": "Combat", "debut": "Le combat se déroule en tours de"}}, ...]}}

Règles impératives :
- "debut" = les 5 à 10 PREMIERS MOTS du passage où la section commence, COPIÉS À L'IDENTIQUE
  depuis l'extrait (même orthographe, même ponctuation, même langue). JAMAIS un résumé.
- La PREMIÈRE entrée commence aux tout premiers mots de l'extrait (même si le contenu
  poursuit une section entamée avant cet extrait).
- Les entrées suivent l'ordre du texte. Vise des sections LARGES (un thème), pas un titre
  par paragraphe : un extrait contient typiquement 1 à 6 sections.
- Titres : EN PRIORITÉ parmi :
{canonical}
  sinon un titre court et clair en français.
- Pages de garde, sommaires, crédits : n'en fais pas des sections. Si l'extrait n'est que ça,
  renvoie {{"sections": []}}."""

# Schéma passé à Ollama (structured outputs) : un objet {"sections": [...]}.
# Racine objet (pas tableau) car l'extraction côté Brain repère le premier {…}.
_ANCHORS_SCHEMA: dict = {
    "type": "object",
    "properties": {
        "sections": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "titre": {"type": "string"},
                    "debut": {"type": "string"},
                },
                "required": ["titre", "debut"],
                "additionalProperties": False,
            },
        },
    },
    "required": ["sections"],
    "additionalProperties": False,
}


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


# Clés "méta" que certains modèles glissent dans le JSON (fuite de raisonnement,
# schéma title/content inventé…) : jamais des titres de section voulus.
_META_KEYS = frozenset({
    "thought", "thoughts", "thinking", "reasoning", "raisonnement",
    "comment", "commentaire", "commentaires", "note", "notes", "explanation",
})


def _normalize_sections(parsed: dict) -> dict:
    """Ramène les formes déviantes courantes au format attendu {titre: contenu}.

    Observé sur les petits modèles locaux (gemma 12b) malgré les consignes :
      - enveloppe {"sections": {...}} ou {"règles": {...}} autour du vrai contenu ;
      - schéma inventé {"title": "...", "content": "...", "thought": "..."} →
        une seule section dont le titre est la valeur de "title" ;
      - clés méta ("thought", "notes"…) mêlées aux vraies sections → retirées.
    """
    by_lower = {str(k).strip().lower(): k for k in parsed}
    # Enveloppe : un unique conteneur connu dont la valeur est l'objet attendu.
    if len(parsed) == 1:
        only_key, only_val = next(iter(parsed.items()))
        if (isinstance(only_val, dict)
                and str(only_key).strip().lower() in {"sections", "règles", "regles", "rules"}):
            return _normalize_sections(only_val)
    # Schéma {"title": ..., "content": ...} : le titre est une VALEUR, pas une clé.
    if "title" in by_lower and "content" in by_lower:
        title = str(parsed[by_lower["title"]]).strip()
        content = parsed[by_lower["content"]]
        if title and not isinstance(content, dict):
            return {title: content}
    return {k: v for k, v in parsed.items()
            if str(k).strip().lower() not in _META_KEYS}


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


def _find_anchor(text: str, anchor: str, start: int) -> int | None:
    """Position de `anchor` dans `text` à partir de `start`, ou None.

    Le modèle recopie les premiers mots d'un passage, mais le texte extrait du
    PDF contient des sauts de ligne/espaces multiples au même endroit, et le
    modèle normalise parfois la casse. Trois passes, de la plus stricte à la
    plus tolérante : exacte → espaces≈\\s+ → idem insensible à la casse."""
    pos = text.find(anchor, start)
    if pos != -1:
        return pos
    words = anchor.split()
    if not words:
        return None
    pattern = r"\s+".join(re.escape(w) for w in words)
    match = re.compile(pattern).search(text, start)
    if match:
        return match.start()
    match = re.compile(pattern, re.IGNORECASE).search(text, start)
    return match.start() if match else None


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
        segment_only: bool = False,
    ) -> None:
        """`segment_only=True` (modèles locaux) : le LLM ne renvoie que les
        frontières des sections (titre + premiers mots) et le texte original est
        découpé localement — sortie minuscule, pas de réécriture. False (cloud) :
        le LLM réécrit le contenu en sections markdown nettoyées."""
        self._llm = llm
        self._extractor = extractor
        self._chunk_target_tokens = chunk_target_tokens
        self._segment_only = segment_only

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
        # Canal de statut : les couches profondes (retry LLM, re-découpage) y
        # publient des messages destinés à l'UI — cf. import_status.notify_status.
        status_queue: asyncio.Queue = asyncio.Queue()
        status_token = set_status_queue(status_queue)
        try:
            for i, chunk in enumerate(chunks):
                # RÉSILIENCE : un morceau qui échoue est SAUTÉ, l'import continue.
                # Abandon seulement si AUCUN morceau ne passe (cf. après la boucle).
                # HEARTBEAT : on émet des keep-alive pendant l'appel LLM (long sur un
                # provider lent) pour que le flux SSE ne soit jamais coupé par le Core.
                new_titles: list[str] = []
                try:
                    sections: dict[str, str] | None = None
                    async for kind, payload in with_heartbeat(
                        self._map_chunk(chunk, index=i, total=total),
                        status_queue=status_queue,
                    ):
                        if kind == "heartbeat":
                            yield {"type": "heartbeat", "current": i + 1, "total": total}
                        elif kind == "status":
                            yield {"type": "status", "message": payload,
                                   "current": i + 1, "total": total}
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
        finally:
            reset_status_queue(status_token)

        if total > 0 and skipped == total:
            yield {"type": "error",
                   "message": "Tous les morceaux ont échoué auprès du fournisseur IA. "
                              f"Dernier message : {last_error or 'inconnu'}"}
            return

        sections = merger.result()
        if total > 0 and not sections:
            # Le texte a bien été extrait mais AUCUN morceau n'a produit de JSON
            # exploitable (sorties coupées/illisibles). Sans ce signal, l'UI reçoit
            # un `done` vide et l'utilisateur conclut à tort que le PDF est illisible.
            yield {"type": "error",
                   "message": "Le texte du PDF a été extrait, mais le modèle n'a produit "
                              "aucune section exploitable (réponses JSON vides ou coupées). "
                              "Réduisez la taille des morceaux d'import, augmentez la fenêtre "
                              "de contexte (num_ctx) ou essayez un autre modèle."}
            return

        yield {
            "type": "done",
            "sections": sections,
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
        system = _SEGMENT_SYSTEM if self._segment_only else _MAP_SYSTEM
        schema = _ANCHORS_SCHEMA if self._segment_only else _SECTIONS_SCHEMA
        prompt = (
            system.format(
                canonical="\n".join(f"  - {s}" for s in _CANONICAL_SECTIONS)
            )
            + f"\n\n--- EXTRAIT {index + 1}/{total} ---\n{text}\n\n"
            "Renvoie maintenant le JSON des sections."
        )
        try:
            raw = await generate_with_retry(
                self._llm, prompt, output_format=schema, temperature=_TEMPERATURE)
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
            notify_status(
                f"Le modèle est trop lent sur le morceau {index + 1} : "
                "re-découpage en 2 moitiés plus digestes…")
            a = await self._extract_sections(left, index=index, total=total, depth=depth + 1)
            b = await self._extract_sections(right, index=index, total=total, depth=depth + 1)
            return _combine_sections(a, b)
        if self._segment_only:
            sections, truncated = self._parse_anchors(raw, text, index=index)
        else:
            sections, truncated = self._parse_sections(raw, index=index)

        if truncated and depth < _MAX_SPLIT_DEPTH:
            left, right = split_in_half(text)
            if left and right:
                logger.info(
                    "Morceau %s : sortie tronquée → re-découpage en 2 moitiés (niveau %s).",
                    index, depth + 1)
                notify_status(
                    f"Réponse du modèle coupée sur le morceau {index + 1} : "
                    "re-découpage en 2 moitiés plus digestes…")
                a = await self._extract_sections(left, index=index, total=total, depth=depth + 1)
                b = await self._extract_sections(right, index=index, total=total, depth=depth + 1)
                return _combine_sections(a, b)
        if truncated:
            logger.warning(
                "Morceau %s : sortie tronquée, profondeur max atteinte — partiel conservé.", index)
        return sections

    @staticmethod
    def _parse_anchors(raw: str, text: str, *, index: int) -> tuple[dict[str, str], bool]:
        """Mode segmentation : réponse {"sections": [{titre, debut}, …]} → on localise
        chaque `debut` dans le texte ORIGINAL et on découpe entre les ancres.

        Une ancre introuvable est abandonnée (son contenu reste dans la section
        précédente — aucun texte n'est perdu). Le texte avant la première ancre
        trouvée est rattaché à la première section (le prompt demande au modèle de
        faire démarrer la première entrée aux premiers mots de l'extrait)."""
        parsed, recovered = load_json_object(raw)
        if parsed is None:
            truncated = looks_like_truncated_json(raw)
            if not truncated:
                logger.warning(
                    "Morceau %s : aucun objet JSON exploitable (segmentation), ignoré. "
                    "Début de la réponse du modèle : %r",
                    index, (raw or "").strip()[:300] or "(réponse VIDE)")
            return {}, truncated
        entries = parsed.get("sections") if isinstance(parsed, dict) else None
        if not isinstance(entries, list):
            logger.warning("Morceau %s : pas de liste 'sections' exploitable, ignoré.", index)
            return {}, False

        # Localisation séquentielle : chaque ancre est cherchée APRÈS la précédente
        # (préserve l'ordre du texte, évite qu'une phrase répétée matche trop tôt).
        located: list[tuple[str, int]] = []
        cursor = 0
        dropped = 0
        for entry in entries:
            if not isinstance(entry, dict):
                continue
            title = str(entry.get("titre") or "").strip()
            anchor = str(entry.get("debut") or "").strip()
            if not title or not anchor:
                continue
            pos = _find_anchor(text, anchor, cursor)
            if pos is None:
                dropped += 1
                continue
            located.append((title, pos))
            cursor = pos + 1
        if dropped:
            logger.info(
                "Morceau %s : %s ancre(s) de section introuvable(s) — contenu rattaché "
                "à la section précédente.", index, dropped)
        if not located:
            return {}, False

        # Découpe entre ancres ; le préambule éventuel rejoint la première section.
        located[0] = (located[0][0], 0)
        sections: dict[str, str] = {}
        for i, (title, start) in enumerate(located):
            end = located[i + 1][1] if i + 1 < len(located) else len(text)
            content = text[start:end].strip()
            if not content:
                continue
            if title in sections:
                sections[title] = f"{sections[title]}\n\n{content}"
            else:
                sections[title] = content
        return sections, recovered

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
        normalized = _normalize_sections(parsed)
        return {str(k): _coerce_markdown(v) for k, v in normalized.items()}, recovered
