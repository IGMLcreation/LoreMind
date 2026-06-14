package com.loremind.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entité JPA pour les flags narratifs d'un Playthrough.
 *
 * <p>Remplace l'ancienne {@code CampaignFlagJpaEntity} : les flags suivent
 * désormais la partie (table jouée), pas le scénario.</p>
 */
@Entity
@Table(
        name = "playthrough_flag",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_playthrough_flag_name",
                columnNames = {"playthrough_id", "name"}
        ),
        indexes = @Index(name = "ix_playthrough_flag_playthrough", columnList = "playthrough_id")
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaythroughFlagJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "playthrough_id", nullable = false)
    private Long playthroughId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false)
    private boolean value;
}
