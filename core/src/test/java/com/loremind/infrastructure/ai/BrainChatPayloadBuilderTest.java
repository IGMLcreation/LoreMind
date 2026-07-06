package com.loremind.infrastructure.ai;

import com.loremind.domain.generationcontext.CampaignStructuralContext;
import com.loremind.domain.generationcontext.CampaignStructuralContext.ArcSummary;
import com.loremind.domain.generationcontext.CampaignStructuralContext.BranchHint;
import com.loremind.domain.generationcontext.CampaignStructuralContext.ChapterSummary;
import com.loremind.domain.generationcontext.CampaignStructuralContext.SceneSummary;
import com.loremind.domain.generationcontext.ChatMessage;
import com.loremind.domain.generationcontext.ChatRequest;
import com.loremind.domain.generationcontext.CampaignStructuralContext.CharacterSummary;
import com.loremind.domain.generationcontext.CampaignStructuralContext.NpcSummary;
import com.loremind.domain.generationcontext.CampaignStructuralContext.RoomBranchHint;
import com.loremind.domain.generationcontext.CampaignStructuralContext.RoomSummary;
import com.loremind.domain.generationcontext.GameSystemContext;
import com.loremind.domain.generationcontext.LoreStructuralContext;
import com.loremind.domain.generationcontext.LoreStructuralContext.PageSummary;
import com.loremind.domain.generationcontext.NarrativeEntityContext;
import com.loremind.domain.generationcontext.PageContext;
import com.loremind.domain.generationcontext.SessionContext;
import com.loremind.domain.generationcontext.SessionContext.JournalEntrySummary;
import com.loremind.domain.generationcontext.SessionContext.QuestSummary;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires pour BrainChatPayloadBuilder.
 * Verifie la traduction ChatRequest (domaine) -> dict JSON (schema Brain).
 * Points critiques :
 *  - omission conditionnelle des contextes null (alignement Pydantic Optional),
 *  - omission conditionnelle des sous-champs vides (values/tags/branches),
 *  - sérialisation récursive arc -> chapter -> scene -> branches.
 */
class BrainChatPayloadBuilderTest {

    private final BrainChatPayloadBuilder builder = new BrainChatPayloadBuilder();

    private final List<ChatMessage> sampleMessages = List.of(
            new ChatMessage("user", "Bonjour"),
            new ChatMessage("assistant", "Salut"));

    /** Helper : cast generique d'un Object vers Map<String,Object>. Evite les chaines de casts illisibles. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return (Map<String, Object>) o;
    }

    /** Helper : recupere le premier element d'une liste-de-maps imbriquee sous une cle. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstOf(Map<String, Object> parent, String key) {
        return ((List<Map<String, Object>>) parent.get(key)).get(0);
    }

    // ---------- messages + omission des contextes null ---------------------

    @Test
    void build_withMessagesOnly_omitsAllOptionalContexts() {
        ChatRequest req = ChatRequest.builder().messages(sampleMessages).build();

        Map<String, Object> payload = builder.build(req);

        assertTrue(payload.containsKey("messages"));
        assertFalse(payload.containsKey("lore_context"));
        assertFalse(payload.containsKey("page_context"));
        assertFalse(payload.containsKey("campaign_context"));
        assertFalse(payload.containsKey("narrative_entity"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void build_messagesSerialization_preservesRoleAndContent() {
        ChatRequest req = ChatRequest.builder().messages(sampleMessages).build();

        Map<String, Object> payload = builder.build(req);

        List<Map<String, Object>> messages = (List<Map<String, Object>>) payload.get("messages");
        assertEquals(2, messages.size());
        assertEquals("user", messages.get(0).get("role"));
        assertEquals("Bonjour", messages.get(0).get("content"));
        assertEquals("assistant", messages.get(1).get("role"));
    }

    // ---------- lore_context + page_summary omissions ----------------------

    @Test
    @SuppressWarnings("unchecked")
    void build_loreContext_includesBasicFields() {
        LoreStructuralContext lore = new LoreStructuralContext(
                "Ithoril", "Royaume sombre", Map.of(), List.of("dark-fantasy"));
        ChatRequest req = ChatRequest.builder().messages(sampleMessages).loreContext(lore).build();

        Map<String, Object> payload = builder.build(req);

        Map<String, Object> lctx = (Map<String, Object>) payload.get("lore_context");
        assertEquals("Ithoril", lctx.get("lore_name"));
        assertEquals("Royaume sombre", lctx.get("lore_description"));
        assertNotNull(lctx.get("folders"));
        assertEquals(List.of("dark-fantasy"), lctx.get("tags"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void build_pageSummary_omitsEmptyValuesTagsAndRelated() {
        PageSummary minimal = new PageSummary("Thorin", "PNJ",
                Map.of(), List.of(), List.of());
        LoreStructuralContext lore = new LoreStructuralContext(
                "X", "", Map.of("PNJ", List.of(minimal)), List.of());
        ChatRequest req = ChatRequest.builder().messages(sampleMessages).loreContext(lore).build();

        Map<String, Object> payload = builder.build(req);

        Map<String, Object> lctx = (Map<String, Object>) payload.get("lore_context");
        Map<String, List<Map<String, Object>>> folders = (Map<String, List<Map<String, Object>>>) lctx.get("folders");
        Map<String, Object> page = folders.get("PNJ").get(0);
        assertEquals("Thorin", page.get("title"));
        assertEquals("PNJ", page.get("template_name"));
        // Omissions : sous-champs vides absents du payload (allege le prompt).
        assertFalse(page.containsKey("values"));
        assertFalse(page.containsKey("tags"));
        assertFalse(page.containsKey("related_page_titles"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void build_pageSummary_includesNonEmptyValuesTagsAndRelated() {
        PageSummary full = new PageSummary("Thorin", "PNJ",
                Map.of("histoire", "Nee sous une etoile rouge"),
                List.of("pnj", "allie"),
                List.of("Taverne du Dragon d'Or"));
        LoreStructuralContext lore = new LoreStructuralContext(
                "X", "", Map.of("PNJ", List.of(full)), List.of());
        ChatRequest req = ChatRequest.builder().messages(sampleMessages).loreContext(lore).build();

        Map<String, Object> payload = builder.build(req);
        Map<String, Object> lctx = (Map<String, Object>) payload.get("lore_context");
        Map<String, List<Map<String, Object>>> folders = (Map<String, List<Map<String, Object>>>) lctx.get("folders");
        Map<String, Object> page = folders.get("PNJ").get(0);

        assertTrue(page.containsKey("values"));
        assertTrue(page.containsKey("tags"));
        assertTrue(page.containsKey("related_page_titles"));
        assertEquals(List.of("pnj", "allie"), page.get("tags"));
    }

    // ---------- page_context -----------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void build_pageContext_includesAllFields() {
        PageContext pc = new PageContext("Thorin", "PNJ",
                List.of("histoire", "motto"), Map.of("histoire", "..."));
        ChatRequest req = ChatRequest.builder().messages(sampleMessages).pageContext(pc).build();

        Map<String, Object> payload = builder.build(req);
        Map<String, Object> pctx = (Map<String, Object>) payload.get("page_context");
        assertEquals("Thorin", pctx.get("title"));
        assertEquals("PNJ", pctx.get("template_name"));
        assertEquals(List.of("histoire", "motto"), pctx.get("template_fields"));
        assertEquals(Map.of("histoire", "..."), pctx.get("values"));
    }

    // ---------- campaign_context + arc/chapter/scene recursion -------------

    @Test
    @SuppressWarnings("unchecked")
    void build_campaignContext_serializesFullNarrativeTree() {
        BranchHint branch = new BranchHint("fuite", "La poursuite", "HP < 50%");
        SceneSummary scene = new SceneSummary("L'auberge", "Rencontre tendue", 3, List.of(branch), List.of());
        ChapterSummary chapter = new ChapterSummary("L'arrivee", "...", 0, List.of(scene));
        ArcSummary arc = new ArcSummary("Acte I", "Mise en place", false, 1, List.of(chapter));
        CampaignStructuralContext camp = new CampaignStructuralContext(
                "Les Ombres", "dark fantasy", List.of(arc), List.of(), List.of());
        ChatRequest req = ChatRequest.builder().messages(sampleMessages).campaignContext(camp).build();

        Map<String, Object> payload = builder.build(req);

        Map<String, Object> cctx = (Map<String, Object>) payload.get("campaign_context");
        assertEquals("Les Ombres", cctx.get("campaign_name"));
        List<Map<String, Object>> arcs = (List<Map<String, Object>>) cctx.get("arcs");
        Map<String, Object> arcMap = arcs.get(0);
        assertEquals("Acte I", arcMap.get("name"));
        assertEquals(1, arcMap.get("illustration_count"));

        List<Map<String, Object>> chapters = (List<Map<String, Object>>) arcMap.get("chapters");
        Map<String, Object> chapterMap = chapters.get(0);
        assertEquals("L'arrivee", chapterMap.get("name"));

        List<Map<String, Object>> scenes = (List<Map<String, Object>>) chapterMap.get("scenes");
        Map<String, Object> sceneMap = scenes.get(0);
        assertEquals("L'auberge", sceneMap.get("name"));
        assertEquals(3, sceneMap.get("illustration_count"));

        List<Map<String, Object>> branches = (List<Map<String, Object>>) sceneMap.get("branches");
        Map<String, Object> branchMap = branches.get(0);
        assertEquals("fuite", branchMap.get("label"));
        assertEquals("La poursuite", branchMap.get("target_scene_name"));
        assertEquals("HP < 50%", branchMap.get("condition"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void build_arcSummary_omitsIllustrationCount_whenZero() {
        ArcSummary arc = new ArcSummary("A", "", false, 0, List.of());
        CampaignStructuralContext camp = new CampaignStructuralContext(
                "X", "", List.of(arc), List.of(), List.of());
        ChatRequest req = ChatRequest.builder().messages(sampleMessages).campaignContext(camp).build();

        Map<String, Object> payload = builder.build(req);
        Map<String, Object> arcMap = firstOf(asMap(payload.get("campaign_context")), "arcs");

        // Economie de payload : n'injecte pas "N illustrations" quand N=0.
        assertFalse(arcMap.containsKey("illustration_count"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void build_sceneSummary_omitsBranches_whenEmpty() {
        SceneSummary scene = new SceneSummary("S", "", 0, List.of(), List.of());
        ChapterSummary chapter = new ChapterSummary("Ch", "", 0, List.of(scene));
        ArcSummary arc = new ArcSummary("A", "", false, 0, List.of(chapter));
        CampaignStructuralContext camp = new CampaignStructuralContext(
                "X", "", List.of(arc), List.of(), List.of());
        ChatRequest req = ChatRequest.builder().messages(sampleMessages).campaignContext(camp).build();

        Map<String, Object> payload = builder.build(req);
        Map<String, Object> arcMap = firstOf(asMap(payload.get("campaign_context")), "arcs");
        Map<String, Object> chapterMap = firstOf(arcMap, "chapters");
        Map<String, Object> sceneMap = firstOf(chapterMap, "scenes");

        assertFalse(sceneMap.containsKey("branches"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void build_branchHint_omitsCondition_whenBlank() {
        BranchHint branch = new BranchHint("X", "Y", "   ");
        SceneSummary scene = new SceneSummary("S", "", 0, List.of(branch), List.of());
        ChapterSummary chapter = new ChapterSummary("Ch", "", 0, List.of(scene));
        ArcSummary arc = new ArcSummary("A", "", false, 0, List.of(chapter));
        CampaignStructuralContext camp = new CampaignStructuralContext(
                "X", "", List.of(arc), List.of(), List.of());
        ChatRequest req = ChatRequest.builder().messages(sampleMessages).campaignContext(camp).build();

        Map<String, Object> payload = builder.build(req);
        Map<String, Object> arcMap = firstOf(asMap(payload.get("campaign_context")), "arcs");
        Map<String, Object> chapterMap = firstOf(arcMap, "chapters");
        Map<String, Object> sceneMap = firstOf(chapterMap, "scenes");
        Map<String, Object> branchMap = firstOf(sceneMap, "branches");

        assertFalse(branchMap.containsKey("condition"));
    }

    // ---------- narrative_entity -------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void build_narrativeEntity_includesAllFields() {
        NarrativeEntityContext entity = new NarrativeEntityContext("scene", "L'auberge",
                Map.of("location", "Taverne", "timing", "Soir"));
        ChatRequest req = ChatRequest.builder().messages(sampleMessages).narrativeEntity(entity).build();

        Map<String, Object> payload = builder.build(req);
        Map<String, Object> ne = (Map<String, Object>) payload.get("narrative_entity");
        assertEquals("scene", ne.get("entity_type"));
        assertEquals("L'auberge", ne.get("title"));
        assertEquals(2, ((Map<?, ?>) ne.get("fields")).size());
    }

    // ---------- combinaison complete ---------------------------------------

    @Test
    void build_campaignScenario_includesBothContextsAndEntity() {
        CampaignStructuralContext camp = new CampaignStructuralContext(
                "X", "", List.of(), List.of(), List.of());
        NarrativeEntityContext entity = new NarrativeEntityContext("arc", "T", Map.of());
        ChatRequest req = ChatRequest.builder()
                .messages(sampleMessages)
                .campaignContext(camp)
                .narrativeEntity(entity)
                .build();

        Map<String, Object> payload = builder.build(req);

        assertTrue(payload.containsKey("campaign_context"));
        assertTrue(payload.containsKey("narrative_entity"));
        assertFalse(payload.containsKey("lore_context"));
        assertFalse(payload.containsKey("page_context"));
    }

    // ---------- arc HUB + characters/npcs ----------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void build_arcHub_injecteArcTypeHub() {
        // arc.hub() == true -> ajoute "arc_type":"HUB" (vocabulaire « quêtes »).
        ArcSummary arc = new ArcSummary("Hub central", "", true, 0, List.of());
        CampaignStructuralContext camp = new CampaignStructuralContext(
                "X", "", List.of(arc), List.of(), List.of());
        ChatRequest req = ChatRequest.builder().messages(sampleMessages).campaignContext(camp).build();

        Map<String, Object> arcMap = firstOf(asMap(builder.build(req).get("campaign_context")), "arcs");
        assertEquals("HUB", arcMap.get("arc_type"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void build_arcLineaire_n_injectePasArcType() {
        ArcSummary arc = new ArcSummary("Lineaire", "", false, 0, List.of());
        CampaignStructuralContext camp = new CampaignStructuralContext(
                "X", "", List.of(arc), List.of(), List.of());
        ChatRequest req = ChatRequest.builder().messages(sampleMessages).campaignContext(camp).build();

        Map<String, Object> arcMap = firstOf(asMap(builder.build(req).get("campaign_context")), "arcs");
        assertFalse(arcMap.containsKey("arc_type"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void build_campaignContext_serialiseCharactersEtNpcs_avecOmissionSnippetBlank() {
        // snippet renseigné -> présent ; snippet blank/null -> omis.
        CharacterSummary pj1 = new CharacterSummary("Aria", "Magicienne elfe");
        CharacterSummary pj2 = new CharacterSummary("Bran", "   ");
        NpcSummary pnj1 = new NpcSummary("Garde", "Sentinelle bourrue");
        NpcSummary pnj2 = new NpcSummary("Mendiant", null);
        CampaignStructuralContext camp = new CampaignStructuralContext(
                "X", "", List.of(), List.of(pj1, pj2), List.of(pnj1, pnj2));
        ChatRequest req = ChatRequest.builder().messages(sampleMessages).campaignContext(camp).build();

        Map<String, Object> cctx = asMap(builder.build(req).get("campaign_context"));
        List<Map<String, Object>> chars = (List<Map<String, Object>>) cctx.get("characters");
        assertEquals("Aria", chars.get(0).get("name"));
        assertTrue(chars.get(0).containsKey("snippet"));
        assertFalse(chars.get(1).containsKey("snippet"), "snippet blank omis");

        List<Map<String, Object>> npcs = (List<Map<String, Object>>) cctx.get("npcs");
        assertEquals("Garde", npcs.get(0).get("name"));
        assertTrue(npcs.get(0).containsKey("snippet"));
        assertFalse(npcs.get(1).containsKey("snippet"), "snippet null omis");
    }

    // ---------- rooms d'une scène ------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void build_sceneRooms_serialiseTousLesChampsEtBranchesEntrePieces() {
        RoomBranchHint exit = new RoomBranchHint("porte nord", "Salle du trone", "clé en main");
        RoomSummary room = new RoomSummary("Hall", 1, "Vaste entrée", "Spectres", List.of(exit));
        SceneSummary scene = new SceneSummary("Donjon", "", 0, List.of(), List.of(room));
        ChapterSummary chapter = new ChapterSummary("Ch", "", 0, List.of(scene));
        ArcSummary arc = new ArcSummary("A", "", false, 0, List.of(chapter));
        CampaignStructuralContext camp = new CampaignStructuralContext(
                "X", "", List.of(arc), List.of(), List.of());
        ChatRequest req = ChatRequest.builder().messages(sampleMessages).campaignContext(camp).build();

        Map<String, Object> arcMap = firstOf(asMap(builder.build(req).get("campaign_context")), "arcs");
        Map<String, Object> sceneMap = firstOf(firstOf(arcMap, "chapters"), "scenes");
        Map<String, Object> roomMap = firstOf(sceneMap, "rooms");

        assertEquals("Hall", roomMap.get("name"));
        assertEquals(1, roomMap.get("floor"));
        assertEquals("Vaste entrée", roomMap.get("description"));
        assertEquals("Spectres", roomMap.get("enemies"));
        Map<String, Object> branchMap = firstOf(roomMap, "branches");
        assertEquals("porte nord", branchMap.get("label"));
        assertEquals("Salle du trone", branchMap.get("target_room_name"));
        assertEquals("clé en main", branchMap.get("condition"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void build_sceneRooms_ometLesChampsOptionnelsVides() {
        // floor null, description/enemies blank, condition blank, branches non vides.
        RoomBranchHint exit = new RoomBranchHint("sortie", "Autre", "  ");
        RoomSummary room = new RoomSummary("Cellule", null, "  ", "   ", List.of(exit));
        SceneSummary scene = new SceneSummary("Donjon", "", 0, List.of(), List.of(room));
        ChapterSummary chapter = new ChapterSummary("Ch", "", 0, List.of(scene));
        ArcSummary arc = new ArcSummary("A", "", false, 0, List.of(chapter));
        CampaignStructuralContext camp = new CampaignStructuralContext(
                "X", "", List.of(arc), List.of(), List.of());
        ChatRequest req = ChatRequest.builder().messages(sampleMessages).campaignContext(camp).build();

        Map<String, Object> arcMap = firstOf(asMap(builder.build(req).get("campaign_context")), "arcs");
        Map<String, Object> sceneMap = firstOf(firstOf(arcMap, "chapters"), "scenes");
        Map<String, Object> roomMap = firstOf(sceneMap, "rooms");

        assertFalse(roomMap.containsKey("floor"));
        assertFalse(roomMap.containsKey("description"));
        assertFalse(roomMap.containsKey("enemies"));
        Map<String, Object> branchMap = firstOf(roomMap, "branches");
        assertFalse(branchMap.containsKey("condition"), "condition blank omise");
    }

    @Test
    @SuppressWarnings("unchecked")
    void build_sceneRooms_omisesSiListeVide() {
        // s.rooms() vide -> clé "rooms" absente.
        SceneSummary scene = new SceneSummary("Lineaire", "", 0, List.of(), List.of());
        ChapterSummary chapter = new ChapterSummary("Ch", "", 0, List.of(scene));
        ArcSummary arc = new ArcSummary("A", "", false, 0, List.of(chapter));
        CampaignStructuralContext camp = new CampaignStructuralContext(
                "X", "", List.of(arc), List.of(), List.of());
        ChatRequest req = ChatRequest.builder().messages(sampleMessages).campaignContext(camp).build();

        Map<String, Object> arcMap = firstOf(asMap(builder.build(req).get("campaign_context")), "arcs");
        Map<String, Object> sceneMap = firstOf(firstOf(arcMap, "chapters"), "scenes");
        assertFalse(sceneMap.containsKey("rooms"));
    }

    // ---------- game_system_context ----------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void build_gameSystemContext_completInclutDescriptionEtSections() {
        GameSystemContext gs = new GameSystemContext(
                "Nimble", "JDR rapide", Map.of("Combat", "Lancez 1d20"));
        ChatRequest req = ChatRequest.builder().messages(sampleMessages).gameSystemContext(gs).build();

        Map<String, Object> gsm = asMap(builder.build(req).get("game_system_context"));
        assertEquals("Nimble", gsm.get("system_name"));
        assertEquals("JDR rapide", gsm.get("system_description"));
        assertEquals(Map.of("Combat", "Lancez 1d20"), gsm.get("sections"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void build_gameSystemContext_descriptionBlankEtSectionsNull() {
        // systemDescription blank -> omis ; sections null -> Map.of() de repli.
        GameSystemContext gs = new GameSystemContext("D&D 5.1", "   ", null);
        ChatRequest req = ChatRequest.builder().messages(sampleMessages).gameSystemContext(gs).build();

        Map<String, Object> gsm = asMap(builder.build(req).get("game_system_context"));
        assertEquals("D&D 5.1", gsm.get("system_name"));
        assertFalse(gsm.containsKey("system_description"));
        assertEquals(Map.of(), gsm.get("sections"));
    }

    // ---------- session_context --------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void build_sessionContext_complet_inclutToutesLesListesPeuplees() {
        LocalDateTime now = LocalDateTime.of(2026, Month.JUNE, 14, 20, 0);
        JournalEntrySummary entry = new JournalEntrySummary(
                "combat", "Le dragon attaque", now, null);
        JournalEntrySummary prev = new JournalEntrySummary(
                "info", "Le roi est mort", now, "Session 1");
        QuestSummary avail = new QuestSummary("Sauver le village", "Acte I", "Urgence");
        QuestSummary inProg = new QuestSummary("Trouver l'épée", "Acte II", "  ");
        SessionContext sc = new SessionContext(
                "Session 2", true, now,
                List.of(entry), List.of(prev),
                List.of(avail), List.of(inProg),
                List.of("Quête secrète"), List.of("porte_ouverte"));
        ChatRequest req = ChatRequest.builder().messages(sampleMessages).sessionContext(sc).build();

        Map<String, Object> scm = asMap(builder.build(req).get("session_context"));
        assertEquals("Session 2", scm.get("session_name"));
        assertEquals(true, scm.get("active"));
        assertEquals(now.toString(), scm.get("started_at"));

        // entries : type/content/occurredAt présents, source omis (null) sur l'entrée courante.
        Map<String, Object> entryMap = firstOf(scm, "entries");
        assertEquals("combat", entryMap.get("type"));
        assertEquals("Le dragon attaque", entryMap.get("content"));
        assertEquals(now.toString(), entryMap.get("occurred_at"));
        assertFalse(entryMap.containsKey("source_session_name"));

        // previous_events : source renseignée.
        Map<String, Object> prevMap = firstOf(scm, "previous_events");
        assertEquals("Session 1", prevMap.get("source_session_name"));

        // available_quests : description renseignée ; in_progress : description blank omise.
        Map<String, Object> availMap = firstOf(scm, "available_quests");
        assertEquals("Sauver le village", availMap.get("name"));
        assertEquals("Acte I", availMap.get("arc_name"));
        assertEquals("Urgence", availMap.get("description"));
        Map<String, Object> inProgMap = firstOf(scm, "in_progress_quests");
        assertFalse(inProgMap.containsKey("description"), "description blank omise");

        assertEquals(List.of("Quête secrète"), scm.get("locked_quest_titles"));
        assertEquals(List.of("porte_ouverte"), scm.get("active_flags"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void build_sessionContext_minimal_ometListesVidesEtChampsNull() {
        // startedAt null -> omis ; entries null -> List.of() ; toutes les autres listes
        // vides/null -> clés absentes.
        SessionContext sc = new SessionContext(
                "Session vide", false, null,
                null, List.of(),
                List.of(), List.of(),
                List.of(), List.of());
        ChatRequest req = ChatRequest.builder().messages(sampleMessages).sessionContext(sc).build();

        Map<String, Object> scm = asMap(builder.build(req).get("session_context"));
        assertEquals("Session vide", scm.get("session_name"));
        assertEquals(false, scm.get("active"));
        assertFalse(scm.containsKey("started_at"));
        assertEquals(List.of(), scm.get("entries"));
        assertFalse(scm.containsKey("previous_events"));
        assertFalse(scm.containsKey("available_quests"));
        assertFalse(scm.containsKey("in_progress_quests"));
        assertFalse(scm.containsKey("locked_quest_titles"));
        assertFalse(scm.containsKey("active_flags"));
    }

    // ---------- tous les contextes présents simultanément ------------------

    @Test
    void build_tousLesContextes_sontTousPresents() {
        LoreStructuralContext lore = new LoreStructuralContext("L", "", Map.of(), List.of());
        PageContext page = new PageContext("P", "T", List.of(), Map.of());
        CampaignStructuralContext camp = new CampaignStructuralContext("C", "", List.of(), List.of(), List.of());
        NarrativeEntityContext entity = new NarrativeEntityContext("scene", "S", Map.of());
        GameSystemContext gs = new GameSystemContext("G", null, Map.of());
        SessionContext sc = new SessionContext("Sess", true, null, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of());
        ChatRequest req = ChatRequest.builder()
                .messages(sampleMessages)
                .loreContext(lore)
                .pageContext(page)
                .campaignContext(camp)
                .narrativeEntity(entity)
                .gameSystemContext(gs)
                .sessionContext(sc)
                .build();

        Map<String, Object> payload = builder.build(req);
        assertTrue(payload.containsKey("lore_context"));
        assertTrue(payload.containsKey("page_context"));
        assertTrue(payload.containsKey("campaign_context"));
        assertTrue(payload.containsKey("narrative_entity"));
        assertTrue(payload.containsKey("game_system_context"));
        assertTrue(payload.containsKey("session_context"));
    }
}
