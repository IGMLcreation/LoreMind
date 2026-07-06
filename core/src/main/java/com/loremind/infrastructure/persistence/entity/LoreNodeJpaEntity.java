package com.loremind.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Entité JPA pour la persistance des LoreNodes en base de données PostgreSQL.
 * Structure hiérarchique via parentId (auto-référence).
 */
@Entity
@Table(name = "lore_nodes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoreNodeJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** Clé de l'icône lucide-angular (ex: "users", "map-pin"). Nullable : un dossier peut ne pas avoir d'icône. */
    @Column(length = 64)
    private String icon;

    @Column(name = "parent_id")
    private Long parentId; // Auto-référence pour l'arborescence

    @Column(name = "lore_id", nullable = false)
    private Long loreId;

    /** Position parmi les dossiers frères. Colonne quotée : `order` est un mot-clé SQL. */
    @Column(name = "\"order\"", nullable = false)
    private int order;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now(ZoneId.systemDefault());
        updatedAt = LocalDateTime.now(ZoneId.systemDefault());
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now(ZoneId.systemDefault());
    }
}
