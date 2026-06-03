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
     * Ancienne référence directe vers la Campaign. Conservée (nullable) pour la
     * rétrocompatibilité de la migration, mais plus utilisée par le code.
     * À supprimer manuellement quand toutes les sessions auront leur playthrough_id.
     */
    @Column(name = "campaign_id")
    private String campaignId;

    /**
     * ID du Playthrough (partie) auquel cette session appartient.
     * Weak reference inter-contexte.
     */
    @Column(name = "playthrough_id")
    private Long playthroughId;

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
