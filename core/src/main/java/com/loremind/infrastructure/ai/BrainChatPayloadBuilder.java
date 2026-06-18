package com.loremind.infrastructure.ai;

import com.loremind.domain.generationcontext.CampaignStructuralContext;
import com.loremind.domain.generationcontext.CampaignStructuralContext.ArcSummary;
import com.loremind.domain.generationcontext.CampaignStructuralContext.BranchHint;
import com.loremind.domain.generationcontext.CampaignStructuralContext.ChapterSummary;
import com.loremind.domain.generationcontext.CampaignStructuralContext.CharacterSummary;
import com.loremind.domain.generationcontext.CampaignStructuralContext.NpcSummary;
import com.loremind.domain.generationcontext.CampaignStructuralContext.SceneSummary;
import com.loremind.domain.generationcontext.ChatMessage;
import com.loremind.domain.generationcontext.ChatRequest;
import com.loremind.domain.generationcontext.GameSystemContext;
import com.loremind.domain.generationcontext.LoreStructuralContext;
import com.loremind.domain.generationcontext.LoreStructuralContext.PageSummary;
import com.loremind.domain.generationcontext.NarrativeEntityContext;
import com.loremind.domain.generationcontext.PageContext;
import com.loremind.domain.generationcontext.SessionContext;
import com.loremind.domain.generationcontext.SessionContext.JournalEntrySummary;
import com.loremind.domain.generationcontext.SessionContext.QuestSummary;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Helper d'infrastructure : traduit un ChatRequest (domaine) vers le dict JSON
 * attendu par le Brain Python (/chat/stream).
 * <p>
 * Extrait de BrainAiChatClient pour isoler la responsabilité "sérialisation
 * de payload" (SRP) — le client HTTP se concentre désormais uniquement sur le
 * transport et le streaming SSE.
 * <p>
 * Chaque contexte optionnel (lore, page, campaign, entité narrative) est omis
 * si null, pour s'aligner sur le schéma Pydantic (champs Optional absents).
 */
@Component
public class BrainChatPayloadBuilder {

    public Map<String, Object> build(ChatRequest request) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("messages", request.messages().stream()
                .map(this::messageToMap)
                .collect(Collectors.toList()));

        if (request.loreContext() != null) {
            root.put("lore_context", loreContextToMap(request.loreContext()));
        }
        if (request.pageContext() != null) {
            root.put("page_context", pageContextToMap(request.pageContext()));
        }
        if (request.campaignContext() != null) {
            root.put("campaign_context", campaignContextToMap(request.campaignContext()));
        }
        if (request.narrativeEntity() != null) {
            root.put("narrative_entity", narrativeEntityToMap(request.narrativeEntity()));
        }
        if (request.gameSystemContext() != null) {
            root.put("game_system_context", gameSystemContextToMap(request.gameSystemContext()));
        }
        if (request.sessionContext() != null) {
            root.put("session_context", sessionContextToMap(request.sessionContext()));
        }
        return root;
    }

    private Map<String, Object> sessionContextToMap(SessionContext sc) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("session_name", sc.sessionName());
        map.put("active", sc.active());
        if (sc.startedAt() != null) {
            map.put("started_at", sc.startedAt().toString());
        }
        map.put("entries", sc.entries() != null
                ? sc.entries().stream().map(this::journalEntryToMap).collect(Collectors.toList())
                : List.of());
        // Évènements des sessions précédentes : omis si vide (campagne sur sa 1re session).
        if (sc.previousEvents() != null && !sc.previousEvents().isEmpty()) {
            map.put("previous_events", sc.previousEvents().stream()
                    .map(this::journalEntryToMap)
                    .collect(Collectors.toList()));
        }
        // État Hub (quêtes / flags). Toutes les listes sont omises si vides pour ne pas
        // saturer le prompt sur les campagnes sans Hub.
        if (sc.availableQuests() != null && !sc.availableQuests().isEmpty()) {
            map.put("available_quests", sc.availableQuests().stream()
                    .map(this::questSummaryToMap)
                    .collect(Collectors.toList()));
        }
        if (sc.inProgressQuests() != null && !sc.inProgressQuests().isEmpty()) {
            map.put("in_progress_quests", sc.inProgressQuests().stream()
                    .map(this::questSummaryToMap)
                    .collect(Collectors.toList()));
        }
        if (sc.lockedQuestTitles() != null && !sc.lockedQuestTitles().isEmpty()) {
            map.put("locked_quest_titles", sc.lockedQuestTitles());
        }
        if (sc.activeFlags() != null && !sc.activeFlags().isEmpty()) {
            map.put("active_flags", sc.activeFlags());
        }
        return map;
    }

    private Map<String, Object> questSummaryToMap(QuestSummary q) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", q.name());
        map.put("arc_name", q.arcName());
        putIfText(map, "description", q.description());
        return map;
    }

    private Map<String, Object> journalEntryToMap(JournalEntrySummary e) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", e.type());
        map.put("content", e.content());
        if (e.occurredAt() != null) {
            map.put("occurred_at", e.occurredAt().toString());
        }
        putIfText(map, "source_session_name", e.sourceSessionName());
        return map;
    }

    private Map<String, Object> gameSystemContextToMap(GameSystemContext gs) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("system_name", gs.systemName());
        putIfText(map, "system_description", gs.systemDescription());
        map.put("sections", gs.sections() != null ? gs.sections() : Map.of());
        return map;
    }

    private Map<String, Object> messageToMap(ChatMessage m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("role", m.role());
        map.put("content", m.content());
        return map;
    }

    private Map<String, Object> loreContextToMap(LoreStructuralContext ctx) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("lore_name", ctx.loreName());
        map.put("lore_description", ctx.loreDescription());

        Map<String, Object> foldersMap = new LinkedHashMap<>();
        for (Map.Entry<String, List<PageSummary>> e : ctx.folders().entrySet()) {
            foldersMap.put(e.getKey(), e.getValue().stream()
                    .map(this::pageSummaryToMap)
                    .collect(Collectors.toList()));
        }
        map.put("folders", foldersMap);
        map.put("tags", ctx.tags());
        return map;
    }

    private Map<String, Object> pageSummaryToMap(PageSummary ps) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", ps.title());
        map.put("template_name", ps.templateName());
        // values/tags/related_page_titles : omis si vides pour alléger le payload.
        if (ps.values() != null && !ps.values().isEmpty()) {
            map.put("values", ps.values());
        }
        if (ps.tags() != null && !ps.tags().isEmpty()) {
            map.put("tags", ps.tags());
        }
        if (ps.relatedPageTitles() != null && !ps.relatedPageTitles().isEmpty()) {
            map.put("related_page_titles", ps.relatedPageTitles());
        }
        return map;
    }

    private Map<String, Object> pageContextToMap(PageContext pc) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", pc.title());
        map.put("template_name", pc.templateName());
        map.put("template_fields", pc.templateFields());
        map.put("values", pc.values());
        return map;
    }

    private Map<String, Object> campaignContextToMap(CampaignStructuralContext ctx) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("campaign_name", ctx.campaignName());
        map.put("campaign_description", ctx.campaignDescription());
        map.put("arcs", ctx.arcs().stream()
                .map(this::arcSummaryToMap)
                .collect(Collectors.toList()));
        // Liste des PJ : omise si aucun pour alléger le prompt des campagnes sans fiches.
        if (ctx.characters() != null && !ctx.characters().isEmpty()) {
            map.put("characters", ctx.characters().stream()
                    .map(this::characterSummaryToMap)
                    .collect(Collectors.toList()));
        }
        // Liste des PNJ : symétrique aux PJ, omise si vide pour alléger le payload.
        if (ctx.npcs() != null && !ctx.npcs().isEmpty()) {
            map.put("npcs", ctx.npcs().stream()
                    .map(this::npcSummaryToMap)
                    .collect(Collectors.toList()));
        }
        return map;
    }

    private Map<String, Object> characterSummaryToMap(CharacterSummary c) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", c.name());
        putIfText(map, "snippet", c.snippet());
        return map;
    }

    private Map<String, Object> npcSummaryToMap(NpcSummary n) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", n.name());
        putIfText(map, "snippet", n.snippet());
        return map;
    }

    /**
     * Helper générique pour sérialiser les entités structurelles (Arc/Chapter/Scene)
     * avec name, description et illustration_count conditionnel.
     */
    private <T> Map<String, Object> structuralSummaryToMap(
            T entity,
            Function<T, String> nameExtractor,
            Function<T, String> descriptionExtractor,
            Function<T, Integer> illustrationCountExtractor,
            BiConsumer<Map<String, Object>, T> childSerializer) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", nameExtractor.apply(entity));
        map.put("description", descriptionExtractor.apply(entity));
        if (illustrationCountExtractor.apply(entity) > 0) {
            map.put("illustration_count", illustrationCountExtractor.apply(entity));
        }
        childSerializer.accept(map, entity);
        return map;
    }

    private Map<String, Object> arcSummaryToMap(ArcSummary a) {
        return structuralSummaryToMap(
                a,
                ArcSummary::name,
                ArcSummary::description,
                ArcSummary::illustrationCount,
                (map, arc) -> {
                    // Vocabulaire UI : les chapitres d'un arc HUB sont des « quêtes ».
                    if (arc.hub()) {
                        map.put("arc_type", "HUB");
                    }
                    map.put("chapters", arc.chapters().stream()
                            .map(this::chapterSummaryToMap)
                            .collect(Collectors.toList()));
                });
    }

    private Map<String, Object> chapterSummaryToMap(ChapterSummary c) {
        return structuralSummaryToMap(
                c,
                ChapterSummary::name,
                ChapterSummary::description,
                ChapterSummary::illustrationCount,
                (map, chapter) -> map.put("scenes", chapter.scenes().stream()
                        .map(this::sceneSummaryToMap)
                        .collect(Collectors.toList())));
    }

    private Map<String, Object> sceneSummaryToMap(SceneSummary s) {
        return structuralSummaryToMap(
                s,
                SceneSummary::name,
                SceneSummary::description,
                SceneSummary::illustrationCount,
                (map, scene) -> {
                    // Branches narratives : omises si absentes (scènes linéaires classiques).
                    if (s.branches() != null && !s.branches().isEmpty()) {
                        map.put("branches", s.branches().stream()
                                .map(this::branchHintToMap)
                                .collect(Collectors.toList()));
                    }
                    // Pièces du lieu explorable : omises si scène classique.
                    if (s.rooms() != null && !s.rooms().isEmpty()) {
                        map.put("rooms", s.rooms().stream()
                                .map(this::roomSummaryToMap)
                                .collect(Collectors.toList()));
                    }
                });
    }

    private Map<String, Object> branchHintToMap(BranchHint b) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("label", b.label());
        map.put("target_scene_name", b.targetSceneName());
        putIfText(map, "condition", b.condition());
        return map;
    }

    private Map<String, Object> roomSummaryToMap(com.loremind.domain.generationcontext.CampaignStructuralContext.RoomSummary r) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", r.name());
        if (r.floor() != null) map.put("floor", r.floor());
        putIfText(map, "description", r.description());
        putIfText(map, "enemies", r.enemies());
        if (r.branches() != null && !r.branches().isEmpty()) {
            map.put("branches", r.branches().stream().map(b -> {
                Map<String, Object> bm = new LinkedHashMap<>();
                bm.put("label", b.label());
                bm.put("target_room_name", b.targetRoomName());
                putIfText(bm, "condition", b.condition());
                return bm;
            }).collect(Collectors.toList()));
        }
        return map;
    }

    private Map<String, Object> narrativeEntityToMap(NarrativeEntityContext ne) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("entity_type", ne.entityType());
        map.put("title", ne.title());
        map.put("fields", ne.fields());
        return map;
    }

    /**
     * Ajoute {@code key → value} uniquement si {@code value} est une chaîne non
     * nulle et non blanche. Centralise l'omission des champs Optional « texte »
     * pour s'aligner sur le schéma Pydantic du Brain (champ absent si vide).
     */
    private static void putIfText(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }
}
