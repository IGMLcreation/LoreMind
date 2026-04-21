package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.conversationcontext.Conversation;
import com.loremind.domain.conversationcontext.ConversationMessage;
import com.loremind.domain.conversationcontext.ports.ConversationRepository;
import com.loremind.infrastructure.persistence.entity.ConversationJpaEntity;
import com.loremind.infrastructure.persistence.entity.ConversationMessageJpaEntity;
import com.loremind.infrastructure.persistence.jpa.ConversationJpaRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adaptateur Postgres pour ConversationRepository.
 *
 * Les methodes de listing ne chargent PAS les messages (messages LAZY,
 * liste vide renvoyee cote domaine) — la sidebar n'a besoin que des
 * meta-donnees. findById charge les messages via fetch explicite de la
 * collection dans une transaction.
 */
@Repository
public class PostgresConversationRepository implements ConversationRepository {

    private final ConversationJpaRepository jpa;

    public PostgresConversationRepository(ConversationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public Conversation save(Conversation conversation) {
        ConversationJpaEntity entity = toJpaEntity(conversation);
        ConversationJpaEntity saved = jpa.save(entity);
        return toDomain(saved, true);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Conversation> findById(String id) {
        return jpa.findById(Long.parseLong(id))
                .map(e -> {
                    // Force l'initialisation LAZY avant de sortir de la transaction.
                    e.getMessages().size();
                    return toDomain(e, true);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<Conversation> findByContext(String loreId, String campaignId, String entityType, String entityId) {
        List<ConversationJpaEntity> rows;
        if (loreId != null) {
            rows = (entityType == null)
                    ? jpa.findByLoreRoot(loreId)
                    : jpa.findByLoreAndEntity(loreId, entityType, entityId);
        } else if (campaignId != null) {
            rows = (entityType == null)
                    ? jpa.findByCampaignRoot(campaignId)
                    : jpa.findByCampaignAndEntity(campaignId, entityType, entityId);
        } else {
            return Collections.emptyList();
        }
        return rows.stream().map(e -> toDomain(e, false)).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteById(String id) {
        jpa.deleteById(Long.parseLong(id));
    }

    @Override
    @Transactional
    public ConversationMessage appendMessage(String conversationId, ConversationMessage message) {
        ConversationJpaEntity conv = jpa.findById(Long.parseLong(conversationId))
                .orElseThrow(() -> new EntityNotFoundException("Conversation " + conversationId));

        ConversationMessageJpaEntity msg = ConversationMessageJpaEntity.builder()
                .role(message.getRole())
                .content(message.getContent())
                .conversation(conv)
                .build();
        conv.getMessages().add(msg);
        // Force updatedAt via @PreUpdate en modifiant la conv (touch).
        conv.setUpdatedAt(java.time.LocalDateTime.now());

        ConversationJpaEntity saved = jpa.save(conv);
        ConversationMessageJpaEntity persisted = saved.getMessages().get(saved.getMessages().size() - 1);
        return toDomainMessage(persisted);
    }

    @Override
    @Transactional
    public void updateTitle(String conversationId, String title) {
        ConversationJpaEntity conv = jpa.findById(Long.parseLong(conversationId))
                .orElseThrow(() -> new EntityNotFoundException("Conversation " + conversationId));
        conv.setTitle(title);
        jpa.save(conv);
    }

    // ---------- Mapping ----------

    private ConversationJpaEntity toJpaEntity(Conversation c) {
        Long id = c.getId() != null ? Long.parseLong(c.getId()) : null;
        return ConversationJpaEntity.builder()
                .id(id)
                .title(c.getTitle())
                .loreId(c.getLoreId())
                .campaignId(c.getCampaignId())
                .entityType(c.getEntityType())
                .entityId(c.getEntityId())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    private Conversation toDomain(ConversationJpaEntity e, boolean withMessages) {
        List<ConversationMessage> msgs = withMessages
                ? e.getMessages().stream().map(this::toDomainMessage).collect(Collectors.toList())
                : new java.util.ArrayList<>();
        return Conversation.builder()
                .id(e.getId().toString())
                .title(e.getTitle())
                .loreId(e.getLoreId())
                .campaignId(e.getCampaignId())
                .entityType(e.getEntityType())
                .entityId(e.getEntityId())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .messages(msgs)
                .build();
    }

    private ConversationMessage toDomainMessage(ConversationMessageJpaEntity e) {
        return ConversationMessage.builder()
                .id(e.getId() != null ? e.getId().toString() : null)
                .role(e.getRole())
                .content(e.getContent())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
