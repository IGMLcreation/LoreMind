package com.loremind.infrastructure.web.dto.lorecontext;

import lombok.Data;

/**
 * DTO pour l'entité Lore.
 * Objet de transfert de données pour l'API REST.
 * Contient uniquement les champs nécessaires pour le Frontend.
 */
@Data
public class LoreDTO {

    private String id;
    private String name;
    private String description;
    private int nodeCount;
    private int pageCount;
}
