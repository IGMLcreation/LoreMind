package com.loremind.application.lorecontext;

import com.loremind.domain.lorecontext.Lore;
import com.loremind.domain.lorecontext.ports.LoreNodeRepository;
import com.loremind.domain.lorecontext.ports.LoreRepository;
import com.loremind.domain.lorecontext.ports.PageRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service d'application pour le contexte Lore.
 * Orchestre la logique métier en utilisant le Port LoreRepository.
 * Fait partie de la couche Application de l'Architecture Hexagonale.
 * <p>
 * Note: les compteurs nodeCount/pageCount sont calculés à la volée via
 * countByLoreId sur les ports LoreNode et Page, plutôt que stockés en BDD
 * (la colonne existe encore mais n'est plus fiable). Source of truth = les
 * tables nodes/pages elles-mêmes, jamais de désync possible.
 */
@Service
public class LoreService {

    private final LoreRepository loreRepository;
    private final LoreNodeRepository loreNodeRepository;
    private final PageRepository pageRepository;

    public LoreService(LoreRepository loreRepository,
                       LoreNodeRepository loreNodeRepository,
                       PageRepository pageRepository) {
        this.loreRepository = loreRepository;
        this.loreNodeRepository = loreNodeRepository;
        this.pageRepository = pageRepository;
    }

    public Lore createLore(String name, String description) {
        Lore lore = Lore.builder()
                .name(name)
                .description(description)
                .nodeCount(0)
                .pageCount(0)
                .build();
        return loreRepository.save(lore);
    }

    public Optional<Lore> getLoreById(String id) {
        return loreRepository.findById(id).map(this::withCounts);
    }

    public List<Lore> getAllLores() {
        return loreRepository.findAll().stream()
                .map(this::withCounts)
                .collect(Collectors.toList());
    }

    /**
     * Enrichit un Lore avec les compteurs calculés à la volée.
     * N+1 acceptable à l'échelle actuelle (quelques dizaines de Lores max).
     */
    private Lore withCounts(Lore lore) {
        lore.setNodeCount((int) loreNodeRepository.countByLoreId(lore.getId()));
        lore.setPageCount((int) pageRepository.countByLoreId(lore.getId()));
        return lore;
    }

    /** Recherche par nom (ILIKE). Résultats sans compteurs — pas utile pour la command palette. */
    public List<Lore> searchLores(String query) {
        if (query == null || query.isBlank()) return List.of();
        return loreRepository.searchByName(query.trim());
    }

    public Lore updateLore(String id, String name, String description) {
        Optional<Lore> existingLore = loreRepository.findById(id);
        if (existingLore.isEmpty()) {
            throw new IllegalArgumentException("Lore non trouvé avec l'ID: " + id);
        }
        
        Lore lore = existingLore.get();
        lore.setName(name);
        lore.setDescription(description);
        return loreRepository.save(lore);
    }

    public void deleteLore(String id) {
        loreRepository.deleteById(id);
    }
}
