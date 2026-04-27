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
class SceneSummary:
    """Résumé d'une scène : nom + description courte + illustrations + branches."""

    name: str
    description: str | None
    # Depuis l'etape 6 : permet a l'IA de savoir qu'une scene a des illustrations
    # attachees. 0 par defaut pour retrocompat si le Core n'envoie rien.
    illustration_count: int = 0
    # Connexions narratives sortantes (livre dont vous etes le heros).
    branches: list[SceneBranchHint] = field(default_factory=list)


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
