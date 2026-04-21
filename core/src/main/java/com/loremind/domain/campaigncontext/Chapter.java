package com.loremind.domain.campaigncontext;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entité de domaine représentant un Chapter.
 * Subdivision d'un Arc (ex: "Chapitre 1: Le début").
 * Entité pure du domaine, sans dépendance technique.
 */
@Data
@Builder
public class Chapter {

    private String id;
    private String name;
    private String description;       // = Synopsis du chapitre dans l'UI
    private String arcId;              // Référence vers l'Arc parent
    private int order;                 // Ordre du chapitre dans l'arc

    // Champs narratifs enrichis (voir docs/maquettes/campagne/détail/)
    private String gmNotes;            // Notes privées du MJ (non exportées vers FoundryVTT)
    private String playerObjectives;   // Objectifs des joueurs dans ce chapitre
    private String narrativeStakes;    // Enjeux narratifs dramatiques

    /**
     * IDs des pages du Lore associées à ce chapitre (weak cross-context references).
     * Permet au MJ de lier des PNJ / lieux / éléments du Lore qui apparaissent dans ce chapitre.
     */
    @Builder.Default
    private List<String> relatedPageIds = new ArrayList<>();

    /**
     * IDs des images (Shared Kernel) illustrant ce chapitre.
     */
    @Builder.Default
    private List<String> illustrationImageIds = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
