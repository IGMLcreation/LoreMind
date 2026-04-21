package com.loremind.domain.images.ports;

import java.io.InputStream;

/**
 * Port de sortie pour le stockage du BINAIRE des images.
 * <p>
 * Separe de ImageRepository (metadonnees) pour respecter le SRP :
 *  - ImageRepository --> Postgres (metadonnees)
 *  - ImageStorage    --> MinIO/S3 (fichiers binaires)
 * <p>
 * Le domaine raisonne en termes de "cle opaque" (storageKey).
 * Chaque implementation (MinIO, filesystem, S3...) traduit cette cle selon
 * sa propre logique physique.
 */
public interface ImageStorage {

    /**
     * Envoie un flux binaire et retourne la cle generee.
     *
     * @param filename    nom d'origine (utilise pour extraire l'extension)
     * @param contentType MIME type valide
     * @param data        flux binaire a stocker
     * @param sizeBytes   taille en octets (requis par certains backends comme S3)
     * @return cle opaque utilisable ensuite pour retrouver le binaire
     */
    String upload(String filename, String contentType, InputStream data, long sizeBytes);

    /** Recupere le flux binaire associe a une cle, ou null si inexistante. */
    InputStream download(String storageKey);

    /** Supprime le binaire. No-op silencieux si la cle n'existe pas. */
    void delete(String storageKey);
}
