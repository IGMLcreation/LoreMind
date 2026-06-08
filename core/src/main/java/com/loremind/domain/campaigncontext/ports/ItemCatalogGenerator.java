package com.loremind.domain.campaigncontext.ports;

import com.loremind.domain.campaigncontext.CatalogItem;

import java.util.List;

/**
 * Port de sortie : génération IA d'un catalogue d'objets. Implémenté par un client
 * du Brain (service IA Python).
 */
public interface ItemCatalogGenerator {

    /** Catalogue proposé (non persisté) à partir d'une description. */
    record GeneratedCatalog(String name, String description, List<CatalogItem> items) {}

    /**
     * Génère une proposition de catalogue (objets) sur le sujet donné, en s'appuyant
     * sur le contexte (campagne, système…) s'il est fourni.
     */
    GeneratedCatalog generate(String description, String context);
}
