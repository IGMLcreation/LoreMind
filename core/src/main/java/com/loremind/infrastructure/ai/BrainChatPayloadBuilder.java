package com.loremind.infrastructure.ai;

import com.loremind.domain.generationcontext.CampaignStructuralContext;
import com.loremind.domain.generationcontext.CampaignStructuralContext.ArcSummary;
import com.loremind.domain.generationcontext.CampaignStructuralContext.BranchHint;
import com.loremind.domain.generationcontext.CampaignStructuralContext.ChapterSummary;
import com.loremind.domain.generationcontext.CampaignStructuralContext.CharacterSummary;
import com.loremind.domain.generationcontext.CampaignStructuralContext.SceneSummary;
import com.loremind.domain.generationcontext.ChatMessage;
import com.loremind.domain.generationcontext.ChatRequest;
import com.loremind.domain.generationcontext.GameSystemContext;
import com.loremind.domain.generationcontext.LoreStructuralContext;
import com.loremind.domain.generationcontext.LoreStructuralContext.PageSummary;
import com.loremind.domain.generationcontext.NarrativeEntityContext;
import com.loremind.domain.generationcontext.PageContext;
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
        root.put("messages", request.getMessages().stream()
                .map(this::messageToMap)
                .collect(Collectors.toList()));

        if (request.getLoreContext() != null) {
            root.put("lore_context", loreContextToMap(request.getLoreContext()));
        }
        if (request.getPageContext() != null) {
            root.put("page_context", pageContextToMap(request.getPageContext()));
        }
        if (request.getCampaignContext() != null) {
            root.put("campaign_context", campaignContextToMap(request.getCampaignContext()));
        }
        if (request.getNarrativeEntity() != null) {
            root.put("narrative_entity", narrativeEntityToMap(request.getNarrativeEntity()));
        }
        if (request.getGameSystemContext() != null) {
            root.put("game_system_context", gameSystemContextToMap(request.getGameSystemContext()));
        }
        return root;
    }

    private Map<String, Object> gameSystemContextToMap(GameSystemContext gs) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("system_name", gs.getSystemName());
        if (gs.getSystemDescription() != null && !gs.getSystemDescription().isBlank()) {
            map.put("system_description", gs.getSystemDescription());
        }
        map.put("sections", gs.getSections() != null ? gs.getSections() : Map.of());
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
        map.put("lore_name", ctx.getLoreName());
        map.put("lore_description", ctx.getLoreDescription());

        Map<String, Object> foldersMap = new LinkedHashMap<>();
        for (Map.Entry<String, List<PageSummary>> e : ctx.getFolders().entrySet()) {
            foldersMap.put(e.getKey(), e.getValue().stream()
                    .map(this::pageSummaryToMap)
                    .collect(Collectors.toList()));
        }
        map.put("folders", foldersMap);
        map.put("tags", ctx.getTags());
        return map;
    }

    private Map<String, Object> pageSummaryToMap(PageSummary ps) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", ps.getTitle());
        map.put("template_name", ps.getTemplateName());
        // values/tags/related_page_titles : omis si vides pour alléger le payload.
        if (ps.getValues() != null && !ps.getValues().isEmpty()) {
            map.put("values", ps.getValues());
        }
        if (ps.getTags() != null && !ps.getTags().isEmpty()) {
            map.put("tags", ps.getTags());
        }
        if (ps.getRelatedPageTitles() != null && !ps.getRelatedPageTitles().isEmpty()) {
            map.put("related_page_titles", ps.getRelatedPageTitles());
        }
        return map;
    }

    private Map<String, Object> pageContextToMap(PageContext pc) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", pc.getTitle());
        map.put("template_name", pc.getTemplateName());
        map.put("template_fields", pc.getTemplateFields());
        map.put("values", pc.getValues());
        return map;
    }

    private Map<String, Object> campaignContextToMap(CampaignStructuralContext ctx) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("campaign_name", ctx.getCampaignName());
        map.put("campaign_description", ctx.getCampaignDescription());
        map.put("arcs", ctx.getArcs().stream()
                .map(this::arcSummaryToMap)
                .collect(Collectors.toList()));
        // Liste des PJ : omise si aucun pour alléger le prompt des campagnes sans fiches.
        if (ctx.getCharacters() != null && !ctx.getCharacters().isEmpty()) {
            map.put("characters", ctx.getCharacters().stream()
                    .map(this::characterSummaryToMap)
                    .collect(Collectors.toList()));
        }
        return map;
    }

    private Map<String, Object> characterSummaryToMap(CharacterSummary c) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", c.getName());
        if (c.getSnippet() != null && !c.getSnippet().isBlank()) {
            map.put("snippet", c.getSnippet());
        }
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
                ArcSummary::getName,
                ArcSummary::getDescription,
                ArcSummary::getIllustrationCount,
                (map, arc) -> map.put("chapters", arc.getChapters().stream()
                        .map(this::chapterSummaryToMap)
                        .collect(Collectors.toList())));
    }

    private Map<String, Object> chapterSummaryToMap(ChapterSummary c) {
        return structuralSummaryToMap(
                c,
                ChapterSummary::getName,
                ChapterSummary::getDescription,
                ChapterSummary::getIllustrationCount,
                (map, chapter) -> map.put("scenes", chapter.getScenes().stream()
                        .map(this::sceneSummaryToMap)
                        .collect(Collectors.toList())));
    }

    private Map<String, Object> sceneSummaryToMap(SceneSummary s) {
        return structuralSummaryToMap(
                s,
                SceneSummary::getName,
                SceneSummary::getDescription,
                SceneSummary::getIllustrationCount,
                (map, scene) -> {
                    // Branches narratives : omises si absentes (scènes linéaires classiques).
                    if (s.getBranches() != null && !s.getBranches().isEmpty()) {
                        map.put("branches", s.getBranches().stream()
                                .map(this::branchHintToMap)
                                .collect(Collectors.toList()));
                    }
                });
    }

    private Map<String, Object> branchHintToMap(BranchHint b) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("label", b.getLabel());
        map.put("target_scene_name", b.getTargetSceneName());
        if (b.getCondition() != null && !b.getCondition().isBlank()) {
            map.put("condition", b.getCondition());
        }
        return map;
    }

    private Map<String, Object> narrativeEntityToMap(NarrativeEntityContext ne) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("entity_type", ne.getEntityType());
        map.put("title", ne.getTitle());
        map.put("fields", ne.getFields());
        return map;
    }
}
