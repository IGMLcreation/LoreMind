package com.loremind.infrastructure.persistence.entity;

import com.loremind.infrastructure.persistence.converter.StringListMapJsonConverter;
import com.loremind.infrastructure.persistence.converter.StringMapJsonConverter;
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
 * Entité JPA pour les fiches de personnages (PJ).
 * <p>
 * Refonte 2026-04-30 : ancien champ markdownContent migré vers {@code values["Notes"]}
 * via Hibernate au demarrage. Hibernate ddl-auto=update ajoute les nouvelles colonnes
 * sans dropper {@code markdown_content} — les donnees existantes sont conservees mais
 * plus mappees au domaine. Migration manuelle prevue (script SQL one-shot) si le
 * deploiement passe en bluegreen.
 */
@Entity
@Table(name = "characters")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CharacterJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "portrait_image_id")
    private String portraitImageId;

    @Column(name = "header_image_id")
    private String headerImageId;

    /** Valeurs TEXT/NUMBER serialisees JSON. */
    @Convert(converter = StringMapJsonConverter.class)
    @Column(name = "field_values", columnDefinition = "TEXT")
    private Map<String, String> values;

    /** Valeurs IMAGE serialisees JSON. */
    @Convert(converter = StringListMapJsonConverter.class)
    @Column(name = "image_values", columnDefinition = "TEXT")
    private Map<String, List<String>> imageValues;

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
        if (values == null) values = new HashMap<>();
        if (imageValues == null) imageValues = new HashMap<>();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
