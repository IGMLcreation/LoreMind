package com.loremind.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Entité JPA pour la persistance des Lores en base de données PostgreSQL.
 * Cette classe contient des annotations JPA, donc elle est dans infrastructure, pas dans le domaine.
 */
@Entity
@Table(name = "lores")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoreJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "node_count", nullable = false)
    private int nodeCount;

    @Column(name = "page_count", nullable = false)
    private int pageCount;

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
