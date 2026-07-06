package com.loremind.infrastructure.persistence.entity;

import com.loremind.domain.campaigncontext.quest.Prerequisite;
import com.loremind.domain.campaigncontext.quest.QuestNodeRef;
import com.loremind.infrastructure.persistence.converter.PrerequisiteListJsonConverter;
import com.loremind.infrastructure.persistence.converter.QuestNodeListJsonConverter;
import com.loremind.infrastructure.persistence.converter.StringListJsonConverter;
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
 * Entité JPA pour la persistance des Quests (table {@code quests}).
 *
 * <p>Calquée sur {@code ChapterJpaEntity} : rattachement campagne ({@code campaign_id})
 * au lieu d'arc, + colonne {@code nodes} (liens vers chapitres/scènes) sérialisée
 * en JSON. Le converter {@link PrerequisiteListJsonConverter} est RÉUTILISÉ tel
 * quel pour les prérequis (format on-disk identique à celui des chapitres).</p>
 */
@Entity
@Table(name = "quests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    /** Arc de rattachement (nullable, weak ref — pas de FK, agrégat Quest indépendant). */
    @Column(name = "arc_id")
    private Long arcId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column
    private String icon;

    @Column(name = "\"order\"", nullable = false)
    private int order;

    @Column(name = "prerequisites", columnDefinition = "TEXT")
    @Convert(converter = PrerequisiteListJsonConverter.class)
    @Builder.Default
    private List<Prerequisite> prerequisites = new ArrayList<>();

    @Column(name = "nodes", columnDefinition = "TEXT")
    @Convert(converter = QuestNodeListJsonConverter.class)
    @Builder.Default
    private List<QuestNodeRef> nodes = new ArrayList<>();

    @Column(name = "gm_notes", columnDefinition = "TEXT")
    private String gmNotes;

    @Column(name = "player_objectives", columnDefinition = "TEXT")
    private String playerObjectives;

    @Column(name = "narrative_stakes", columnDefinition = "TEXT")
    private String narrativeStakes;

    @Column(name = "related_page_ids", columnDefinition = "TEXT")
    @Convert(converter = StringListJsonConverter.class)
    @Builder.Default
    private List<String> relatedPageIds = new ArrayList<>();

    @Column(name = "illustration_image_ids", columnDefinition = "TEXT")
    @Convert(converter = StringListJsonConverter.class)
    @Builder.Default
    private List<String> illustrationImageIds = new ArrayList<>();

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
