package com.loremind.infrastructure.persistence.entity;

import com.loremind.domain.campaigncontext.SceneBranch;
import com.loremind.infrastructure.persistence.converter.SceneBranchListJsonConverter;
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
 * Entité JPA pour la persistance des Scenes en base de données PostgreSQL.
 */
@Entity
@Table(name = "scenes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SceneJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "chapter_id", nullable = false)
    private Long chapterId;

    @Column(name = "\"order\"", nullable = false)
    private int order;

    // Champs narratifs enrichis — ajoutés automatiquement par Hibernate (ddl-auto=update)

    // Contexte et ambiance
    @Column(columnDefinition = "TEXT")
    private String location;

    @Column(columnDefinition = "TEXT")
    private String timing;

    @Column(columnDefinition = "TEXT")
    private String atmosphere;

    // Narration
    @Column(name = "player_narration", columnDefinition = "TEXT")
    private String playerNarration;

    // Secrets MJ
    @Column(name = "gm_secret_notes", columnDefinition = "TEXT")
    private String gmSecretNotes;

    // Choix et conséquences
    @Column(name = "choices_consequences", columnDefinition = "TEXT")
    private String choicesConsequences;

    // Combat
    @Column(name = "combat_difficulty", columnDefinition = "TEXT")
    private String combatDifficulty;

    @Column(columnDefinition = "TEXT")
    private String enemies;

    @Column(name = "related_page_ids", columnDefinition = "TEXT")
    @Convert(converter = StringListJsonConverter.class)
    @Builder.Default
    private List<String> relatedPageIds = new ArrayList<>();

    @Column(name = "illustration_image_ids", columnDefinition = "TEXT")
    @Convert(converter = StringListJsonConverter.class)
    @Builder.Default
    private List<String> illustrationImageIds = new ArrayList<>();

    @Column(name = "map_image_ids", columnDefinition = "TEXT")
    @Convert(converter = StringListJsonConverter.class)
    @Builder.Default
    private List<String> mapImageIds = new ArrayList<>();

    // Graphe narratif intra-chapitre : sorties possibles vers d'autres scènes.
    // Persisté en TEXT JSON via converter (pattern homogène avec les autres listes).
    @Column(name = "branches", columnDefinition = "TEXT")
    @Convert(converter = SceneBranchListJsonConverter.class)
    @Builder.Default
    private List<SceneBranch> branches = new ArrayList<>();

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
