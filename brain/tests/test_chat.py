"""Tests de la construction du system prompt du chat (app.application.chat).

Assertions par INCLUSION (présence des données/sections clés) plutôt que sur le
texte exact des consignes : robuste aux retouches de formulation, tout en
vérifiant que chaque contexte est bien injecté.
"""
from __future__ import annotations

from app.application.chat import ChatUseCase
from app.domain.models import (
    ArcSummary,
    CampaignStructuralContext,
    ChapterSummary,
    CharacterSummary,
    ChatMessage,
    GameSystemContext,
    JournalEntrySummary,
    LoreStructuralContext,
    NarrativeEntityContext,
    NpcSummary,
    PageContext,
    PageSummary,
    SceneSummary,
    SessionContext,
)


def _build(**kw) -> str:
    return ChatUseCase(None).build_system_prompt(**kw)


def _empty_lore() -> LoreStructuralContext:
    return LoreStructuralContext(lore_name="L", lore_description=None, folders={}, tags=[])


def test_base_prompt_without_context_is_non_empty():
    assert len(_build()) > 0


def test_lore_block_renders_pages_with_values_tags_and_links():
    lore = LoreStructuralContext(
        lore_name="Eldoria", lore_description="un monde sombre",
        folders={"PNJ": [PageSummary(
            title="Aragorn", template_name="Personnage",
            values={"apparence": "grand et noble"}, tags=["héros"],
            related_page_titles=["Gondor"])]},
        tags=["dark-fantasy"])
    p = _build(lore_context=lore)
    assert "Eldoria" in p
    assert "un monde sombre" in p
    assert "Aragorn" in p
    assert "apparence" in p and "grand et noble" in p
    assert "héros" in p
    assert "Gondor" in p


def test_empty_lore_signals_vide():
    assert "Lore vide" in _build(lore_context=_empty_lore())


def test_page_context_block_lists_fields_and_empty_marker():
    page = PageContext(title="Aragorn", template_name="Personnage",
                       template_fields=["apparence", "histoire"],
                       values={"apparence": "grand"})
    p = _build(page_context=page)
    assert "PAGE EN COURS" in p
    assert "Aragorn" in p
    assert "apparence" in p
    assert "(vide)" in p  # 'histoire' sans valeur


def test_campaign_block_with_arc_and_empty_pj_npc_and_no_lore_note():
    camp = CampaignStructuralContext(
        campaign_name="La Malédiction", campaign_description="horreur gothique",
        arcs=[ArcSummary(name="Acte I", description="intro",
                         chapters=[ChapterSummary(name="Ch1", description="",
                                                  scenes=[SceneSummary(name="Sc1", description="")])])],
        characters=[], npcs=[])
    p = _build(campaign_context=camp)
    assert "CAMPAGNE COURANTE" in p
    assert "La Malédiction" in p
    assert "Acte I" in p
    assert "aucune fiche" in p       # pas de PJ
    assert "aucun univers" in p      # pas de lore lié


def test_campaign_with_characters_npcs_and_lore_present_note():
    camp = CampaignStructuralContext(
        campaign_name="C", campaign_description=None, arcs=[],
        characters=[CharacterSummary(name="Tav", snippet="roublarde")],
        npcs=[NpcSummary(name="Strahd", snippet="vampire de Barovia")])
    p = _build(campaign_context=camp, lore_context=_empty_lore())
    assert "Tav" in p and "roublarde" in p
    assert "Strahd" in p and "vampire de Barovia" in p
    assert "liée à l'univers" in p


def test_game_system_narrative_and_session_sections_injected():
    gs = GameSystemContext(system_name="Nimble", system_description=None,
                           sections={"Combat": "règles de combat"})
    narr = NarrativeEntityContext(entity_type="scene", title="L'auberge du Portail",
                                  fields={"ambiance": "tendue"})
    sess = SessionContext(
        session_name="Séance 3", active=True, started_at=None,
        entries=[JournalEntrySummary(type="EVENT", content="Le pont s'effondre", occurred_at=None)],
        previous_events=[])
    p = _build(lore_context=_empty_lore(), game_system_context=gs,
               narrative_entity=narr, session_context=sess)
    assert "Nimble" in p
    assert "L'auberge du Portail" in p
    assert "Séance 3" in p
    assert "Le pont s'effondre" in p


async def test_stream_passes_built_prompt_to_llm():
    class FakeChatLLM:
        def __init__(self) -> None:
            self.system_prompt = None

        async def stream_chat(self, messages, *, system_prompt=None, temperature=None):
            self.system_prompt = system_prompt
            yield "tok"

    llm = FakeChatLLM()
    out = [t async for t in ChatUseCase(llm).stream(
        [ChatMessage(role="user", content="salut")],
        lore_context=LoreStructuralContext("Eldoria", None, {}, []))]
    assert out == ["tok"]
    assert "Eldoria" in llm.system_prompt
