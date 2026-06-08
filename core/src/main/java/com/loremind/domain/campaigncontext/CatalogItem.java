package com.loremind.domain.campaigncontext;

import lombok.Builder;
import lombok.Data;

/**
 * Un objet d'un {@link ItemCatalog} (boutique, butin, trésor…).
 * Value object possédé par le catalogue : remplacé en bloc à chaque mise à jour.
 */
@Data
@Builder
public class CatalogItem {

    private String name;

    /** Prix libre (ex. « 50 po », « 2 pa »). Nullable. */
    private String price;

    /** Catégorie de regroupement (ex. « Armes », « Potions »). Nullable. */
    private String category;

    /** Description / effet (markdown). Nullable. */
    private String description;
}
