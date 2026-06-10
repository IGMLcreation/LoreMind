"""Use case : import d'un PDF de campagne → arbre arc → chapitre → scène.

Couche APPLICATION. Même chaîne que l'import de règles (extraction + OCR +
chunking + map-reduce) mais la cible est une ARBORESCENCE narrative :
  - MAP    : chaque morceau → un sous-arbre {arcs:[{chapters:[{scenes}]}]}
  - REDUCE : fusion par NOM à chaque niveau (un chapitre coupé entre 2 morceaux
             est recollé ; ses scènes s'accumulent).

PROPOSITION non persistée : le Core crée les entités seulement après revue.
"""
from __future__ import annotations

import logging

from app.application.chunking import chunk_text, split_in_half
from app.application.llm_json import load_json_object, looks_like_truncated_json
from app.application.llm_retry import generate_with_retry
from app.application.streaming import with_heartbeat

# Repli anti-troncature : si la sortie d'un morceau est coupée, on le retraite en
# 2 moitiés. Borné en profondeur (3 niveaux => jusqu'à 8 sous-blocs).
_MAX_SPLIT_DEPTH = 3
from app.domain.models import (
    ArcProposal,
    CampaignImportResult,
    ChapterProposal,
    RoomProposal,
    SceneProposal,
)
from app.domain.ports import LLMProvider, LLMProviderError, PdfTextExtractor

logger = logging.getLogger(__name__)

# Très basse : structuration = recopie/réorganisation fidèle, pas de créativité.
# Plus la valeur est haute, plus le modèle "brode" (invente du contenu absent).
_TEMPERATURE = 0.1

# Nom de l'arc unique quand le livre n'est pas découpé en actes/parties.
_DEFAULT_ARC_NAME = "Aventure principale"

# Morceaux PLUS GROS que pour les règles : l'IA voit une quête/un chapitre entier
# d'un coup et le structure de façon cohérente (1 scène par lieu) au lieu de le
# fragmenter en dizaines de scènes. Adapté aux providers à grand contexte (1min.ai).
_CHUNK_TARGET_TOKENS = 10000

_MAP_SYSTEM = """Tu es un assistant qui structure un livre de campagne de jeu de rôle.
On te donne un EXTRAIT brut d'un PDF de campagne (texte parfois mal coupé par la mise en page).

Ta tâche : en dégager une ARBORESCENCE narrative à GROS GRAIN : arcs → chapitres → scènes,
et — pour les lieux explorables — leurs PIÈCES (rooms).
  - Un ARC = un acte / une grande partie de la campagne (souvent un seul pour une aventure courte).
  - Un CHAPITRE = une étape majeure du récit : un chapitre du livre, OU — dans une
    campagne "hub" / bac-à-sable — UNE QUÊTE ou UN LIEU principal débloqué depuis le
    point central (ex : Dragon of Icespire Peak → chaque quête/lieu = un chapitre).
  - Une SCÈNE = un temps fort jouable du chapitre : un lieu, une rencontre clé, un moment pivot.
  - Une PIÈCE (room) = une salle d'un lieu explorable (donjon, crypte, manoir...).

TYPE D'ARC ("type") :
- "HUB" si la campagne est un bac-à-sable : des quêtes/lieux optionnels, parallèles,
  débloqués depuis un point central, SANS ordre fixe imposé (ex : Dragon of Icespire Peak).
- "LINEAR" si les chapitres se jouent dans un ordre séquentiel imposé.
- Dans le doute : "LINEAR".

GRANULARITÉ (évite la sur-détection) :
- Vise PEU de scènes : typiquement 1 à 6 par chapitre. PAS des dizaines.
- Un LIEU EXPLORABLE (donjon, crypte, manoir, grotte à plusieurs salles) = UNE SEULE
  scène. Ses salles vont dans le tableau "rooms" de cette scène — JAMAIS en scènes séparées.
- NE crée PAS une scène par rencontre isolée, par PNJ, par monstre ou par paragraphe.
- IGNORE : blocs de stats, listes de monstres, encarts de règles, légendes de cartes,
  pieds de page, sommaires, crédits.

CONTENU D'UNE SCÈNE (fidélité au livre — important) :
- `description` = synopsis de la scène, 2 à 4 phrases (plus que 1 ligne, mais pas le texte intégral).
- `player_narration` = le texte d'AMBIANCE « à lire aux joueurs » (encadrés / boxed text /
  « lecture à voix haute »), recopié FIDÈLEMENT s'il existe dans l'extrait. Vide sinon.
- `gm_notes` = les informations pour le MJ : secrets, développement, ce qui se passe,
  conséquences, indices cachés. Vide si rien de tel.
- Ne RÉSUME pas abusivement player_narration et gm_notes : recopie le contenu utile du livre.

PIÈCES (rooms) — uniquement pour les scènes qui sont des lieux explorables :
- Une entrée par salle numérotée/nommée du donjon (ex : "1. Entrée", "2. Salle des gardes").
- `enemies` = créatures/boss de la salle (vide si aucune). `loot` = trésor/récompense (vide si aucun).
- Pour une scène narrative classique (pas un donjon), "rooms" est un tableau vide [].

Format de réponse :
- Tu réponds UNIQUEMENT par un objet JSON valide, sans markdown ni commentaire autour.
- Schéma EXACT :
  {{"arcs": [{{"name": "...", "description": "...", "type": "LINEAR",
     "chapters": [{{"name": "...", "description": "...", "scenes": [
        {{"name": "...", "description": "...", "player_narration": "...", "gm_notes": "...",
          "rooms": [{{"name": "...", "description": "...", "enemies": "...", "loot": "..."}}]}}
     ]}}]}}
  ]}}
- Utilise les VRAIS titres du livre pour les noms (pas de paraphrase).
- Si le livre n'est PAS découpé en actes/parties, regroupe tout sous un seul arc nommé "{default_arc}".
- N'invente pas de contenu : tu réorganises et recopies ce qui est présent dans l'extrait.
- Si l'extrait ne contient aucune matière narrative, renvoie {{"arcs": []}}."""

# Bloc TOC injecté quand le PDF a des bookmarks : les morceaux étant traités
# séparément, c'est CE référentiel commun qui garantit que tous nomment les
# mêmes chapitres à l'identique → la fusion par nom du _TreeMerger recolle
# les chapitres coupés au lieu de créer des doublons.
_TOC_BLOCK = """

--- STRUCTURE OFFICIELLE DU LIVRE (table des matières du PDF) ---
{toc}
--- FIN DE LA STRUCTURE ---
IMPORTANT : pour nommer les arcs et chapitres, reprends EXACTEMENT les titres
de cette structure (caractère pour caractère). Rattache le contenu de l'extrait
au bon chapitre de la structure, même si son titre n'apparaît pas dans l'extrait."""

# Garde-fou prompt : une TOC de gros livre peut compter des centaines d'entrées
# (sous-sous-sections). On la limite aux niveaux hauts et à un nombre raisonnable.
_TOC_MAX_LEVEL = 2
_TOC_MAX_ENTRIES = 80


def _format_toc(toc) -> str:
    """Formate la TOC du PDF en liste indentée, bornée (niveaux hauts d'abord)."""
    entries = [e for e in toc if e.level <= _TOC_MAX_LEVEL][:_TOC_MAX_ENTRIES]
    if not entries:
        return ""
    return "\n".join(f"{'  ' * (e.level - 1)}- {e.title} (p. {e.page})" for e in entries)


class _TreeMerger:
    """Fusionne les sous-arbres des morceaux en un seul arbre, ordre préservé.

    Clés insensibles à la casse à chaque niveau (nom d'arc / chapitre / scène).
    Description : la première non-vide rencontrée l'emporte (les morceaux suivants
    ne l'écrasent pas).
    """

    def __init__(self) -> None:
        # arc_key -> {"name", "description", "chapters": {chap_key -> {...}}}
        self._arcs: dict[str, dict] = {}

    def add(self, arcs_json: list[dict]) -> None:
        for arc in arcs_json or []:
            name = str(arc.get("name", "")).strip()
            if not name:
                continue
            a = self._arcs.setdefault(
                name.lower(), {"name": name, "description": "", "type": "LINEAR", "chapters": {}})
            self._fill_desc(a, arc)
            # Type d'arc : HUB l'emporte si un seul morceau le signale (propriété globale
            # souvent énoncée une fois, dans l'intro du livre).
            if str(arc.get("type", "")).strip().upper() == "HUB":
                a["type"] = "HUB"
            for chap in arc.get("chapters", []) or []:
                cname = str(chap.get("name", "")).strip()
                if not cname:
                    continue
                c = a["chapters"].setdefault(cname.lower(), {"name": cname, "description": "", "scenes": {}})
                self._fill_desc(c, chap)
                for sc in chap.get("scenes", []) or []:
                    sname = str(sc.get("name", "")).strip()
                    if not sname:
                        continue
                    s = c["scenes"].setdefault(
                        sname.lower(),
                        {"name": sname, "description": "", "player_narration": "",
                         "gm_notes": "", "rooms": {}})
                    self._fill_desc(s, sc)
                    self._fill_field(s, sc, "player_narration")
                    self._fill_field(s, sc, "gm_notes")
                    for rm in sc.get("rooms", []) or []:
                        rname = str(rm.get("name", "")).strip()
                        if not rname:
                            continue
                        r = s["rooms"].setdefault(
                            rname.lower(),
                            {"name": rname, "description": "", "enemies": "", "loot": ""})
                        self._fill_desc(r, rm)
                        self._fill_field(r, rm, "enemies")
                        self._fill_field(r, rm, "loot")

    @staticmethod
    def _fill_desc(node: dict, src: dict) -> None:
        if not node["description"]:
            node["description"] = str(src.get("description") or "").strip()

    @staticmethod
    def _fill_field(node: dict, src: dict, field_name: str) -> None:
        if not node[field_name]:
            node[field_name] = str(src.get(field_name) or "").strip()

    def result(self) -> list[ArcProposal]:
        arcs: list[ArcProposal] = []
        for a in self._arcs.values():
            chapters: list[ChapterProposal] = []
            for c in a["chapters"].values():
                scenes: list[SceneProposal] = []
                for s in c["scenes"].values():
                    rooms = [
                        RoomProposal(r["name"], r["description"], r["enemies"], r["loot"])
                        for r in s["rooms"].values()
                    ]
                    scenes.append(SceneProposal(
                        s["name"], s["description"], s["player_narration"], s["gm_notes"], rooms))
                chapters.append(ChapterProposal(c["name"], c["description"], scenes))
            arcs.append(ArcProposal(a["name"], a["description"], a["type"], chapters))
        return arcs

    def counts(self) -> tuple[int, int, int]:
        arcs = len(self._arcs)
        chapters = sum(len(a["chapters"]) for a in self._arcs.values())
        scenes = sum(len(c["scenes"]) for a in self._arcs.values() for c in a["chapters"].values())
        return arcs, chapters, scenes


class ImportCampaignUseCase:
    """Transforme un PDF de campagne en proposition d'arbre arc→chapitre→scène."""

    def __init__(
        self,
        llm: LLMProvider,
        extractor: PdfTextExtractor,
        chunk_target_tokens: int = _CHUNK_TARGET_TOKENS,
    ) -> None:
        self._llm = llm
        self._extractor = extractor
        self._chunk_target_tokens = chunk_target_tokens

    async def execute(self, pdf_bytes: bytes) -> CampaignImportResult:
        """Variante non-streamée : traite tout puis renvoie l'arbre complet."""
        doc = self._extractor.extract(pdf_bytes)
        chunks = chunk_text(doc.full_text, self._chunk_target_tokens)
        toc_block = _format_toc(doc.toc)
        merger = _TreeMerger()
        for i, chunk in enumerate(chunks):
            merger.add(await self._map_chunk(
                chunk, index=i, total=len(chunks), toc_block=toc_block))
        return CampaignImportResult(
            arcs=merger.result(),
            page_count=doc.page_count,
            ocr_page_count=doc.ocr_page_count,
        )

    async def stream(self, pdf_bytes: bytes):
        """Variante streamée : yield des évènements d'avancement.

        {"type":"extracting"}, puis {"type":"start", page_count, ocr_page_count,
        total}, puis un {"type":"progress", current, total, arc_count,
        chapter_count, scene_count} par morceau, et enfin
        {"type":"done", arcs:[...], page_count, ocr_page_count}.
        """
        yield {"type": "extracting"}

        doc = self._extractor.extract(pdf_bytes)
        chunks = chunk_text(doc.full_text, self._chunk_target_tokens)
        toc_block = _format_toc(doc.toc)
        total = len(chunks)
        logger.info(
            "Import campagne (stream) : %s page(s) (%s via OCR), %s morceau(x), TOC %s.",
            doc.page_count, doc.ocr_page_count, total,
            "présente" if toc_block else "absente",
        )
        yield {
            "type": "start",
            "page_count": doc.page_count,
            "ocr_page_count": doc.ocr_page_count,
            "total": total,
        }

        merger = _TreeMerger()
        skipped = 0
        last_error: str | None = None
        for i, chunk in enumerate(chunks):
            # RÉSILIENCE : un morceau qui échoue (provider saturé, quota, etc.) est
            # SAUTÉ — on ne perd pas tout l'import pour autant. On n'abandonne que
            # si AUCUN morceau ne passe (cf. après la boucle).
            # HEARTBEAT : keep-alive pendant l'appel LLM pour ne jamais laisser le
            # flux SSE silencieux (sinon le Core coupe sur timeout d'inactivité).
            try:
                arcs_payload: list[dict] | None = None
                async for kind, payload in with_heartbeat(
                    self._map_chunk(chunk, index=i, total=total, toc_block=toc_block)
                ):
                    if kind == "heartbeat":
                        yield {"type": "heartbeat", "current": i + 1, "total": total}
                    else:
                        arcs_payload = payload
                merger.add(arcs_payload or [])
            except LLMProviderError as exc:
                skipped += 1
                last_error = str(exc)
                logger.warning("Morceau %s/%s ignoré (échec LLM) : %s", i + 1, total, exc)
                yield {"type": "chunk_failed", "current": i + 1, "total": total,
                       "message": str(exc)[:300]}
            arcs, chapters, scenes = merger.counts()
            yield {
                "type": "progress",
                "current": i + 1,
                "total": total,
                "arc_count": arcs,
                "chapter_count": chapters,
                "scene_count": scenes,
                "skipped": skipped,
            }

        if total > 0 and skipped == total:
            # Tout a échoué : "done" vide serait trompeur → erreur explicite.
            yield {"type": "error",
                   "message": "Tous les morceaux ont échoué auprès du fournisseur IA. "
                              f"Dernier message : {last_error or 'inconnu'}"}
            return

        yield {
            "type": "done",
            "arcs": _serialize_arcs(merger.result()),
            "page_count": doc.page_count,
            "ocr_page_count": doc.ocr_page_count,
            "skipped": skipped,
        }

    # --- MAP : un morceau → sous-arbre ---------------------------------------

    async def _map_chunk(
        self, chunk: str, *, index: int, total: int, toc_block: str = ""
    ) -> list[dict]:
        return await self._extract_arcs(
            chunk, index=index, total=total, depth=0, toc_block=toc_block)

    async def _extract_arcs(
        self, text: str, *, index: int, total: int, depth: int, toc_block: str = ""
    ) -> list[dict]:
        """Extrait l'arborescence d'un texte. Si la SORTIE est tronquée, retraite le
        texte en DEUX moitiés et concatène — le `_TreeMerger` final dédoublonne par
        nom (un arc/chapitre coupé entre les moitiés est recollé)."""
        toc_section = _TOC_BLOCK.format(toc=toc_block) if toc_block else ""
        prompt = (
            _MAP_SYSTEM.format(default_arc=_DEFAULT_ARC_NAME)
            + toc_section
            + f"\n\n--- EXTRAIT {index + 1}/{total} ---\n{text}\n\n"
            "Renvoie maintenant le JSON de l'arborescence."
        )
        raw = await generate_with_retry(
            self._llm, prompt, output_format="json", temperature=_TEMPERATURE)
        arcs, truncated = self._parse_arcs(raw, index=index)

        if truncated and depth < _MAX_SPLIT_DEPTH:
            left, right = split_in_half(text)
            if left and right:
                logger.info(
                    "Morceau %s : sortie tronquée → re-découpage en 2 moitiés (niveau %s).",
                    index, depth + 1)
                a = await self._extract_arcs(
                    left, index=index, total=total, depth=depth + 1, toc_block=toc_block)
                b = await self._extract_arcs(
                    right, index=index, total=total, depth=depth + 1, toc_block=toc_block)
                return a + b
        if truncated:
            logger.warning(
                "Morceau %s : sortie tronquée, profondeur max atteinte — partiel conservé.", index)
        return arcs

    @staticmethod
    def _parse_arcs(raw: str, *, index: int) -> tuple[list[dict], bool]:
        """Parse robuste → (arcs, tronqué). `tronqué`=True si récupération partielle."""
        parsed, recovered = load_json_object(raw)
        if parsed is None:
            truncated = looks_like_truncated_json(raw)
            if not truncated:
                logger.warning(
                    "Morceau %s : aucun objet JSON exploitable, ignoré. "
                    "Début de la réponse du modèle : %r",
                    index, (raw or "").strip()[:300] or "(réponse VIDE)")
            return [], truncated
        if isinstance(parsed, dict):
            arcs = parsed.get("arcs", [])
            return (arcs if isinstance(arcs, list) else []), recovered
        return [], recovered


def _serialize_arcs(arcs: list[ArcProposal]) -> list[dict]:
    """Sérialise l'arbre de dataclasses en dicts JSON pour le flux SSE."""
    return [
        {
            "name": a.name,
            "description": a.description,
            "type": a.arc_type,
            "chapters": [
                {
                    "name": c.name,
                    "description": c.description,
                    "scenes": [
                        {
                            "name": s.name,
                            "description": s.description,
                            "player_narration": s.player_narration,
                            "gm_notes": s.gm_notes,
                            "rooms": [
                                {
                                    "name": r.name,
                                    "description": r.description,
                                    "enemies": r.enemies,
                                    "loot": r.loot,
                                }
                                for r in s.rooms
                            ],
                        }
                        for s in c.scenes
                    ],
                }
                for c in a.chapters
            ],
        }
        for a in arcs
    ]
