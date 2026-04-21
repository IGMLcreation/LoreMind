package com.loremind.domain.images;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Entite de domaine representant une image uploadee par l'utilisateur.
 * <p>
 * Shared Kernel : cette entite vit dans un package transverse (ni LoreContext
 * ni CampaignContext) car une image peut etre referencee par n'importe quelle
 * entite de ces deux contextes (Page, Scene, Chapter, Arc). Elle n'appartient
 * a aucun context en particulier.
 * <p>
 * Design :
 *  - Metadata en DB relationnelle (Postgres)
 *  - Binaire sur object storage (MinIO/S3) referencE par `storageKey`
 *  - Le domaine ne connait pas MinIO : il manipule juste une cle opaque.
 * <p>
 * Architecture Hexagonale : entite pure, aucune dependance technique.
 */
@Data
@Builder
public class Image {

    /** Identifiant stable (String pour rester agnostique vis-a-vis du stockage). */
    private String id;

    /** Nom original du fichier uploade (ex: "portrait-elfe.jpg"). */
    private String filename;

    /** Type MIME valide (ex: "image/jpeg", "image/png", "image/webp"). */
    private String contentType;

    /** Taille en octets, utile pour quotas et affichage UI. */
    private long sizeBytes;

    /**
     * Cle opaque dans l'object storage (ex: "images/abc123.jpg").
     * Le domaine ne fait qu'acheminer cette cle ; seul l'adaptateur MinIO sait
     * comment la transformer en bucket + path pour recuperer le binaire.
     */
    private String storageKey;

    /** Horodatage de l'upload initial (l'image est immuable apres creation). */
    private LocalDateTime uploadedAt;
}
