"""Mapping DTO → domaine (couche anti-corruption de la frontière HTTP).

Traduit les DTOs Pydantic du chat contextuel en dataclasses du domaine :
le cœur métier ne dépend ainsi jamais de Pydantic ni du format JSON du Core.
"""
from app.api.chat_dto import (
    CampaignContextDTO,
    GameSystemContextDTO,
    JournalEntrySummaryDTO,
    LoreContextDTO,
    NarrativeEntityDTO,
    PageContextDTO,
    PageSummaryDTO,
    QuestSummaryDTO,
    SessionContextDTO,
)
from app.domain.models import (
    ArcSummary,
    CampaignStructuralContext,
    ChapterSummary,
    CharacterSummary,
    GameSystemContext,
    JournalEntrySummary,
    LoreStructuralContext,
    NarrativeEntityContext,
    NpcSummary,
    PageContext,
    PageSummary,
    QuestSummary,
    RoomBranchHint,
    RoomSummary,
    SceneBranchHint,
    SceneSummary,
    SessionContext,
)


def to_lore_context(dto: LoreContextDTO | None) -> LoreStructuralContext | None:
    if dto is None:
        return None
    return LoreStructuralContext(
        lore_name=dto.lore_name,
        lore_description=dto.lore_description,
        folders={
            folder: [_to_page_summary(p) for p in pages]
            for folder, pages in dto.folders.items()
        },
        tags=dto.tags,
    )


def _to_page_summary(dto: PageSummaryDTO) -> PageSummary:
    return PageSummary(
        title=dto.title,
        template_name=dto.template_name,
        values=dict(dto.values),
        tags=list(dto.tags),
        related_page_titles=list(dto.related_page_titles),
    )


def to_page_context(dto: PageContextDTO | None) -> PageContext | None:
    if dto is None:
        return None
    return PageContext(
        title=dto.title,
        template_name=dto.template_name,
        template_fields=dto.template_fields,
        values=dto.values,
    )


def to_campaign_context(dto: CampaignContextDTO | None) -> CampaignStructuralContext | None:
    if dto is None:
        return None
    arcs = [
        ArcSummary(
            name=arc.name,
            description=arc.description,
            illustration_count=arc.illustration_count,
            chapters=[
                ChapterSummary(
                    name=ch.name,
                    description=ch.description,
                    illustration_count=ch.illustration_count,
                    scenes=[
                        SceneSummary(
                            name=sc.name,
                            description=sc.description,
                            illustration_count=sc.illustration_count,
                            branches=[
                                SceneBranchHint(
                                    label=br.label,
                                    target_scene_name=br.target_scene_name,
                                    condition=br.condition,
                                )
                                for br in sc.branches
                            ],
                            rooms=[
                                RoomSummary(
                                    name=room.name,
                                    floor=room.floor,
                                    description=room.description,
                                    enemies=room.enemies,
                                    branches=[
                                        RoomBranchHint(
                                            label=rb.label,
                                            target_room_name=rb.target_room_name,
                                            condition=rb.condition,
                                        )
                                        for rb in room.branches
                                    ],
                                )
                                for room in sc.rooms
                            ],
                        )
                        for sc in ch.scenes
                    ],
                )
                for ch in arc.chapters
            ],
        )
        for arc in dto.arcs
    ]
    characters = [
        CharacterSummary(name=c.name, snippet=c.snippet)
        for c in dto.characters
    ]
    npcs = [
        NpcSummary(name=n.name, snippet=n.snippet)
        for n in dto.npcs
    ]
    return CampaignStructuralContext(
        campaign_name=dto.campaign_name,
        campaign_description=dto.campaign_description,
        arcs=arcs,
        characters=characters,
        npcs=npcs,
    )


def to_narrative_entity(dto: NarrativeEntityDTO | None) -> NarrativeEntityContext | None:
    if dto is None:
        return None
    return NarrativeEntityContext(
        entity_type=dto.entity_type,
        title=dto.title,
        fields=dict(dto.fields),
    )


def to_game_system_context(dto: GameSystemContextDTO | None) -> GameSystemContext | None:
    if dto is None:
        return None
    return GameSystemContext(
        system_name=dto.system_name,
        system_description=dto.system_description,
        sections=dict(dto.sections),
    )


def to_session_context(dto: SessionContextDTO | None) -> SessionContext | None:
    if dto is None:
        return None
    return SessionContext(
        session_name=dto.session_name,
        active=dto.active,
        started_at=dto.started_at,
        entries=[_to_journal_entry(e) for e in dto.entries],
        previous_events=[_to_journal_entry(e) for e in dto.previous_events],
        available_quests=[_to_quest_summary(q) for q in dto.available_quests],
        in_progress_quests=[_to_quest_summary(q) for q in dto.in_progress_quests],
        locked_quest_titles=list(dto.locked_quest_titles),
        active_flags=list(dto.active_flags),
    )


def _to_quest_summary(dto: QuestSummaryDTO) -> QuestSummary:
    return QuestSummary(
        name=dto.name,
        arc_name=dto.arc_name,
        description=dto.description,
    )


def _to_journal_entry(dto: JournalEntrySummaryDTO) -> JournalEntrySummary:
    return JournalEntrySummary(
        type=dto.type,
        content=dto.content,
        occurred_at=dto.occurred_at,
        source_session_name=dto.source_session_name,
    )
