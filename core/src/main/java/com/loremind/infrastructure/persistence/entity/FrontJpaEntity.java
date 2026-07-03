package com.loremind.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entité JPA pour la persistance des Fronts en PostgreSQL.
 * Adaptateur d'infrastructure — n'est PAS dans le domaine.
 */
@Entity
@Table(name = "fronts", indexes = @Index(name = "ix_front_playthrough", columnList = "playthrough_id"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FrontJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "playthrough_id", nullable = false)
    private Long playthroughId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "\"order\"", nullable = false)
    private int order;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
