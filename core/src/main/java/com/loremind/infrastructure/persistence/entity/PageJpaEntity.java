package com.loremind.infrastructure.persistence.entity;

import com.loremind.infrastructure.persistence.converter.StringListJsonConverter;
import com.loremind.infrastructure.persistence.converter.StringListMapJsonConverter;
import com.loremind.infrastructure.persistence.converter.StringMapJsonConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Entité JPA pour la persistance des Pages en PostgreSQL.
 * - lore_id dénormalisé (accès rapide aux pages d'un lore sans passer par nodes).
 * - values / tags / related_page_ids stockés en JSON (TEXT via converters).
 * - Note : colonne `values_json` car `values` est un mot-clé SQL réservé.
 */
@Entity
@Table(name = "pages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lore_id", nullable = false)
    private Long loreId;

    @Column(name = "node_id", nullable = false)
    private Long nodeId;

    @Column(name = "template_id")
    private Long templateId;

    @Column(nullable = false)
    private String title;

    @Column(name = "values_json", columnDefinition = "TEXT")
    @Convert(converter = StringMapJsonConverter.class)
    private Map<String, String> values;

    /** Stocke les IDs d'images par champ IMAGE du template. JSON dans colonne TEXT. */
    @Column(name = "image_values_json", columnDefinition = "TEXT")
    @Convert(converter = StringListMapJsonConverter.class)
    private Map<String, List<String>> imageValues;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "tags", columnDefinition = "TEXT")
    @Convert(converter = StringListJsonConverter.class)
    private List<String> tags;

    @Column(name = "related_page_ids", columnDefinition = "TEXT")
    @Convert(converter = StringListJsonConverter.class)
    private List<String> relatedPageIds;

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
