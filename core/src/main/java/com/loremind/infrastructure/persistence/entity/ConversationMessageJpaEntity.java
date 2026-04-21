package com.loremind.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * Persistance d'un message appartenant a une {@link ConversationJpaEntity}.
 * Les messages sont ordonnes par createdAt ASC (ordre d'ajout = ordre lu).
 */
@Entity
@Table(name = "conversation_messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMessageJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** "user" | "assistant" | "system". */
    @Column(nullable = false, length = 16)
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Reference vers la conversation parent. ToString exclu pour eviter une
     * boucle infinie quand Lombok genere toString() (conv -> messages -> conv...).
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    @ToString.Exclude
    private ConversationJpaEntity conversation;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
