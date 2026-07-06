package com.loremind.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "notebook_messages", indexes = {
        @Index(name = "idx_notebook_messages_notebook_id", columnList = "notebook_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotebookMessageJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notebook_id", nullable = false)
    private Long notebookId;

    @Column(nullable = false, length = 16)
    private String role;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Null = message de la conversation ACTIVE. Non-null = message archivé lors
     * d'un « vider la conversation » ; tous les messages d'un même clear portent
     * le même horodatage, qui sert d'identifiant de lot d'archive.
     */
    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now(ZoneId.systemDefault());
    }
}
