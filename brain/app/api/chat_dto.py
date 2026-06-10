"""DTOs Pydantic du chat contextuel — frontière HTTP avec le Core Java.

C'est ici (et seulement ici, avec les autres modules de `app.api`) qu'on
utilise Pydantic : le domaine ne voit que des dataclasses (voir chat_mapping).
"""
from pydantic import BaseModel, Field


class ChatMessageDTO(BaseModel):
    """Un message de la conversation. Rôles acceptés : user, assistant, system."""

    role: str = Field(pattern="^(user|assistant|system)$")
    content: str


class PageSummaryDTO(BaseModel):
    """Résumé enrichi d'une page : identité + contenu + interconnexions.

    Depuis b9 : values/tags/related_page_titles sont optionnels côté JSON —
    le Core Java ne les sérialise que s'ils sont non-vides (payload léger
    pour un Lore avec beaucoup de pages vierges).
    """

    title: str
    template_name: str
    values: dict[str, str] = Field(default_factory=dict)
    tags: list[str] = Field(default_factory=list)
    related_page_titles: list[str] = Field(default_factory=list)


class LoreContextDTO(BaseModel):
    """Carte structurelle du Lore avec contenu des pages (b9+)."""

    lore_name: str
    lore_description: str | None = None
    folders: dict[str, list[PageSummaryDTO]] = Field(default_factory=dict)
    tags: list[str] = Field(default_factory=list)


class PageContextDTO(BaseModel):
    """Contexte d'une page spécifique pour focaliser le chat (optionnel)."""

    title: str
    template_name: str
    template_fields: list[str] = Field(default_factory=list)
    values: dict[str, str] = Field(default_factory=dict)


class SceneBranchHintDTO(BaseModel):
    """Indice d'une branche narrative (le Core a deja resolu le nom cible)."""

    label: str
    target_scene_name: str
    condition: str | None = None


class RoomBranchHintDTO(BaseModel):
    """Sortie d'une pièce vers une autre pièce du même lieu (donjon)."""

    label: str
    target_room_name: str
    condition: str | None = None


class RoomSummaryDTO(BaseModel):
    """Pièce d'un lieu explorable. Omise par le Core si la scène est classique."""

    name: str
    floor: int | None = None
    description: str | None = None
    enemies: str | None = None
    branches: list[RoomBranchHintDTO] = Field(default_factory=list)


class SceneSummaryDTO(BaseModel):
    """Résumé d'une scène : nom + description courte (synopsis)."""

    name: str
    description: str | None = None
    # Optionnel : le Core Java ne serialise illustration_count QUE si > 0
    # (payload plus leger). Defaut 0 = pas d'illustrations ou champ absent.
    illustration_count: int = 0
    # Branches narratives sortantes, omises cote Core si vides.
    branches: list[SceneBranchHintDTO] = Field(default_factory=list)
    # Pièces du lieu explorable, omises par Core si scène classique.
    rooms: list[RoomSummaryDTO] = Field(default_factory=list)


class ChapterSummaryDTO(BaseModel):
    """Résumé d'un chapitre : nom + description courte + ses scènes."""

    name: str
    description: str | None = None
    scenes: list[SceneSummaryDTO] = Field(default_factory=list)
    illustration_count: int = 0


class ArcSummaryDTO(BaseModel):
    """Résumé d'un arc narratif : nom + description courte + ses chapitres."""

    name: str
    description: str | None = None
    chapters: list[ChapterSummaryDTO] = Field(default_factory=list)
    illustration_count: int = 0


class CharacterSummaryDTO(BaseModel):
    """Résumé d'un PJ : nom + snippet. Pas de fiche complète au niveau résumé."""

    name: str
    snippet: str = ""


class NpcSummaryDTO(BaseModel):
    """Résumé d'un PNJ : symétrique à CharacterSummaryDTO."""

    name: str
    snippet: str = ""


class CampaignContextDTO(BaseModel):
    """Carte narrative enrichie : arcs → chapitres → scènes avec synopsis."""

    campaign_name: str
    campaign_description: str | None = None
    arcs: list[ArcSummaryDTO] = Field(default_factory=list)
    characters: list[CharacterSummaryDTO] = Field(default_factory=list)
    npcs: list[NpcSummaryDTO] = Field(default_factory=list)


class NarrativeEntityDTO(BaseModel):
    """Entité narrative (arc/chapter/scene/character) en cours d'édition — focus optionnel."""

    entity_type: str = Field(pattern="^(arc|chapter|scene|character|npc)$")
    title: str
    fields: dict[str, str] = Field(default_factory=dict)


class GameSystemContextDTO(BaseModel):
    """Règles de JDR présélectionnées par le Core (filtrées par intent).

    Les sections sont un dict titre_H2 → contenu_markdown. Peuvent être
    vides si aucune section ne matchait l'intent de génération courant.
    """

    system_name: str
    system_description: str | None = None
    sections: dict[str, str] = Field(default_factory=dict)


class JournalEntrySummaryDTO(BaseModel):
    """Une entrée du journal de session.

    `source_session_name` est présent uniquement pour les évènements issus
    des sessions précédentes — sert à ancrer temporellement dans le prompt.
    """

    type: str
    content: str
    occurred_at: str | None = None
    source_session_name: str | None = None


class QuestSummaryDTO(BaseModel):
    """Résumé d'une quête (Chapter dans un Arc HUB). Voir QuestSummary côté domaine."""

    name: str
    arc_name: str
    description: str | None = None


class SessionContextDTO(BaseModel):
    """Contexte d'une Session de jeu en cours (Play Context).

    Combine le journal complet (`entries`), les EVENTs des sessions précédentes
    (`previous_events`), et — depuis l'ajout du mode Hub — l'état des quêtes
    Hub de la campagne (disponibles / en cours / verrouillées) plus les flags
    narratifs actuellement actifs.
    """

    session_name: str
    active: bool
    started_at: str | None = None
    entries: list[JournalEntrySummaryDTO] = Field(default_factory=list)
    previous_events: list[JournalEntrySummaryDTO] = Field(default_factory=list)
    available_quests: list[QuestSummaryDTO] = Field(default_factory=list)
    in_progress_quests: list[QuestSummaryDTO] = Field(default_factory=list)
    locked_quest_titles: list[str] = Field(default_factory=list)
    active_flags: list[str] = Field(default_factory=list)


class ChatStreamRequestDTO(BaseModel):
    """Requête de chat streamé : historique + contextes structurels.

    Les contextes (lore, page, campaign, narrative_entity, session) sont
    optionnels, mais au moins l'un des contextes "racines" (lore_context,
    campaign_context ou session_context) doit être fourni. Le validateur
    `check_scope` applique cette règle à la frontière HTTP.
    """

    messages: list[ChatMessageDTO] = Field(min_length=1)
    lore_context: LoreContextDTO | None = None
    page_context: PageContextDTO | None = None
    campaign_context: CampaignContextDTO | None = None
    narrative_entity: NarrativeEntityDTO | None = None
    game_system_context: GameSystemContextDTO | None = None
    session_context: SessionContextDTO | None = None

    def has_scope(self) -> bool:
        """Vrai si au moins un contexte racine (Lore, Campagne ou Session) est fourni."""
        return (
            self.lore_context is not None
            or self.campaign_context is not None
            or self.session_context is not None
        )
