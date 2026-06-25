package com.loremind.domain.files.ports;

import com.loremind.domain.files.StoredFile;

import java.util.Optional;

/**
 * Port de sortie pour la persistance des metadonnees de fichiers generiques.
 * <p>
 * Pendant de {@link com.loremind.domain.images.ports.ImageRepository}. Ne
 * manipule QUE les metadonnees ; le binaire est gere par {@link FileStorage}.
 */
public interface StoredFileRepository {

    StoredFile save(StoredFile file);

    Optional<StoredFile> findById(String id);

    Optional<StoredFile> findByStorageKey(String storageKey);

    void deleteById(String id);

    boolean existsById(String id);
}
