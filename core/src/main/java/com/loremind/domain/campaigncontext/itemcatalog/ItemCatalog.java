package com.loremind.domain.campaigncontext.itemcatalog;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Catalogue d'objets prédéfinis d'une campagne : une boutique, un butin, un trésor…
 * Le MJ le remplit à la main ou via l'IA, et le consulte (notamment en session) quand
 * les joueurs visitent une échoppe. Scope campagne (cross-aggregate via ID).
 */
@Data
@Builder
public class ItemCatalog {

    private String id;
    private String name;

    /** Description libre (à quoi sert ce catalogue / cette boutique). Nullable. */
    private String description;

    /** Clé d'icône (lucide) pour la sidebar/fiche. Nullable. */
    private String icon;

    /** Référence vers la Campaign parente (cross-aggregate via ID). */
    private String campaignId;

    /** Ordre d'affichage dans la liste des catalogues de la campagne. */
    private int order;

    /** Objets ordonnés du catalogue. Jamais null après construction. */
    private List<CatalogItem> items;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public List<CatalogItem> getItems() {
        if (items == null) items = new ArrayList<>();
        return items;
    }
}
