package com.loremind.application.lorecontext;

import com.loremind.domain.lorecontext.Page;
import com.loremind.domain.lorecontext.ports.PageRepository;
import org.springframework.stereotype.Service;

import com.loremind.domain.shared.CollectionUtils;
import com.loremind.domain.shared.ReorderSupport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

/**
 * Service d'application pour le contexte Page.
 * Orchestre la logique métier via le Port PageRepository.
 * Couche Application de l'Architecture Hexagonale.
 */
@Service
public class PageService {

    private final PageRepository pageRepository;

    public PageService(PageRepository pageRepository) {
        this.pageRepository = pageRepository;
    }

    /** Création MVP : seuls les champs structurels sont requis. Le contenu est
     *  enrichi plus tard depuis l'écran d'édition. */
    public Page createPage(String loreId, String nodeId, String templateId, String title) {
        Page page = Page.builder()
                .loreId(loreId)
                .nodeId(nodeId)
                .templateId(templateId)
                .title(title)
                .order(nextOrderFor(nodeId))
                .values(new HashMap<>())
                .tags(new ArrayList<>())
                .relatedPageIds(new ArrayList<>())
                .build();
        return pageRepository.save(page);
    }

    /** Prochain ordre pour une page neuve : après les pages existantes du même dossier. */
    private int nextOrderFor(String nodeId) {
        if (nodeId == null) return 0;
        return pageRepository.findByNodeId(nodeId).stream()
                .mapToInt(Page::getOrder)
                .max().orElse(-1) + 1;
    }

    public Optional<Page> getPageById(String id) {
        return pageRepository.findById(id);
    }

    public List<Page> getAllPages() {
        return pageRepository.findAll();
    }

    public List<Page> getPagesByLoreId(String loreId) {
        return pageRepository.findByLoreId(loreId);
    }

    public List<Page> getPagesByNodeId(String nodeId) {
        return pageRepository.findByNodeId(nodeId);
    }

    public List<Page> searchPages(String query) {
        if (query == null || query.isBlank()) return List.of();
        return pageRepository.searchByTitle(query.trim());
    }

    /**
     * Met à jour une page existante.
     * Parameter Object pattern : le controller construit une Page "changes"
     * avec les nouvelles valeurs, le service recharge et applique les champs modifiables.
     * Les champs structurels immuables sont : id, loreId, templateId.
     * Le nodeId est mutable (déplacement d'une page d'un noeud à l'autre).
     */
    public Page updatePage(String id, Page changes) {
        Page existing = pageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Page non trouvée avec l'ID: " + id));

        existing.setTitle(changes.getTitle());
        existing.setNodeId(changes.getNodeId());
        existing.setValues(CollectionUtils.copyMap(changes.getValues()));
        existing.setImageValues(CollectionUtils.copyMap(changes.getImageValues()));
        existing.setKeyValueValues(CollectionUtils.copyMap(changes.getKeyValueValues()));
        existing.setTableValues(CollectionUtils.copyMap(changes.getTableValues()));
        existing.setNotes(changes.getNotes());
        existing.setTags(CollectionUtils.copyList(changes.getTags()));
        existing.setRelatedPageIds(CollectionUtils.copyList(changes.getRelatedPageIds()));
        return pageRepository.save(existing);
    }

    public void deletePage(String id) {
        pageRepository.deleteById(id);
    }

    /**
     * Réordonne (et déplace) les pages d'un dossier : {@code order} = position dans la
     * liste, et {@code nodeId} = dossier cible (déplacement par glisser-déposer).
     */
    @org.springframework.transaction.annotation.Transactional
    public void reorderPages(String nodeId, List<String> orderedIds) {
        ReorderSupport.reorder(orderedIds,
                id -> pageRepository.findById(id).orElse(null),
                (page, i) -> {
                    if (nodeId != null && !nodeId.isBlank()) page.setNodeId(nodeId);
                    page.setOrder(i);
                },
                pageRepository::save);
    }
}
