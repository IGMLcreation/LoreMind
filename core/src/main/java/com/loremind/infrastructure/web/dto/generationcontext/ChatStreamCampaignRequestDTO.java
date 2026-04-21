package com.loremind.infrastructure.web.dto.generationcontext;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO HTTP de requête pour POST /api/ai/chat/stream-campaign.
 * <p>
 * Le Core charge lui-même :
 *  - la carte narrative à partir de {campaignId} ;
 *  - la carte du Lore associé si la campagne a un `loreId` (asymétrie :
 *    une Campagne voit son Lore, l'inverse n'est pas vrai) ;
 *  - l'entité narrative focalisée si {entityType}+{entityId} sont fournis.
 * <p>
 * Le frontend n'a qu'à envoyer l'historique + les IDs.
 */
@Data
@NoArgsConstructor
public class ChatStreamCampaignRequestDTO {

    private String campaignId;

    /** Optionnel : "arc", "chapter" ou "scene". Si fourni, doit être accompagné d'entityId. */
    private String entityType;
    /** Optionnel : ID de l'entité narrative en cours d'édition. */
    private String entityId;

    private List<ChatMessageDTO> messages;
}
