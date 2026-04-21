package com.loremind.infrastructure.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistance d'une conversation de chat IA.
 *
 * Les refs loreId / campaignId / entityId sont des weak references (String,
 * pas de FK) — coherent avec la politique inter-contexte du reste du code.
 * Indexes compose pour accelerer le listing par contexte dans la sidebar.
 */
@Entity
@Table(name = "conversations", indexes = {
        @Index(name = "idx_conv_lore_entity", columnList = "lore_id,entity_type,entity_id,updated_at"),
        @Index(name = "idx_conv_campaign_entity", columnList = "campaign_id,entity_type,entity_id,updated_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(name = "lore_id")
    private String loreId;

    @Column(name = "campaign_id")
    private String campaignId;

    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "entity_id")
    private String entityId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Messages enfants. Charges a la demande (fetch=LAZY) pour ne pas plomber
     * le listing sidebar. Cascade ALL + orphanRemoval : la suppression d'une
     * conversation efface ses messages.
     */
    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC, id ASC")
    @Builder.Default
    private List<ConversationMessageJpaEntity> messages = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
