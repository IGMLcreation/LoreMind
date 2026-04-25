package com.loremind.infrastructure.persistence.entity;

import com.loremind.infrastructure.persistence.converter.StringListJsonConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entité JPA pour la persistance des Arcs en base de données PostgreSQL.
 */
@Entity
@Table(name = "arcs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArcJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(name = "\"order\"", nullable = false)
    private int order;

    @Column
    private String icon;

    // Champs narratifs enrichis — ajoutés automatiquement par Hibernate DDL (ddl-auto=update)
    @Column(columnDefinition = "TEXT")
    private String themes;

    @Column(columnDefinition = "TEXT")
    private String stakes;

    @Column(name = "gm_notes", columnDefinition = "TEXT")
    private String gmNotes;

    @Column(columnDefinition = "TEXT")
    private String rewards;

    @Column(columnDefinition = "TEXT")
    private String resolution;

    /**
     * IDs des pages du Lore liées à cet arc, stockés en JSON dans une colonne TEXT.
     * Pas de FK cross-context : respect des Bounded Contexts.
     */
    @Column(name = "related_page_ids", columnDefinition = "TEXT")
    @Convert(converter = StringListJsonConverter.class)
    @Builder.Default
    private List<String> relatedPageIds = new ArrayList<>();

    /** IDs des images (Shared Kernel) illustrant cet arc. JSON dans colonne TEXT. */
    @Column(name = "illustration_image_ids", columnDefinition = "TEXT")
    @Convert(converter = StringListJsonConverter.class)
    @Builder.Default
    private List<String> illustrationImageIds = new ArrayList<>();

    /** IDs des images "cartes / plans". */
    @Column(name = "map_image_ids", columnDefinition = "TEXT")
    @Convert(converter = StringListJsonConverter.class)
    @Builder.Default
    private List<String> mapImageIds = new ArrayList<>();

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
