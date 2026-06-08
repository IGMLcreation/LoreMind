package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.CatalogItem;
import com.loremind.domain.campaigncontext.ItemCatalog;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.campaigncontext.ports.ItemCatalogGenerator;
import com.loremind.domain.campaigncontext.ports.ItemCatalogRepository;
import com.loremind.domain.gamesystemcontext.ports.GameSystemRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service d'application des catalogues d'objets (campagne) : CRUD + génération IA.
 */
@Service
public class ItemCatalogService {

    private final ItemCatalogRepository repository;
    private final ItemCatalogGenerator generator;
    private final CampaignRepository campaignRepository;
    private final GameSystemRepository gameSystemRepository;

    public ItemCatalogService(
            ItemCatalogRepository repository,
            ItemCatalogGenerator generator,
            CampaignRepository campaignRepository,
            GameSystemRepository gameSystemRepository) {
        this.repository = repository;
        this.generator = generator;
        this.campaignRepository = campaignRepository;
        this.gameSystemRepository = gameSystemRepository;
    }

    public record CatalogData(
            String name,
            String description,
            String icon,
            List<CatalogItem> items,
            String campaignId,
            Integer order
    ) {}

    public ItemCatalog createCatalog(CatalogData data) {
        int order = data.order() != null ? data.order() : nextOrderFor(data.campaignId());
        ItemCatalog catalog = ItemCatalog.builder()
                .name(data.name())
                .description(data.description())
                .icon(data.icon())
                .items(copyItems(data.items()))
                .campaignId(data.campaignId())
                .order(order)
                .build();
        return repository.save(catalog);
    }

    public Optional<ItemCatalog> getCatalogById(String id) {
        return repository.findById(id);
    }

    public List<ItemCatalog> getCatalogsByCampaignId(String campaignId) {
        return repository.findByCampaignId(campaignId);
    }

    public ItemCatalog updateCatalog(String id, CatalogData data) {
        ItemCatalog existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Catalogue d'objets introuvable: " + id));
        existing.setName(data.name());
        existing.setDescription(data.description());
        existing.setIcon(data.icon());
        existing.setItems(copyItems(data.items()));
        if (data.order() != null) {
            existing.setOrder(data.order());
        }
        return repository.save(existing);
    }

    public void deleteCatalog(String id) {
        repository.deleteById(id);
    }

    /** Génère une PROPOSITION de catalogue (non persistée) via l'IA, contextualisée campagne. */
    public ItemCatalog generateProposal(String campaignId, String description) {
        ItemCatalogGenerator.GeneratedCatalog g = generator.generate(description, buildContext(campaignId));
        return ItemCatalog.builder()
                .name(g.name())
                .description(g.description())
                .campaignId(campaignId)
                .items(g.items() != null ? new ArrayList<>(g.items()) : new ArrayList<>())
                .build();
    }

    private static List<CatalogItem> copyItems(List<CatalogItem> items) {
        return items != null ? new ArrayList<>(items) : new ArrayList<>();
    }

    private String buildContext(String campaignId) {
        if (campaignId == null) return "";
        Campaign campaign = campaignRepository.findById(campaignId).orElse(null);
        if (campaign == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("Campagne : ").append(campaign.getName());
        if (campaign.getDescription() != null && !campaign.getDescription().isBlank()) {
            sb.append(" — ").append(campaign.getDescription().trim());
        }
        if (campaign.getGameSystemId() != null && !campaign.getGameSystemId().isBlank()) {
            gameSystemRepository.findById(campaign.getGameSystemId())
                    .ifPresent(gs -> sb.append("\nSystème de jeu : ").append(gs.getName()));
        }
        return sb.toString();
    }

    private int nextOrderFor(String campaignId) {
        return repository.findByCampaignId(campaignId).stream()
                .mapToInt(ItemCatalog::getOrder)
                .max()
                .orElse(-1) + 1;
    }
}
