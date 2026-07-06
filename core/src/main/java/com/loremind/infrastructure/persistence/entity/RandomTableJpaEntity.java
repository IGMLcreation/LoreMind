package com.loremind.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Entité JPA d'une table aléatoire (parent) avec ses entrées ordonnées (enfants).
 * Tables créées automatiquement par Hibernate (ddl-auto=update).
 */
@Entity
@Table(name = "random_tables")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RandomTableJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "dice_formula", nullable = false, length = 32)
    private String diceFormula;

    @Column(length = 64)
    private String icon;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(name = "\"order\"", nullable = false)
    private int order;

    @OneToMany(
            mappedBy = "randomTable",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @OrderBy("position ASC")
    @Builder.Default
    private List<RandomTableEntryJpaEntity> entries = new ArrayList<>();

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
