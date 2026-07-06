package com.loremind.domain.campaigncontext.ports;

import com.loremind.domain.campaigncontext.itemcatalog.ItemCatalog;

import java.util.List;
import java.util.Optional;

/**
 * Port de sortie pour la persistance des {@link ItemCatalog}.
 */
public interface ItemCatalogRepository {

    ItemCatalog save(ItemCatalog catalog);

    Optional<ItemCatalog> findById(String id);

    List<ItemCatalog> findByCampaignId(String campaignId);

    void deleteById(String id);

    boolean existsById(String id);

    /** Recherche par nom (insensible à la casse) — alimente la recherche globale. */
    List<ItemCatalog> searchByName(String query);
}
