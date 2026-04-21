package com.loremind.infrastructure.web.dto.conversationcontext;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload de creation. Le client fournit l'ancrage (lore ou campagne, +/-
 * entite focus). Le titre est optionnel — sera auto-genere apres le 1er
 * echange IA si absent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateConversationDTO {
    private String title;
    private String loreId;
    private String campaignId;
    private String entityType;
    private String entityId;
}
