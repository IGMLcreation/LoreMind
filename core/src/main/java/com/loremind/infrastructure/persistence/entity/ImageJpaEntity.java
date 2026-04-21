package com.loremind.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entite JPA pour les metadonnees d'images en PostgreSQL.
 * Le binaire est stocke cote MinIO (reference par storage_key).
 */
@Entity
@Table(name = "images")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String filename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** Cle opaque dans MinIO, unique. */
    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        if (uploadedAt == null) {
            uploadedAt = LocalDateTime.now();
        }
    }
}
