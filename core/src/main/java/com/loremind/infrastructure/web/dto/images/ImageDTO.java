package com.loremind.infrastructure.web.dto.images;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO de retour pour les metadonnees d'une image.
 * Ne contient PAS le binaire : celui-ci est servi separement via
 * GET /api/images/{id}/content.
 */
@Data
public class ImageDTO {
    private String id;
    private String filename;
    private String contentType;
    private long sizeBytes;
    /**
     * URL relative pour telecharger le binaire.
     * Le front construit l'URL absolue en prefixant le baseUrl de l'API.
     */
    private String url;
    private LocalDateTime uploadedAt;
}
