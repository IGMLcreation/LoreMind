package com.loremind.infrastructure.persistence.entity;

import com.loremind.domain.playcontext.ClockTrigger;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Entité JPA pour la persistance des Horloges (Clocks) en PostgreSQL.
 * Adaptateur d'infrastructure — n'est PAS dans le domaine.
 */
@Entity
@Table(name = "clocks", indexes = @Index(name = "ix_clock_playthrough", columnList = "playthrough_id"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClockJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "playthrough_id", nullable = false)
    private Long playthroughId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private int segments;

    @Column(nullable = false)
    private int filled;

    @Column(name = "\"order\"", nullable = false)
    private int order;

    /** Déclencheur d'avancement auto (co-MJ). Défaut NONE ; colonne dotée d'un default SQL (V16). */
    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", length = 32)
    @Builder.Default
    private ClockTrigger triggerType = ClockTrigger.NONE;

    @Column(name = "trigger_ref")
    private String triggerRef;

    /** Front (menace) auquel l'horloge appartient (null = libre). Weak reference, pas de FK. */
    @Column(name = "front_id")
    private Long frontId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now(ZoneId.systemDefault());
    }
}
