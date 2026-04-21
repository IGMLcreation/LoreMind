package com.loremind.application.generationcontext;

import com.loremind.domain.generationcontext.LoreStructuralContext;
import com.loremind.domain.generationcontext.LoreStructuralContext.PageSummary;
import com.loremind.domain.lorecontext.Lore;
import com.loremind.domain.lorecontext.LoreNode;
import com.loremind.domain.lorecontext.Page;
import com.loremind.domain.lorecontext.Template;
import com.loremind.domain.lorecontext.ports.LoreNodeRepository;
import com.loremind.domain.lorecontext.ports.LoreRepository;
import com.loremind.domain.lorecontext.ports.PageRepository;
import com.loremind.domain.lorecontext.ports.TemplateRepository;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service applicatif qui construit un {@link LoreStructuralContext}
 * depuis le Lore Context (Single Responsibility : projection LoreContext → GenerationContext).
 * <p>
 * Partagé entre {@link StreamChatForLoreUseCase} (Lore) et
 * {@link StreamChatForCampaignUseCase} (Campagne liée à un Lore) pour
 * respecter DRY — la carte structurelle d'un Lore se calcule de la même
 * manière des deux côtés.
 * <p>
 * Depuis b9 : chaque PageSummary embarque values/tags/relatedPageTitles
 * (résolus en titres), avec troncature à {@value #MAX_VALUE_LENGTH} caractères
 * par valeur pour garder le prompt sous contrôle.
 */
@Component
public class LoreStructuralContextBuilder {

    /** Garde-fou : évite qu'un champ énorme (ex: "Histoire" de 5000 car.) ne sature le prompt. */
    private static final int MAX_VALUE_LENGTH = 500;

    private final LoreRepository loreRepository;
    private final LoreNodeRepository loreNodeRepository;
    private final PageRepository pageRepository;
    private final TemplateRepository templateRepository;

    public LoreStructuralContextBuilder(
            LoreRepository loreRepository,
            LoreNodeRepository loreNodeRepository,
            PageRepository pageRepository,
            TemplateRepository templateRepository) {
        this.loreRepository = loreRepository;
        this.loreNodeRepository = loreNodeRepository;
        this.pageRepository = pageRepository;
        this.templateRepository = templateRepository;
    }

    /**
     * Construit la carte structurelle pour un Lore obligatoire.
     * @throws IllegalArgumentException si le Lore est introuvable
     */
    public LoreStructuralContext build(String loreId) {
        return buildOptional(loreId).orElseThrow(() ->
                new IllegalArgumentException("Lore non trouvé avec l'ID: " + loreId));
    }

    /**
     * Variante non-strict : renvoie Optional.empty() si le Lore a été supprimé
     * (cas d'une Campagne dont le loreId pointe sur un Lore effacé entre-temps).
     */
    public Optional<LoreStructuralContext> buildOptional(String loreId) {
        return loreRepository.findById(loreId).map(this::buildFromLore);
    }

    private LoreStructuralContext buildFromLore(Lore lore) {
        List<LoreNode> nodes = loreNodeRepository.findByLoreId(lore.getId());
        List<Page> pages = pageRepository.findByLoreId(lore.getId());
        List<Template> templates = templateRepository.findByLoreId(lore.getId());

        // Maps de résolution construites une seule fois — évite les N² en aval.
        Map<String, String> templateNameById = templates.stream()
                .collect(Collectors.toMap(Template::getId, Template::getName, (a, b) -> a));
        Map<String, String> pageTitleById = pages.stream()
                .collect(Collectors.toMap(Page::getId, Page::getTitle, (a, b) -> a));

        return LoreStructuralContext.builder()
                .loreName(lore.getName())
                .loreDescription(lore.getDescription())
                .folders(buildFoldersMap(nodes, pages, templateNameById, pageTitleById))
                .tags(extractUniqueTags(pages))
                .build();
    }

    private Map<String, List<PageSummary>> buildFoldersMap(
            List<LoreNode> nodes,
            List<Page> pages,
            Map<String, String> templateNameById,
            Map<String, String> pageTitleById) {
        // LinkedHashMap : préserve l'ordre d'insertion pour un prompt lisible.
        Map<String, List<PageSummary>> folders = new LinkedHashMap<>();
        for (LoreNode node : nodes) {
            folders.put(node.getName(), pagesInFolder(node.getId(), pages, templateNameById, pageTitleById));
        }
        return folders;
    }

    private List<PageSummary> pagesInFolder(
            String nodeId,
            List<Page> allPages,
            Map<String, String> templateNameById,
            Map<String, String> pageTitleById) {
        return allPages.stream()
                .filter(p -> nodeId.equals(p.getNodeId()))
                .map(p -> toPageSummary(p, templateNameById, pageTitleById))
                .collect(Collectors.toList());
    }

    private PageSummary toPageSummary(
            Page page,
            Map<String, String> templateNameById,
            Map<String, String> pageTitleById) {
        return PageSummary.builder()
                .title(page.getTitle())
                .templateName(templateNameById.getOrDefault(page.getTemplateId(), "?"))
                .values(truncatedValues(page.getValues()))
                .tags(page.getTags() != null ? List.copyOf(page.getTags()) : Collections.emptyList())
                .relatedPageTitles(resolveRelatedTitles(page.getRelatedPageIds(), pageTitleById))
                .build();
    }

    /**
     * Copie défensive des values avec troncature par valeur.
     * Les entrées vides/nulles sont filtrées pour alléger le prompt.
     */
    private Map<String, String> truncatedValues(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : source.entrySet()) {
            String v = e.getValue();
            if (v == null || v.isBlank()) continue;
            out.put(e.getKey(), truncate(v));
        }
        return out;
    }

    private String truncate(String value) {
        if (value.length() <= MAX_VALUE_LENGTH) return value;
        return value.substring(0, MAX_VALUE_LENGTH) + "…";
    }

    /**
     * Résout les IDs de pages liées en titres. Un ID qui ne matche rien
     * (page supprimée entre-temps) est silencieusement ignoré — pas de "?"
     * qui polluerait le prompt.
     */
    private List<String> resolveRelatedTitles(
            List<String> relatedIds, Map<String, String> pageTitleById) {
        if (relatedIds == null || relatedIds.isEmpty()) {
            return Collections.emptyList();
        }
        return relatedIds.stream()
                .map(pageTitleById::get)
                .filter(title -> title != null && !title.isBlank())
                .collect(Collectors.toList());
    }

    private List<String> extractUniqueTags(List<Page> pages) {
        return pages.stream()
                .filter(p -> p.getTags() != null)
                .flatMap(p -> p.getTags().stream())
                .distinct()
                .collect(Collectors.toList());
    }
}
