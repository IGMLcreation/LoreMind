package com.loremind.infrastructure.persistence.entity;

import com.loremind.domain.lorecontext.TemplateField;
import com.loremind.infrastructure.persistence.converter.TemplateFieldListJsonConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Entité JPA pour la persistance des Templates en PostgreSQL.
 * - loreId et defaultNodeId : colonnes typées (FK logiques, pas de @ManyToOne
 *   pour respecter l'isolation des Bounded Contexts).
 * - fields : stocké en JSON (TEXT) via TemplateFieldListJsonConverter.
 *   Les anciens templates (format legacy ["a","b"]) sont lus de maniere tolerante.
 */
@Entity
@Table(name = "templates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lore_id", nullable = false)
    private Long loreId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "default_node_id")
    private Long defaultNodeId;

    @Column(name = "fields", columnDefinition = "TEXT")
    @Convert(converter = TemplateFieldListJsonConverter.class)
    private List<TemplateField> fields;

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
