package com.loremind.infrastructure.web.dto.files;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO de retour pour les metadonnees d'un fichier generique.
 * Ne contient PAS le binaire : celui-ci est servi via GET /api/files/{id}/content.
 */
@Data
public class StoredFileDTO {
    private String id;
    private String filename;
    private String contentType;
    private long sizeBytes;
    /** URL relative pour telecharger le binaire. */
    private String url;
    private LocalDateTime uploadedAt;
}
