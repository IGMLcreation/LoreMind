"""Modèles de domaine pour le cas d'usage de génération de page LoreMind.

On utilise @dataclass (pas Pydantic) pour garder le domaine exempt de toute
dépendance framework. Pydantic apparaît uniquement aux frontières : DTOs HTTP
dans `main.py`, Settings dans `core/config.py`.
"""
from dataclasses import dataclass, field


@dataclass(frozen=True)
class PageGenerationContext:
    """Contexte métier à fournir au LLM pour générer une page LoreMind.

    Les champs correspondent aux entités du Lore Context côté Core Java :
    - lore_*        : l'univers (Lore)
    - folder_name   : le dossier (LoreNode) qui catégorise la page
    - template_*    : le gabarit qui liste les champs à remplir
    - page_title    : le titre de la page à créer
    """

    lore_name: str
    folder_name: str
    template_name: str
    template_fields: list[str]
    page_title: str
    lore_description: str | None = None


@dataclass(frozen=True)
class PageGenerationResult:
    """Résultat métier : une valeur textuelle générée par champ du template.

    La clé du dict est le nom du champ (ex: "apparence"), la valeur est
    le contenu généré par le LLM. Cohérent avec la structure
    `Page.values: Map<String,String>` côté Core Java.
    """

    values: dict[str, str]


@dataclass(frozen=True)
class ChatMessage:
    """Message d'une conversation — rôle + contenu textuel.

    Rôles possibles (OpenAI/Ollama compatibles) :
    - "system"    : prompt système (contexte, instructions)
    - "user"      : message de l'utilisateur
    - "assistant" : réponse précédente du LLM
    """

    role: str
    content: str


@dataclass(frozen=True)
class PageSummary:
    """Résumé enrichi d'une page du Lore, projeté pour alimenter le prompt.

    Depuis b9 : on ne se contente plus du nom + template, on embarque aussi
    les valeurs des champs dynamiques (tronquées côté Core Java à 500 car.),
    les tags, et les titres des pages liées (les IDs techniques sont déjà
    résolus en titres lisibles côté Java — voir LoreStructuralContextBuilder).

    Les notes privées du MJ restent volontairement absentes ici (confinées
    à leur page d'édition via PageContext quand l'utilisateur y travaille).
    """

    title: str
    template_name: str
    values: dict[str, str]
    tags: list[str]
    related_page_titles: list[str]


@dataclass(frozen=True)
class LoreStructuralContext:
    """Carte structurelle enrichie d'un Lore pour nourrir l'IA.

    Depuis b9 : chaque page expose son contenu (values, tags, liens) via
    PageSummary. Le prompt n'est plus qu'une table des matières — c'est
    une encyclopédie condensée que le LLM peut directement citer.

    Le dict `folders` est indexé par nom de dossier et mappe vers la liste
    des pages qu'il contient (PageSummary).
    """

    lore_name: str
    lore_description: str | None
    folders: dict[str, list[PageSummary]]
    tags: list[str]


@dataclass(frozen=True)
class PageContext:
    """Contexte d'une page spécifique en cours d'édition.

    Injecté dans le system prompt pour focaliser le chat sur CETTE page
    précise : son template, ses champs, ses valeurs actuelles. Permet à
    l'IA d'éviter de parler d'autres pages du Lore par mégarde.

    Complémentaire de `LoreStructuralContext` : l'un donne la carte
    générale (toutes les pages existantes), l'autre zoome sur la page
    en cours de discussion.
    """

    title: str
    template_name: str
    template_fields: list[str]
    values: dict[str, str]


@dataclass(frozen=True)
class SceneBranchHint:
    """Indice d'une branche narrative vers une autre scène du même chapitre.

    Le Core Java résout déjà `targetSceneId` en nom humain avant l'envoi :
    l'IA ne voit donc jamais d'UUID, seulement des noms qu'elle peut citer.
    """

    label: str
    target_scene_name: str
    condition: str | None = None


@dataclass(frozen=True)
class RoomBranchHint:
    """Indice d'une sortie entre pièces (donjon). target_room_name déjà résolu côté Core."""

    label: str
    target_room_name: str
    condition: str | None = None


@dataclass(frozen=True)
class RoomSummary:
    """Pièce d'un lieu explorable. Projection plate pour le prompt IA (pas de notes MJ)."""

    name: str
    floor: int | None = None
    description: str | None = None
    enemies: str | None = None
    branches: list[RoomBranchHint] = field(default_factory=list)


@dataclass(frozen=True)
class SceneSummary:
    """Résumé d'une scène : nom + description courte + illustrations + branches + pièces."""

    name: str
    description: str | None
    # Depuis l'etape 6 : permet a l'IA de savoir qu'une scene a des illustrations
    # attachees. 0 par defaut pour retrocompat si le Core n'envoie rien.
    illustration_count: int = 0
    # Connexions narratives sortantes (livre dont vous etes le heros).
    branches: list[SceneBranchHint] = field(default_factory=list)
    # Pièces du lieu explorable (vide = scène classique).
    rooms: list[RoomSummary] = field(default_factory=list)


@dataclass(frozen=True)
class ChapterSummary:
    """Résumé d'un chapitre : nom + description courte + ses scènes."""

    name: str
    description: str | None
    scenes: list[SceneSummary]
    illustration_count: int = 0


@dataclass(frozen=True)
class ArcSummary:
    """Résumé d'un arc narratif : nom + description courte + ses chapitres."""

    name: str
    description: str | None
    chapters: list[ChapterSummary]
    illustration_count: int = 0


@dataclass(frozen=True)
class CampaignStructuralContext:
    """Carte narrative enrichie d'une Campagne pour nourrir l'IA.

    Jumeau de LoreStructuralContext côté Campaign. On décrit l'arbre
    arcs → chapitres → scènes en donnant le NOM + une DESCRIPTION courte
    (synopsis) à chaque niveau. Les champs longs (notes MJ, narration
    joueur, combat) restent réservés à l'entité focus via
    NarrativeEntityContext. Ordre narratif préservé dans la liste `arcs`.
    """

    campaign_name: str
    campaign_description: str | None
    arcs: list[ArcSummary]
    characters: list["CharacterSummary"] = field(default_factory=list)
    npcs: list["NpcSummary"] = field(default_factory=list)


@dataclass(frozen=True)
class CharacterSummary:
    """Résumé d'un PJ : nom + snippet court extrait du markdown de la fiche.

    La fiche complète n'est JAMAIS dans ce résumé — elle n'arrive que si le PJ
    est l'entité focus (via NarrativeEntityContext entity_type="character").
    Ça plafonne le coût token à ~40 tokens/PJ quel que soit le détail des fiches.
    """

    name: str
    snippet: str


@dataclass(frozen=True)
class NpcSummary:
    """Résumé d'un PNJ : symétrique à CharacterSummary.

    Permet à l'IA de connaître les PNJ d'une campagne (nom + snippet) sans
    injecter leurs fiches complètes. Évolution prévue : entity_type="npc"
    pour focus sur la fiche complète.
    """

    name: str
    snippet: str


@dataclass(frozen=True)
class NarrativeEntityContext:
    """Contexte d'une entité narrative précise en cours d'édition.

    Équivalent de PageContext côté Campaign. Focalise l'IA sur un Arc,
    Chapter ou Scene en particulier. `entity_type` ∈ {"arc","chapter","scene"}.
    Les `fields` sont une map ordonnée nomChamp → valeurActuelle (chaîne
    vide si non renseigné).
    """

    entity_type: str
    title: str
    fields: dict[str, str]


@dataclass(frozen=True)
class GameSystemContext:
    """Règles d'un système de JDR (D&D, Nimble, homebrew...) injectées
    dans le system prompt pour que l'IA respecte les mécaniques du jeu.

    Les sections ont été présélectionnées côté Core selon l'intent
    (SCENE → combat/PNJ, CHAPTER → combat/classes, ARC → lore/factions,
    GENERIC → toutes). Indexées par titre H2 original.

    Campagne uniquement au MVP : jamais présent sur un chat Lore.
    """

    system_name: str
    system_description: str | None
    sections: dict[str, str]


@dataclass(frozen=True)
class JournalEntrySummary:
    """Une entrée du journal d'une Session.

    `source_session_name` n'est renseigné que pour les entrées issues de
    sessions précédentes (option 3 : continuité narrative entre séances).
    """

    type: str
    content: str
    occurred_at: str | None
    source_session_name: str | None = None


@dataclass(frozen=True)
class QuestSummary:
    """Résumé d'une quête (Chapter dans un Arc HUB) pour le system prompt.

    Volontairement sans notes MJ ni statut texte : c'est déjà classé côté Core
    dans available_quests / in_progress_quests / locked_quest_titles.
    """

    name: str
    arc_name: str
    description: str | None = None


@dataclass(frozen=True)
class SessionContext:
    """Contexte d'une Session de jeu en cours (Play Context).

    Combine plusieurs niveaux :
    - `entries` : journal COMPLET de la session courante (cappé ~80 entrées)
    - `previous_events` : EVENTs marquants des sessions précédentes (continuité)
    - `available_quests` / `in_progress_quests` : quêtes du Hub ouvertes
    - `locked_quest_titles` : titres seuls des quêtes verrouillées (anti-spoiler)
    - `active_flags` : noms des flags de campagne actuellement à true
    """

    session_name: str
    active: bool
    started_at: str | None
    entries: list[JournalEntrySummary]
    previous_events: list[JournalEntrySummary]
    available_quests: list[QuestSummary] = field(default_factory=list)
    in_progress_quests: list[QuestSummary] = field(default_factory=list)
    locked_quest_titles: list[str] = field(default_factory=list)
    active_flags: list[str] = field(default_factory=list)


# ─────────────────────── Import de PDF (règles → GameSystem) ───────────────────────


@dataclass(frozen=True)
class ExtractedPage:
    """Texte extrait d'UNE page de PDF, avec la trace de la méthode utilisée.

    `used_ocr=True` signale que la page n'avait pas de couche texte exploitable
    (born-digital absent) et a donc été rasterisée puis passée à l'OCR. Permet
    au CLI/diagnostic de dire à l'utilisateur si son PDF est "texte" ou "scan".
    """

    index: int  # 0-based
    text: str
    used_ocr: bool


@dataclass(frozen=True)
class TocEntry:
    """Une entrée de la table des matières (bookmarks/outline) du PDF.

    `level` : profondeur 1-based (1 = chapitre, 2 = section…). `page` : 1-based.
    """

    level: int
    title: str
    page: int


@dataclass(frozen=True)
class ExtractedDocument:
    """Résultat brut de l'extraction d'un PDF : une entrée par page."""

    pages: list[ExtractedPage]
    # Table des matières (bookmarks PDF). Vide si le PDF n'en a pas — fréquent
    # pour les scans ; les livres born-digital en ont presque toujours une.
    toc: list[TocEntry] = field(default_factory=list)

    @property
    def page_count(self) -> int:
        return len(self.pages)

    @property
    def ocr_page_count(self) -> int:
        return sum(1 for p in self.pages if p.used_ocr)

    @property
    def full_text(self) -> str:
        """Concatène le texte de toutes les pages, séparées par un saut double."""
        return "\n\n".join(p.text for p in self.pages if p.text.strip())


@dataclass(frozen=True)
class RulesImportResult:
    """Proposition structurée de règles : sections markdown indexées par titre.

    `sections` = {titre H2 → contenu markdown}. C'est une PROPOSITION : rien
    n'est persisté côté Core tant que l'utilisateur n'a pas validé/édité.
    `page_count` / `ocr_page_count` remontent au diagnostic d'extraction.
    """

    sections: dict[str, str]
    page_count: int
    ocr_page_count: int

    def to_markdown(self) -> str:
        """Assemble les sections en un markdown monolithique (## titre + contenu).

        Format aligné sur `GameSystem.rulesMarkdown` côté Core (découpé par H2).
        """
        blocks = [f"## {title}\n\n{content.strip()}" for title, content in self.sections.items()]
        return "\n\n".join(blocks).strip() + "\n"


# ─────────────────────── Import de PDF de campagne (arbre arc→chapitre→scène) ──────────────


@dataclass(frozen=True)
class RoomProposal:
    """Pièce d'un lieu explorable (donjon) proposée pour une scène."""

    name: str
    description: str
    enemies: str = ""
    loot: str = ""


@dataclass(frozen=True)
class SceneProposal:
    """Scène proposée. `rooms` non vide => donjon/lieu explorable.

    On capture aussi, quand le livre les fournit, le texte d'encadré « à lire aux
    joueurs » (`player_narration`) et les secrets/développement MJ (`gm_notes`).
    """

    name: str
    description: str
    player_narration: str = ""
    gm_notes: str = ""
    rooms: list[RoomProposal] = field(default_factory=list)


@dataclass(frozen=True)
class ChapterProposal:
    """Chapitre proposé : nom + synopsis + ses scènes."""

    name: str
    description: str
    scenes: list[SceneProposal] = field(default_factory=list)


@dataclass(frozen=True)
class ArcProposal:
    """Arc proposé : nom + synopsis + type (LINEAR/HUB) + ses chapitres."""

    name: str
    description: str
    arc_type: str = "LINEAR"
    chapters: list[ChapterProposal] = field(default_factory=list)


@dataclass(frozen=True)
class CampaignImportResult:
    """Proposition d'arborescence narrative extraite d'un PDF de campagne.

    PROPOSITION non persistée : l'UI laisse l'utilisateur réviser/éditer l'arbre
    avant la création effective des arcs/chapitres/scènes côté Core.
    """

    arcs: list[ArcProposal]
    page_count: int
    ocr_page_count: int

    def counts(self) -> tuple[int, int, int]:
        """(nb arcs, nb chapitres, nb scènes) — pour le diagnostic / la progression."""
        chapters = sum(len(a.chapters) for a in self.arcs)
        scenes = sum(len(c.scenes) for a in self.arcs for c in a.chapters)
        return len(self.arcs), chapters, scenes
