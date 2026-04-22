package com.loremind.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Entité JPA pour la persistance des Campaigns en base de données PostgreSQL.
 */
@Entity
@Table(name = "campaigns")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "arcs_count", nullable = false)
    private int arcsCount;

    /**
     * ID du Lore associé (nullable).
     * Pas de @ManyToOne / pas de FK : c'est une weak reference inter-contexte.
     * Le Campaign Context et le Lore Context doivent pouvoir évoluer indépendamment.
     */
    @Column(name = "lore_id")
    private String loreId;

    /**
     * ID du GameSystem associé (nullable).
     * Weak reference inter-contexte — pas de @ManyToOne / pas de FK DB.
     */
    @Column(name = "game_system_id")
    private String gameSystemId;

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
