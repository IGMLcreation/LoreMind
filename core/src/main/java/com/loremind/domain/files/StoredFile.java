package com.loremind.domain.files;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Entite de domaine representant un FICHIER generique uploade par l'utilisateur.
 * <p>
 * Pendant de {@link com.loremind.domain.images.Image}, mais SANS la contrainte
 * "image" : un StoredFile peut etre une video, un JSON, etc. Sert notamment aux
 * "battlemaps" de scene (paire media + sidecar JSON Universal VTT) destinees a
 * l'export Foundry, qui ne sont ni des images (mp4) ni limitees a 10 Mo.
 * <p>
 * Meme design que Image :
 *  - Metadata en DB relationnelle (table {@code stored_files})
 *  - Binaire sur object storage (MinIO/S3) ou filesystem, reference par {@code storageKey}
 *  - Le domaine ne connait que la cle opaque.
 * <p>
 * Architecture Hexagonale : entite pure, aucune dependance technique.
 */
@Data
@Builder
public class StoredFile {

    /** Identifiant stable (String pour rester agnostique vis-a-vis du stockage). */
    private String id;

    /** Nom original du fichier uploade (ex: "cellier.dd2vtt", "donjon.mp4"). */
    private String filename;

    /** Type MIME (ex: "video/mp4", "application/json", "image/png"). */
    private String contentType;

    /** Taille en octets. */
    private long sizeBytes;

    /** Cle opaque dans le stockage (ex: "files/abc123.mp4"). */
    private String storageKey;

    /** Horodatage de l'upload initial (le fichier est immuable apres creation). */
    private LocalDateTime uploadedAt;
}
