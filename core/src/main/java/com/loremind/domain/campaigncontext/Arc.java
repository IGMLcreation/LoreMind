package com.loremind.domain.campaigncontext;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entité de domaine représentant un Arc narratif.
 * Division majeure d'une Campaign (ex: "L'arc sombre").
 * Entité pure du domaine, sans dépendance technique.
 */
@Data
@Builder
public class Arc {

    private String id;
    private String name;
    private String description;      // = Synopsis dans l'UI
    private String campaignId;       // Référence vers la Campaign parente
    private int order;               // Ordre de l'arc dans la campagne

    // Champs narratifs enrichis (voir docs/maquettes/campagne/détail/)
    private String themes;           // Thèmes principaux explorés dans cet arc
    private String stakes;           // Enjeux globaux pour les personnages
    private String gmNotes;          // Notes privées du MJ (non exportées vers FoundryVTT)
    private String rewards;          // Récompenses et progression
    private String resolution;       // Dénouement prévu

    /**
     * IDs des pages du Lore associées à cet arc (weak cross-context references).
     * Permet au MJ de lier des PNJ, lieux ou lore items qui jouent un rôle dans cet arc.
     * Ne contient que des IDs ; pas d'import du Lore Context (respect des Bounded Contexts).
     * Initialisé en {@link ArrayList} vide dans le builder pour éviter les NPE.
     */
    @Builder.Default
    private List<String> relatedPageIds = new ArrayList<>();

    /**
     * IDs des images (Shared Kernel) servant d'illustrations a cet arc (ambiance).
     * Galerie ordonnee : la 1ere image est l'illustration principale.
     */
    @Builder.Default
    private List<String> illustrationImageIds = new ArrayList<>();

    /**
     * IDs des images utilisees comme cartes / plans (outil de table).
     */
    @Builder.Default
    private List<String> mapImageIds = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
