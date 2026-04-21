package com.loremind.infrastructure.web.dto.campaigncontext;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO pour l'entité Arc.
 * Objet de transfert de données pour l'API REST.
 */
@Data
public class ArcDTO {

    private String id;
    private String name;
    private String description;
    private String campaignId;
    private int order;

    // Champs narratifs enrichis
    private String themes;
    private String stakes;
    private String gmNotes;
    private String rewards;
    private String resolution;

    /** IDs des pages du Lore liées à cet arc (weak cross-context references). */
    private List<String> relatedPageIds = new ArrayList<>();

    /** IDs des images (Shared Kernel) illustrant cet arc. */
    private List<String> illustrationImageIds = new ArrayList<>();
}
