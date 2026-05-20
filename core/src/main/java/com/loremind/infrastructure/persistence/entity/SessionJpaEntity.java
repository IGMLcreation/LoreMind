package com.loremind.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entité JPA pour la persistance des Sessions en PostgreSQL.
 * Adaptateur d'infrastructure — n'est PAS dans le domaine.
 */
@Entity
@Table(name = "sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /**
     * ID de la Campaign associée. Pas de @ManyToOne / pas de FK : c'est une
     * weak reference inter-contexte (Play Context ↔ Campaign Context).
     */
    @Column(name = "campaign_id", nullable = false)
    private String campaignId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /** Null = session en cours. */
    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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
