package com.loremind.infrastructure.persistence.entity;

import com.loremind.domain.shared.template.TemplateField;
import com.loremind.infrastructure.persistence.converter.TemplateFieldListJsonConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entité JPA pour la persistance des GameSystems (systèmes de JDR).
 */
@Entity
@Table(name = "game_systems")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameSystemJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "rules_markdown", columnDefinition = "TEXT")
    private String rulesMarkdown;

    /** Template PJ serialise en JSON via {@link TemplateFieldListJsonConverter}. */
    @Convert(converter = TemplateFieldListJsonConverter.class)
    @Column(name = "character_template", columnDefinition = "TEXT")
    private List<TemplateField> characterTemplate;

    /** Template PNJ serialise en JSON. */
    @Convert(converter = TemplateFieldListJsonConverter.class)
    @Column(name = "npc_template", columnDefinition = "TEXT")
    private List<TemplateField> npcTemplate;

    @Column
    private String author;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (characterTemplate == null) characterTemplate = new ArrayList<>();
        if (npcTemplate == null) npcTemplate = new ArrayList<>();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
