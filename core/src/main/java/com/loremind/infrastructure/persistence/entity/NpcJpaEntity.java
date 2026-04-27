package com.loremind.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entité JPA pour les fiches de PNJ d'une campagne.
 * Pas de FK physique vers campaigns (weak reference cross-agrégat intra-contexte).
 */
@Entity
@Table(name = "npcs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NpcJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "markdown_content", columnDefinition = "TEXT")
    private String markdownContent;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(name = "\"order\"", nullable = false)
    private int order;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
