package com.loremind.infrastructure.persistence.entity;

import com.loremind.infrastructure.persistence.converter.StringListMapJsonConverter;
import com.loremind.infrastructure.persistence.converter.StringMapJsonConverter;
import com.loremind.infrastructure.persistence.converter.StringMapMapJsonConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Entité JPA des fiches d'ennemis (bestiaire). Mêmes règles que NpcJpaEntity.
 */
@Entity
@Table(name = "enemies", indexes = {
        @Index(name = "idx_enemies_campaign_id", columnList = "campaign_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnemyJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** Niveau / FP — texte libre. Nullable. */
    @Column(name = "level")
    private String level;

    /** Dossier de classement (« Démons », « Humanoïdes »…). Nullable = non classé. */
    @Column(name = "folder")
    private String folder;

    @Column(name = "portrait_image_id")
    private String portraitImageId;

    @Column(name = "header_image_id")
    private String headerImageId;

    @Convert(converter = StringMapJsonConverter.class)
    @Column(name = "field_values", columnDefinition = "TEXT")
    private Map<String, String> values;

    @Convert(converter = StringListMapJsonConverter.class)
    @Column(name = "image_values", columnDefinition = "TEXT")
    private Map<String, List<String>> imageValues;

    @Convert(converter = StringMapMapJsonConverter.class)
    @Column(name = "key_value_values", columnDefinition = "TEXT")
    private Map<String, Map<String, String>> keyValueValues;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    /** UUID de l'acteur de compendium Foundry d'origine (import bestiaire). Nullable. */
    @Column(name = "foundry_ref", length = 512)
    private String foundryRef;

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
        if (values == null) values = new HashMap<>();
        if (imageValues == null) imageValues = new HashMap<>();
        if (keyValueValues == null) keyValueValues = new HashMap<>();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
