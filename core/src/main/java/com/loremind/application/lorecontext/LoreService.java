package com.loremind.application.lorecontext;

import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.lorecontext.Lore;
import com.loremind.domain.lorecontext.LoreNode;
import com.loremind.domain.lorecontext.Page;
import com.loremind.domain.lorecontext.Template;
import com.loremind.domain.lorecontext.ports.LoreNodeRepository;
import com.loremind.domain.lorecontext.ports.LoreRepository;
import com.loremind.domain.lorecontext.ports.PageRepository;
import com.loremind.domain.lorecontext.ports.TemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final TemplateRepository templateRepository;
    private final CampaignRepository campaignRepository;

    public LoreService(LoreRepository loreRepository,
                       LoreNodeRepository loreNodeRepository,
                       PageRepository pageRepository,
                       TemplateRepository templateRepository,
                       CampaignRepository campaignRepository) {
        this.loreRepository = loreRepository;
        this.loreNodeRepository = loreNodeRepository;
        this.pageRepository = pageRepository;
        this.templateRepository = templateRepository;
        this.campaignRepository = campaignRepository;
    }

    /**
     * Compte des entités qui seront supprimées / détachées en cascade si le Lore
     * est effacé. `detachedCampaigns` : campagnes qui perdront leur référence à
     * ce Lore (leur loreId sera nullé) mais resteront présentes.
     */
    public record DeletionImpact(int folders, int pages, int templates, int detachedCampaigns) {}

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

    /**
     * Calcule l'impact d'une suppression de Lore en cascade : dossiers + pages
     * + templates supprimés, et campagnes qui seront détachées (loreId → null
     * sans être supprimées, car une campagne peut vivre sans univers).
     */
    public DeletionImpact getDeletionImpact(String id) {
        int folders = (int) loreNodeRepository.countByLoreId(id);
        int pages = (int) pageRepository.countByLoreId(id);
        int templates = templateRepository.findByLoreId(id).size();
        int detached = countCampaignsReferencingLore(id);
        return new DeletionImpact(folders, pages, templates, detached);
    }

    /**
     * Supprime le Lore et toutes ses entités dépendantes (dossiers, pages, templates).
     * Les campagnes qui référençaient ce Lore sont conservées — leur loreId est
     * mis à null (une campagne peut légitimement exister sans univers associé).
     * Opération transactionnelle : atomique.
     */
    @Transactional
    public void deleteLore(String id) {
        // Pages d'abord : elles référencent nodeId ET loreId, on les supprime
        // globalement via loreId pour éviter d'en rater une rattachée à un
        // node orphelin (ne devrait pas arriver, mais ceinture+bretelles).
        for (Page page : pageRepository.findByLoreId(id)) {
            pageRepository.deleteById(page.getId());
        }
        for (LoreNode node : loreNodeRepository.findByLoreId(id)) {
            loreNodeRepository.deleteById(node.getId());
        }
        for (Template template : templateRepository.findByLoreId(id)) {
            templateRepository.deleteById(template.getId());
        }
        // Détache les campagnes : on garde la campagne, on nulle juste la référence.
        for (Campaign campaign : campaignRepository.findAll()) {
            if (id.equals(campaign.getLoreId())) {
                campaign.setLoreId(null);
                campaignRepository.save(campaign);
            }
        }
        loreRepository.deleteById(id);
    }

    private int countCampaignsReferencingLore(String id) {
        int count = 0;
        for (Campaign campaign : campaignRepository.findAll()) {
            if (id.equals(campaign.getLoreId())) count++;
        }
        return count;
    }
}
