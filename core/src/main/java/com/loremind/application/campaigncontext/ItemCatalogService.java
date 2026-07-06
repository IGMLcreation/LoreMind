package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.itemcatalog.CatalogItem;
import com.loremind.domain.campaigncontext.itemcatalog.ItemCatalog;
import com.loremind.domain.campaigncontext.ports.ItemCatalogGenerator;
import com.loremind.domain.campaigncontext.ports.ItemCatalogRepository;
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
    private final CampaignContextFormatter campaignContextFormatter;

    public ItemCatalogService(
            ItemCatalogRepository repository,
            ItemCatalogGenerator generator,
            CampaignContextFormatter campaignContextFormatter) {
        this.repository = repository;
        this.generator = generator;
        this.campaignContextFormatter = campaignContextFormatter;
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

    public List<ItemCatalog> searchCatalogs(String query) {
        if (query == null || query.isBlank()) return List.of();
        return repository.searchByName(query.trim());
    }

    /** Génère une PROPOSITION de catalogue (non persistée) via l'IA, contextualisée campagne. */
    public ItemCatalog generateProposal(String campaignId, String description) {
        ItemCatalogGenerator.GeneratedCatalog g = generator.generate(
                description, campaignContextFormatter.format(campaignId));
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

    private int nextOrderFor(String campaignId) {
        return repository.findByCampaignId(campaignId).stream()
                .mapToInt(ItemCatalog::getOrder)
                .max()
                .orElse(-1) + 1;
    }
}
