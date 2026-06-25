package com.loremind.infrastructure.transfer;

import com.loremind.domain.files.ports.FileStorage;
import com.loremind.infrastructure.persistence.entity.StoredFileJpaEntity;
import com.loremind.infrastructure.persistence.jpa.StoredFileJpaRepository;
import com.loremind.infrastructure.transfer.dto.ContentExport;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reecriture des fichiers generiques (battlemaps) lors d'un import (cf. {@link ImportService}).
 * <p>
 * Pendant de {@link ImageImporter} pour la table {@code stored_files}. Les binaires
 * sont stockes sous LEUR CLE D'ORIGINE ; un fichier dont la cle existe deja est
 * REUTILISE (pas de reupload).
 */
@Component
class StoredFileImporter {

    private final StoredFileJpaRepository fileRepo;
    private final FileStorage fileStorage;

    StoredFileImporter(StoredFileJpaRepository fileRepo, FileStorage fileStorage) {
        this.fileRepo = fileRepo;
        this.fileStorage = fileStorage;
    }

    /**
     * Reecrit les binaires de fichiers (cle preservee) et leurs metadonnees.
     *
     * @param export       contenu importe (source des metadonnees par cle)
     * @param fileBinaries {@code storageKey -> binaire} lus depuis le zip (prefixe files/)
     * @param result       compteurs a incrementer
     */
    void importFiles(ContentExport export,
                     Map<String, byte[]> fileBinaries,
                     ImportResult.Builder result) {
        Map<String, ContentExport.StoredFileDto> metaByKey = new HashMap<>();
        for (ContentExport.StoredFileDto f : nullSafe(export.storedFiles())) {
            if (f.storageKey() != null) metaByKey.put(f.storageKey(), f);
        }

        int imported = 0;
        for (Map.Entry<String, byte[]> bin : fileBinaries.entrySet()) {
            String storageKey = bin.getKey();
            byte[] data = bin.getValue();
            if (fileRepo.findByStorageKey(storageKey).isPresent()) {
                continue; // deja present : reutilise, pas de reupload
            }
            ContentExport.StoredFileDto meta = metaByKey.get(storageKey);
            String contentType = meta != null && meta.contentType() != null
                    ? meta.contentType() : "application/octet-stream";
            long size = meta != null ? meta.sizeBytes() : data.length;

            fileStorage.store(storageKey, contentType, new ByteArrayInputStream(data), data.length);

            StoredFileJpaEntity e = new StoredFileJpaEntity();
            e.setFilename(meta != null && meta.filename() != null
                    ? meta.filename() : fileNameOf(storageKey));
            e.setContentType(contentType);
            e.setSizeBytes(size);
            e.setStorageKey(storageKey);
            fileRepo.save(e);
            imported++;
        }
        result.count("storedFiles", imported);
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list != null ? list : List.of();
    }

    private static String fileNameOf(String storageKey) {
        int slash = storageKey.lastIndexOf('/');
        return slash >= 0 ? storageKey.substring(slash + 1) : storageKey;
    }
}
