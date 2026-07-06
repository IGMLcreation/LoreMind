package com.loremind.infrastructure.persistence.entity;

import com.loremind.domain.campaigncontext.quest.ProgressionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entité JPA pour la progression d'une quête (Quest) au sein d'un Playthrough.
 *
 * <p>Contrainte d'unicité sur (playthrough_id, quest_id) : une seule ligne par
 * couple quête × partie. L'absence de ligne = NOT_STARTED.</p>
 */
@Entity
@Table(
        name = "quest_progression",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_quest_progression_quest",
                columnNames = {"playthrough_id", "quest_id"}
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

    @Column(name = "quest_id", nullable = false)
    private Long questId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProgressionStatus status;
}
