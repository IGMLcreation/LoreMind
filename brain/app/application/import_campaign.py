"""Use case : import d'un PDF de campagne → arbre arc → chapitre → scène.

Couche APPLICATION. Même chaîne que l'import de règles (extraction + OCR +
chunking + map-reduce) mais la cible est une ARBORESCENCE narrative :
  - MAP    : chaque morceau → un sous-arbre {arcs:[{chapters:[{scenes}]}]}
  - REDUCE : fusion par NOM à chaque niveau (un chapitre coupé entre 2 morceaux
             est recollé ; ses scènes s'accumulent).

PROPOSITION non persistée : le Core crée les entités seulement après revue.
"""
from __future__ import annotations

import asyncio
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
    NpcImportProposal,
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

PNJ ET CRÉATURES NOTABLES ("npcs", tableau au niveau racine) :
- Recense les PNJ NOMMÉS (alliés, marchands, antagonistes) et les créatures UNIQUES
  (boss, monstre récurrent) présents dans l'extrait.
- `description` = courte fiche utile au MJ : rôle dans l'histoire, apparence,
  motivations, où on le rencontre. 2 à 4 phrases, fidèles au livre.
- N'inclus PAS les monstres génériques sans nom (« 3 gobelins », « un loup »).
- Aucun PNJ nommé dans l'extrait → "npcs": [].

Format de réponse :
- Tu réponds UNIQUEMENT par un objet JSON valide, sans markdown ni commentaire autour.
- Schéma EXACT :
  {{"arcs": [{{"name": "...", "description": "...", "type": "LINEAR",
     "chapters": [{{"name": "...", "description": "...", "scenes": [
        {{"name": "...", "description": "...", "player_narration": "...", "gm_notes": "...",
          "rooms": [{{"name": "...", "description": "...", "enemies": "...", "loot": "..."}}]}}
     ]}}]}}
  ],
   "npcs": [{{"name": "...", "description": "..."}}]}}
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


# Consolidation finale : le squelette (noms seuls) est minuscule, donc l'appel
# est quasi gratuit comparé aux MAP. Température 0 et consigne CONSERVATRICE :
# ne fusionner que les doublons évidents, jamais des entités distinctes.
_CONSOLIDATE_PROMPT = """Voici le squelette d'une arborescence arc → chapitre → scène issue d'une
fusion AUTOMATIQUE de morceaux d'un livre de campagne de jeu de rôle. La fusion par nom exact
peut avoir laissé des QUASI-DOUBLONS : le même chapitre ou la même scène sous deux libellés
légèrement différents (ex: "La Crypte" et "Crypte de Karrak", "3. Salle des gardes" et
"Salle des gardes").

{skeleton}

Identifie UNIQUEMENT les fusions ÉVIDENTES (même entité du livre sous deux noms). Sois
CONSERVATEUR : dans le doute, ne fusionne PAS. Deux lieux/évènements distincts ne doivent
JAMAIS être fusionnés.

Réponds UNIQUEMENT par un objet JSON valide :
{{"chapter_merges": [{{"into": "nom du chapitre à garder", "merge": ["nom à fusionner", ...]}}],
  "scene_merges": [{{"chapter": "nom du chapitre", "into": "nom de la scène à garder",
                     "merge": ["nom à fusionner", ...]}}]}}
S'il n'y a RIEN à fusionner (cas le plus fréquent) : {{"chapter_merges": [], "scene_merges": []}}"""


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
        # npc_key (nom en minuscules) -> {"name", "description"}
        self._npcs: dict[str, dict] = {}

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
                    # Narration/notes : CONCATÉNATION (pas premier-gagne) — une
                    # scène coupée entre deux morceaux apporte la suite de son
                    # contenu dans le morceau suivant ; la jeter perdrait la
                    # moitié du donjon. Le doublon exact (overlap) est filtré.
                    self._append_field(s, sc, "player_narration")
                    self._append_field(s, sc, "gm_notes")
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

    def add_npcs(self, npcs_json: list[dict]) -> None:
        """Accumule les PNJ détectés. Un PNJ revu dans un autre morceau garde la
        description la plus COMPLÈTE (la plus longue) — un PNJ récurrent est
        souvent décrit en détail une seule fois."""
        for npc in npcs_json or []:
            name = str(npc.get("name", "")).strip()
            if not name:
                continue
            desc = str(npc.get("description") or "").strip()
            entry = self._npcs.setdefault(name.lower(), {"name": name, "description": ""})
            if len(desc) > len(entry["description"]):
                entry["description"] = desc

    def npcs(self) -> list[NpcImportProposal]:
        return [NpcImportProposal(n["name"], n["description"]) for n in self._npcs.values()]

    @staticmethod
    def _fill_desc(node: dict, src: dict) -> None:
        if not node["description"]:
            node["description"] = str(src.get("description") or "").strip()

    @staticmethod
    def _fill_field(node: dict, src: dict, field_name: str) -> None:
        if not node[field_name]:
            node[field_name] = str(src.get(field_name) or "").strip()

    @staticmethod
    def _append_field(node: dict, src: dict, field_name: str) -> None:
        """Accumule la valeur de `src` à la suite de l'existante (scène coupée
        entre deux morceaux). Ignore le vide et le contenu déjà présent (un
        morceau redondant — relecture d'overlap — ne duplique rien)."""
        new = str(src.get(field_name) or "").strip()
        if not new:
            return
        current = node[field_name]
        if not current:
            node[field_name] = new
        elif new not in current and current not in new:
            node[field_name] = current + "\n\n" + new
        elif current in new:
            # Le nouveau contenu ENGLOBE l'ancien (version plus complète) → on le prend.
            node[field_name] = new

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

    # --- Consolidation : fusion des quasi-doublons détectés par le LLM ---------

    def skeleton_text(self) -> str:
        """Squelette de l'arbre (noms seuls) — entrée compacte de la consolidation."""
        lines: list[str] = []
        for a in self._arcs.values():
            lines.append(f"ARC: {a['name']}")
            for c in a["chapters"].values():
                lines.append(f"  CHAPITRE: {c['name']}")
                for s in c["scenes"].values():
                    lines.append(f"    SCENE: {s['name']}")
        return "\n".join(lines)

    def merge_chapters(self, into_name: str, merge_names: list[str]) -> bool:
        """Fusionne les chapitres `merge_names` dans `into_name` (tous arcs).

        Best-effort : les noms inconnus sont ignorés. Renvoie True si modifié.
        """
        target = self._find_chapter(into_name)
        if target is None:
            return False
        changed = False
        for mname in merge_names:
            key = str(mname).strip().lower()
            if not key or key == str(into_name).strip().lower():
                continue
            for a in self._arcs.values():
                src = a["chapters"].pop(key, None)
                if src is None or src is target:
                    continue
                self._fill_desc(target, src)
                for skey, sdict in src["scenes"].items():
                    if skey in target["scenes"]:
                        self._merge_scene_into(target["scenes"][skey], sdict)
                    else:
                        target["scenes"][skey] = sdict
                changed = True
        return changed

    def merge_scenes(self, chapter_name: str, into_name: str, merge_names: list[str]) -> bool:
        """Fusionne les scènes `merge_names` dans `into_name` au sein du chapitre."""
        chapter = self._find_chapter(chapter_name)
        if chapter is None:
            return False
        target = chapter["scenes"].get(str(into_name).strip().lower())
        if target is None:
            return False
        changed = False
        for mname in merge_names:
            key = str(mname).strip().lower()
            if not key or key == str(into_name).strip().lower():
                continue
            src = chapter["scenes"].pop(key, None)
            if src is None or src is target:
                continue
            self._merge_scene_into(target, src)
            changed = True
        return changed

    def _find_chapter(self, name: str) -> dict | None:
        key = str(name).strip().lower()
        for a in self._arcs.values():
            if key in a["chapters"]:
                return a["chapters"][key]
        return None

    def _merge_scene_into(self, target: dict, src: dict) -> None:
        self._fill_desc(target, src)
        self._append_field(target, src, "player_narration")
        self._append_field(target, src, "gm_notes")
        for rkey, rdict in src.get("rooms", {}).items():
            target["rooms"].setdefault(rkey, rdict)


class ImportCampaignUseCase:
    """Transforme un PDF de campagne en proposition d'arbre arc→chapitre→scène."""

    def __init__(
        self,
        llm: LLMProvider,
        extractor: PdfTextExtractor,
        chunk_target_tokens: int = _CHUNK_TARGET_TOKENS,
        map_concurrency: int = 1,
    ) -> None:
        self._llm = llm
        self._extractor = extractor
        self._chunk_target_tokens = chunk_target_tokens
        # Appels MAP par VAGUES de cette taille : l'ordre narratif est préservé
        # (fusion vague par vague, dans l'ordre du livre) mais le mur d'attente
        # des appels LLM est divisé d'autant. 1 = comportement séquentiel.
        self._map_concurrency = max(1, map_concurrency)

    async def execute(self, pdf_bytes: bytes) -> CampaignImportResult:
        """Variante non-streamée : traite tout puis renvoie l'arbre complet."""
        doc = self._extractor.extract(pdf_bytes)
        chunks = chunk_text(doc.full_text, self._chunk_target_tokens)
        toc_block = _format_toc(doc.toc)
        merger = _TreeMerger()
        total = len(chunks)
        for start in range(0, total, self._map_concurrency):
            wave = list(enumerate(chunks))[start:start + self._map_concurrency]
            results = await asyncio.gather(*(
                self._map_chunk(c, index=i, total=total, toc_block=toc_block)
                for i, c in wave
            ))
            for res in results:
                merger.add(res["arcs"])
                merger.add_npcs(res["npcs"])
        if total > 1:
            await self._consolidate(merger)
        return CampaignImportResult(
            arcs=merger.result(),
            page_count=doc.page_count,
            ocr_page_count=doc.ocr_page_count,
            npcs=merger.npcs(),
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
        done_count = 0
        # PARALLÉLISME : les morceaux sont traités par VAGUES de `map_concurrency`
        # appels simultanés. L'ordre narratif est préservé : la fusion se fait
        # vague par vague, dans l'ordre du livre.
        # RÉSILIENCE : un morceau qui échoue (provider saturé, quota, etc.) est
        # SAUTÉ — on ne perd pas tout l'import pour autant. On n'abandonne que
        # si AUCUN morceau ne passe (cf. après la boucle).
        # HEARTBEAT : keep-alive pendant la vague d'appels LLM pour ne jamais
        # laisser le flux SSE silencieux (sinon le Core coupe sur inactivité).
        for start in range(0, total, self._map_concurrency):
            wave = list(enumerate(chunks))[start:start + self._map_concurrency]
            gathered = asyncio.gather(
                *(self._map_chunk(c, index=i, total=total, toc_block=toc_block)
                  for i, c in wave),
                return_exceptions=True,
            )
            results: list | None = None
            async for kind, payload in with_heartbeat(gathered):
                if kind == "heartbeat":
                    yield {"type": "heartbeat", "current": done_count + 1, "total": total}
                else:
                    results = payload
            for (i, _), res in zip(wave, results or []):
                done_count += 1
                if isinstance(res, LLMProviderError):
                    skipped += 1
                    last_error = str(res)
                    logger.warning("Morceau %s/%s ignoré (échec LLM) : %s", i + 1, total, res)
                    yield {"type": "chunk_failed", "current": i + 1, "total": total,
                           "message": str(res)[:300]}
                elif isinstance(res, BaseException):
                    raise res  # bug inattendu : ne pas l'avaler en silence
                else:
                    merger.add((res or {}).get("arcs") or [])
                    merger.add_npcs((res or {}).get("npcs") or [])
                arcs, chapters, scenes = merger.counts()
                yield {
                    "type": "progress",
                    "current": done_count,
                    "total": total,
                    "arc_count": arcs,
                    "chapter_count": chapters,
                    "scene_count": scenes,
                    "npc_count": len(merger.npcs()),
                    "skipped": skipped,
                }

        if total > 0 and skipped == total:
            # Tout a échoué : "done" vide serait trompeur → erreur explicite.
            yield {"type": "error",
                   "message": "Tous les morceaux ont échoué auprès du fournisseur IA. "
                              f"Dernier message : {last_error or 'inconnu'}"}
            return

        # Consolidation finale : fusion des quasi-doublons inter-morceaux
        # (best-effort, voir _consolidate). Inutile sur un import mono-morceau.
        if total > 1:
            yield {"type": "consolidating", "total": total}
            async for kind, _ in with_heartbeat(self._consolidate(merger)):
                if kind == "heartbeat":
                    yield {"type": "heartbeat", "current": total, "total": total}

        yield {
            "type": "done",
            "arcs": _serialize_arcs(merger.result()),
            "npcs": [{"name": n.name, "description": n.description} for n in merger.npcs()],
            "page_count": doc.page_count,
            "ocr_page_count": doc.ocr_page_count,
            "skipped": skipped,
        }

    # --- Consolidation finale (fusion des quasi-doublons) ---------------------

    async def _consolidate(self, merger: _TreeMerger) -> None:
        """Une passe LLM sur le squelette pour fusionner les quasi-doublons.

        BEST-EFFORT : toute erreur (LLM indisponible, JSON invalide, noms
        inconnus) laisse l'arbre tel quel — la consolidation ne peut qu'améliorer,
        jamais bloquer un import.
        """
        _, chapters, scenes = merger.counts()
        if chapters + scenes < 3:
            return  # rien à dédoublonner sur un arbre minuscule
        skeleton = merger.skeleton_text()
        try:
            raw = await generate_with_retry(
                self._llm, _CONSOLIDATE_PROMPT.format(skeleton=skeleton),
                output_format="json", temperature=0.0)
        except Exception as exc:  # noqa: BLE001 — best-effort STRICT : une erreur ici
            # (LLM, réseau, bug) ne doit JAMAIS faire perdre un import terminé.
            logger.warning("Consolidation ignorée (échec) : %s", exc)
            return
        parsed, _ = load_json_object(raw)
        if not isinstance(parsed, dict):
            logger.warning("Consolidation ignorée (réponse non-JSON).")
            return
        merged = 0
        for cm in parsed.get("chapter_merges") or []:
            if isinstance(cm, dict) and merger.merge_chapters(
                    str(cm.get("into") or ""), list(cm.get("merge") or [])):
                merged += 1
        for sm in parsed.get("scene_merges") or []:
            if isinstance(sm, dict) and merger.merge_scenes(
                    str(sm.get("chapter") or ""), str(sm.get("into") or ""),
                    list(sm.get("merge") or [])):
                merged += 1
        if merged:
            logger.info("Consolidation : %s fusion(s) de quasi-doublons appliquée(s).", merged)

    # --- MAP : un morceau → sous-arbre ---------------------------------------

    async def _map_chunk(
        self, chunk: str, *, index: int, total: int, toc_block: str = ""
    ) -> dict:
        """Phase MAP d'un morceau → {"arcs": [...], "npcs": [...]}."""
        return await self._extract_payload(
            chunk, index=index, total=total, depth=0, toc_block=toc_block)

    async def _extract_payload(
        self, text: str, *, index: int, total: int, depth: int, toc_block: str = ""
    ) -> dict:
        """Extrait l'arborescence + les PNJ d'un texte. Si la SORTIE est tronquée,
        retraite le texte en DEUX moitiés et concatène — le `_TreeMerger` final
        dédoublonne par nom (un arc/chapitre coupé entre les moitiés est recollé)."""
        toc_section = _TOC_BLOCK.format(toc=toc_block) if toc_block else ""
        prompt = (
            _MAP_SYSTEM.format(default_arc=_DEFAULT_ARC_NAME)
            + toc_section
            + f"\n\n--- EXTRAIT {index + 1}/{total} ---\n{text}\n\n"
            "Renvoie maintenant le JSON de l'arborescence."
        )
        raw = await generate_with_retry(
            self._llm, prompt, output_format="json", temperature=_TEMPERATURE)
        payload, truncated = self._parse_payload(raw, index=index)

        if truncated and depth < _MAX_SPLIT_DEPTH:
            left, right = split_in_half(text)
            if left and right:
                logger.info(
                    "Morceau %s : sortie tronquée → re-découpage en 2 moitiés (niveau %s).",
                    index, depth + 1)
                a = await self._extract_payload(
                    left, index=index, total=total, depth=depth + 1, toc_block=toc_block)
                b = await self._extract_payload(
                    right, index=index, total=total, depth=depth + 1, toc_block=toc_block)
                return {"arcs": a["arcs"] + b["arcs"], "npcs": a["npcs"] + b["npcs"]}
        if truncated:
            logger.warning(
                "Morceau %s : sortie tronquée, profondeur max atteinte — partiel conservé.", index)
        return payload

    @staticmethod
    def _parse_payload(raw: str, *, index: int) -> tuple[dict, bool]:
        """Parse robuste → ({"arcs", "npcs"}, tronqué). `tronqué`=True si partiel."""
        empty = {"arcs": [], "npcs": []}
        parsed, recovered = load_json_object(raw)
        if parsed is None:
            truncated = looks_like_truncated_json(raw)
            if not truncated:
                logger.warning(
                    "Morceau %s : aucun objet JSON exploitable, ignoré. "
                    "Début de la réponse du modèle : %r",
                    index, (raw or "").strip()[:300] or "(réponse VIDE)")
            return empty, truncated
        if isinstance(parsed, dict):
            arcs = parsed.get("arcs", [])
            npcs = parsed.get("npcs", [])
            return {
                "arcs": arcs if isinstance(arcs, list) else [],
                "npcs": npcs if isinstance(npcs, list) else [],
            }, recovered
        return empty, recovered


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
