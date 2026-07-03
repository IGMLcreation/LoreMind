package com.loremind.infrastructure.web.dto.campaigncontext;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO pour l'entité Quest (API REST). Calqué sur {@code ChapterDTO}.
 */
@Data
public class QuestDTO {

    private String id;
    private String campaignId;
    /** Arc de rattachement (nullable). Non nul ⇒ quête d'un arc HUB ; null ⇒ transverse. */
    private String arcId;
    private String name;
    private String description;
    private String icon;
    private int order;

    /** Conditions de déblocage (ET logique). Donnée de SCÉNARIO. */
    private List<PrerequisiteDTO> prerequisites = new ArrayList<>();

    /** Nœuds narratifs (Chapitres / Scènes) traversés par la quête. */
    private List<QuestNodeRefDTO> nodes = new ArrayList<>();

    /**
     * Statut de progression — lecture seule, populé uniquement quand le client demande
     * l'enrichissement pour un Playthrough donné (param ?playthroughId=).
     * Valeurs : "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED".
     */
    private String progressionStatus;

    /**
     * Statut effectif calculé côté backend ("LOCKED" | "AVAILABLE" | "IN_PROGRESS" | "COMPLETED").
     * Read-only — populé en même temps que {@code progressionStatus}.
     */
    private String effectiveStatus;

    // Champs narratifs
    private String gmNotes;
    private String playerObjectives;
    private String narrativeStakes;

    /** IDs des pages du Lore liées (weak cross-context references). */
    private List<String> relatedPageIds = new ArrayList<>();

    /** IDs des images (Shared Kernel) illustrant la quête. */
    private List<String> illustrationImageIds = new ArrayList<>();
}
