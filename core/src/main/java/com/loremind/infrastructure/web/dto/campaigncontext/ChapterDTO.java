package com.loremind.infrastructure.web.dto.campaigncontext;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO pour l'entité Chapter.
 * Objet de transfert de données pour l'API REST.
 */
@Data
public class ChapterDTO {

    private String id;
    private String name;
    private String description;
    private String arcId;
    private int order;

    // Champs narratifs enrichis
    private String gmNotes;
    private String playerObjectives;
    private String narrativeStakes;

    /** IDs des pages du Lore liées (weak cross-context references). */
    private List<String> relatedPageIds = new ArrayList<>();

    /** IDs des images (Shared Kernel) illustrant ce chapitre. */
    private List<String> illustrationImageIds = new ArrayList<>();
}
