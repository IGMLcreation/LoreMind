package com.loremind.infrastructure.persistence.entity;

import com.loremind.domain.campaigncontext.ProgressionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entité JPA pour la progression d'une quête (Chapter) au sein d'un Playthrough.
 *
 * <p>Contrainte d'unicité sur (playthrough_id, chapter_id) : une seule ligne par
 * couple quête × partie. L'absence de ligne = NOT_STARTED.</p>
 */
@Entity
@Table(
        name = "quest_progression",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_quest_progression_unique",
                columnNames = {"playthrough_id", "chapter_id"}
        ),
        indexes = @Index(name = "ix_quest_progression_playthrough", columnList = "playthrough_id")
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestProgressionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "playthrough_id", nullable = false)
    private Long playthroughId;

    @Column(name = "chapter_id", nullable = false)
    private Long chapterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProgressionStatus status;
}
