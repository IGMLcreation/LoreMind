package com.loremind.infrastructure.web.dto.conversationcontext;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO d'une conversation. Les messages sont inclus uniquement sur GET /{id}
 * (null pour les reponses de listing afin d'alleger la sidebar).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDTO {
    private String id;
    private String title;
    private String loreId;
    private String campaignId;
    private String entityType;
    private String entityId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ConversationMessageDTO> messages;
}
