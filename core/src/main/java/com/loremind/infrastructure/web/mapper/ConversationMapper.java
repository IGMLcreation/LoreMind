package com.loremind.infrastructure.web.mapper;

import com.loremind.domain.conversationcontext.Conversation;
import com.loremind.domain.conversationcontext.ConversationMessage;
import com.loremind.infrastructure.web.dto.conversationcontext.ConversationDTO;
import com.loremind.infrastructure.web.dto.conversationcontext.ConversationMessageDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Conversion Domaine <-> DTO pour le contexte Conversation.
 *
 * {@link #toListDTO(Conversation)} omet les messages — utilise pour le
 * listing sidebar ou on n'expose que les metadonnees.
 */
@Component
public class ConversationMapper {

    public ConversationDTO toDTO(Conversation c) {
        List<ConversationMessageDTO> msgs = c.getMessages() == null
                ? List.of()
                : c.getMessages().stream().map(this::toMessageDTO).collect(Collectors.toList());
        return ConversationDTO.builder()
                .id(c.getId())
                .title(c.getTitle())
                .loreId(c.getLoreId())
                .campaignId(c.getCampaignId())
                .entityType(c.getEntityType())
                .entityId(c.getEntityId())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .messages(msgs)
                .build();
    }

    /** Variante listing : pas de messages pour alleger la payload. */
    public ConversationDTO toListDTO(Conversation c) {
        return ConversationDTO.builder()
                .id(c.getId())
                .title(c.getTitle())
                .loreId(c.getLoreId())
                .campaignId(c.getCampaignId())
                .entityType(c.getEntityType())
                .entityId(c.getEntityId())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .messages(null)
                .build();
    }

    public ConversationMessageDTO toMessageDTO(ConversationMessage m) {
        return ConversationMessageDTO.builder()
                .id(m.getId())
                .role(m.getRole())
                .content(m.getContent())
                .createdAt(m.getCreatedAt())
                .build();
    }
}
