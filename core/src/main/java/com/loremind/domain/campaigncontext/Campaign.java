package com.loremind.domain.campaigncontext;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Entité de domaine représentant une Campaign.
 * Conteneur du SCÉNARIO (générique, ré-utilisable par plusieurs tables).
 *
 * <p>Toute donnée dynamique propre à une table jouée (progression des quêtes,
 * flags narratifs, sessions, PJ) vit dans un {@link com.loremind.domain.playcontext.Playthrough}.</p>
 *
 * <p>Entité pure du domaine, sans dépendance technique.</p>
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
     * Référence faible vers un Lore. Nullable.
     * Ce n'est qu'un ID : le Campaign Context ne dépend PAS du Lore Context.
     */
    private String loreId;

    /**
     * Référence faible vers un GameSystem. Nullable.
     */
    private String gameSystemId;

    public boolean isLinkedToLore() {
        return this.loreId != null && !this.loreId.isBlank();
    }

    public boolean isLinkedToGameSystem() {
        return this.gameSystemId != null && !this.gameSystemId.isBlank();
    }
}
