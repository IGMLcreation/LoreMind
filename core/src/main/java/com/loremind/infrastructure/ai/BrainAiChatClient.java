package com.loremind.infrastructure.ai;

import com.loremind.domain.generationcontext.CampaignStructuralContext;
import com.loremind.domain.generationcontext.CampaignStructuralContext.ArcSummary;
import com.loremind.domain.generationcontext.CampaignStructuralContext.BranchHint;
import com.loremind.domain.generationcontext.CampaignStructuralContext.ChapterSummary;
import com.loremind.domain.generationcontext.CampaignStructuralContext.SceneSummary;
import com.loremind.domain.generationcontext.ChatMessage;
import com.loremind.domain.generationcontext.ChatRequest;
import com.loremind.domain.generationcontext.ChatUsage;
import com.loremind.domain.generationcontext.LoreStructuralContext;
import com.loremind.domain.generationcontext.LoreStructuralContext.PageSummary;
import com.loremind.domain.generationcontext.NarrativeEntityContext;
import com.loremind.domain.generationcontext.PageContext;
import com.loremind.domain.generationcontext.ports.AiChatProvider;
import com.loremind.domain.generationcontext.ports.AiProviderException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Adapter de sortie (Architecture Hexagonale) : implémente AiChatProvider
 * en appelant le Brain Python via WebClient + SSE (Server-Sent Events).
 * <p>
 * Responsabilités :
 *  1. Traduire ChatRequest (domaine) -> JSON attendu par /chat/stream.
 *     Sérialise lore_context, page_context, campaign_context et
 *     narrative_entity de façon conditionnelle selon le scénario d'appel
 *     (chat Lore / chat Lore focalisé page / chat Campagne / chat Campagne
 *     focalisé arc-chapter-scene).
 *  2. Consommer le flux SSE token par token.
 *  3. Invoquer onToken / onComplete / onError au bon moment.
 *  4. Traduire toute erreur technique en AiProviderException.
 * <p>
 * Le domaine ne voit JAMAIS WebClient, Flux, ni la moindre URL.
 */
@Component
public class BrainAiChatClient implements AiChatProvider {

    private static final String CHAT_STREAM_PATH = "/chat/stream";
    private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_STRING_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient webClient;

    public BrainAiChatClient(
            WebClient.Builder builder,
            @Value("${brain.base-url}") String baseUrl) {
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public void streamChat(
            ChatRequest request,
            Consumer<ChatUsage> onUsage,
            Consumer<String> onToken,
            Runnable onComplete,
            Consumer<Throwable> onError) {

        Map<String, Object> payload = toPayload(request);

        Flux<ServerSentEvent<String>> flux = webClient.post()
                .uri(CHAT_STREAM_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(payload)
                .retrieve()
                .bodyToFlux(SSE_STRING_TYPE);

        try {
            // blockLast() : transforme le flux réactif en appel bloquant conforme
            // au contrat synchrone du port. L'appelant choisit le thread.
            flux
                .timeout(Duration.ofSeconds(120))
                .doOnNext(sse -> handleEvent(sse, onUsage, onToken, onError))
                .blockLast();
            onComplete.run();
        } catch (Exception e) {
            onError.accept(new AiProviderException(
                    "Erreur lors du streaming chat depuis le Brain.", e));
        }
    }

    /** Dispatch selon le type d'événement SSE (data par défaut, done, error, usage). */
    private void handleEvent(
            ServerSentEvent<String> sse,
            Consumer<ChatUsage> onUsage,
            Consumer<String> onToken,
            Consumer<Throwable> onError) {
        String event = sse.event();  // null si pas d'event: xxx -> c'est un data par défaut
        String data = sse.data();

        if ("error".equals(event)) {
            onError.accept(new AiProviderException(
                    "Le Brain a signalé une erreur : " + data));
            return;
        }
        if ("done".equals(event)) {
            return; // la fin est gérée par blockLast + onComplete
        }
        if ("usage".equals(event)) {
            ChatUsage usage = extractUsage(data);
            if (usage != null) onUsage.accept(usage);
            return;
        }
        // Défaut : événement data avec JSON {"token":"..."}.
        String token = extractToken(data);
        if (token != null && !token.isEmpty()) {
            onToken.accept(token);
        }
    }

    /**
     * Parse un JSON {"system":N,"history":N,"current":N,"max":N} en ChatUsage.
     * Renvoie null si le payload est illisible — dans ce cas on ne propage
     * simplement pas d'usage, le stream token continue normalement.
     */
    private ChatUsage extractUsage(String json) {
        if (json == null) return null;
        try {
            int system = extractIntField(json, "system");
            int history = extractIntField(json, "history");
            int current = extractIntField(json, "current");
            int max = extractIntField(json, "max");
            return new ChatUsage(system, history, current, max);
        } catch (Exception e) {
            return null;
        }
    }

    /** Parse minimaliste d'un champ entier JSON sans dépendre de Jackson. */
    private int extractIntField(String json, String field) {
        String needle = "\"" + field + "\"";
        int idx = json.indexOf(needle);
        if (idx < 0) return 0;
        int colon = json.indexOf(':', idx);
        if (colon < 0) return 0;
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        if (end == start) return 0;
        return Integer.parseInt(json.substring(start, end));
    }

    /**
     * Parse minimaliste du JSON {"token":"..."} sans pull Jackson ici.
     * Si le format se complexifie, on remplacera par un DTO Jackson.
     */
    private String extractToken(String json) {
        if (json == null) return null;
        int idx = json.indexOf("\"token\"");
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx);
        int firstQuote = json.indexOf('"', colon + 1);
        int lastQuote = json.lastIndexOf('"');
        if (firstQuote < 0 || lastQuote <= firstQuote) return null;
        return json.substring(firstQuote + 1, lastQuote)
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    // --- Construction du payload JSON vers le Brain -------------------------

    /**
     * Construit le payload JSON. Chaque contexte optionnel est omis s'il est
     * null, pour s'aligner sur le schéma Pydantic côté Brain (champs
     * Optional qui restent absents du dict transmis au LLM).
     */
    private Map<String, Object> toPayload(ChatRequest request) {
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
        return root;
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
        // values/tags/related_page_titles ne sont sérialisés que s'ils contiennent
        // de l'info — payload réseau plus léger quand la page est vierge.
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
        return map;
    }

    /**
     * Helper generic pour serialiser les entites structurelles (Arc/Chapter/Scene)
     * avec name, description et illustration_count conditionnel.
     */
    private <T> Map<String, Object> structuralSummaryToMap(
            T entity,
            java.util.function.Function<T, String> nameExtractor,
            java.util.function.Function<T, String> descriptionExtractor,
            java.util.function.Function<T, Integer> illustrationCountExtractor,
            java.util.function.BiConsumer<Map<String, Object>, T> childSerializer) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", nameExtractor.apply(entity));
        map.put("description", descriptionExtractor.apply(entity));
        // Envoye au Python pour enrichir le prompt ("N illustrations attachees").
        // Serialise uniquement si > 0 pour economiser le payload sur les entites sans images.
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
                    // Branches narratives : serialise uniquement si presentes, pour garder
                    // un payload leger sur les scenes lineaires classiques.
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
