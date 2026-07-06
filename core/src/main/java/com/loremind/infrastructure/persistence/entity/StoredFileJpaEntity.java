package com.loremind.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Entite JPA pour les metadonnees de fichiers generiques en PostgreSQL.
 * Le binaire est stocke cote MinIO/filesystem (reference par storage_key).
 * Pendant de {@link ImageJpaEntity} pour les fichiers non-images.
 */
@Entity
@Table(name = "stored_files")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoredFileJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String filename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** Cle opaque dans le stockage, unique. */
    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        if (uploadedAt == null) {
            uploadedAt = LocalDateTime.now(ZoneId.systemDefault());
        }
    }
}
