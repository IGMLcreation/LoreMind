package com.loremind.domain.files.ports;

import java.io.InputStream;

/**
 * Port de sortie pour le stockage du BINAIRE des fichiers generiques.
 * <p>
 * Pendant de {@link com.loremind.domain.images.ports.ImageStorage}, mais pour
 * des fichiers non-images (video, JSON...). Separe du port image pour que les
 * deux familles aient des regles (MIME, taille, prefixe de cle) independantes.
 * <p>
 * Les implementations (MinIO, filesystem) traduisent la cle opaque selon leur
 * logique physique. Convention de cle : prefixe {@code files/}.
 */
public interface FileStorage {

    /**
     * Envoie un flux binaire et retourne la cle generee (prefixe {@code files/}).
     */
    String upload(String filename, String contentType, InputStream data, long sizeBytes);

    /**
     * Stocke un flux binaire SOUS UNE CLE IMPOSEE (pas de generation). Utilise
     * par l'import de contenu pour reinjecter un fichier sous sa cle d'origine.
     * Ecrase si la cle existe deja.
     */
    void store(String storageKey, String contentType, InputStream data, long sizeBytes);

    /** Recupere le flux binaire associe a une cle, ou null si inexistante. */
    InputStream download(String storageKey);

    /** Supprime le binaire. No-op silencieux si la cle n'existe pas. */
    void delete(String storageKey);
}
