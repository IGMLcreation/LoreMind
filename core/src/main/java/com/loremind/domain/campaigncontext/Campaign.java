package com.loremind.domain.campaigncontext;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Entité de domaine représentant une Campaign.
 * Conteneur global pour organiser la narration d'une campagne.
 * Entité pure du domaine, sans dépendance technique.
 */
@Data
@Builder
public class Campaign {

    private String id;
    private String name;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int arcsCount;

    /**
     * Référence faible (weak reference) vers un Lore.
     * Nullable : une campagne peut exister sans univers associé (one-shot, test, pitch libre).
     * Ce n'est qu'un ID : le Campaign Context ne dépend PAS du Lore Context
     * (respect des Bounded Contexts en DDD).
     */
    private String loreId;

    /**
     * Référence faible (weak reference) vers un GameSystem.
     * Nullable : une campagne peut être "générique" (pas de système de JDR déclaré).
     * Weak reference pour respecter la séparation des Bounded Contexts.
     */
    private String gameSystemId;

    public boolean isLinkedToLore() {
        return this.loreId != null && !this.loreId.isBlank();
    }

    public boolean isLinkedToGameSystem() {
        return this.gameSystemId != null && !this.gameSystemId.isBlank();
    }
}
