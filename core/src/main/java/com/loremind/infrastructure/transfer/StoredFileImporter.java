package com.loremind.infrastructure.transfer;

import com.loremind.domain.files.ports.FileStorage;
import com.loremind.infrastructure.persistence.entity.StoredFileJpaEntity;
import com.loremind.infrastructure.persistence.jpa.StoredFileJpaRepository;
import com.loremind.infrastructure.transfer.dto.ContentExport;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

/**
 * Reecriture des fichiers generiques (battlemaps) lors d'un import (cf. {@link ImportService}).
 * <p>
 * Pendant de {@link ImageImporter} pour la table {@code stored_files}. Les binaires
 * sont stockes sous LEUR CLE D'ORIGINE ; un fichier dont la cle existe deja est
 * REUTILISE (pas de reupload). L'id de ligne est REMAPPE dans
 * {@link ImportIdMaps#storedFileMap} pour que les battlemaps des scenes importees
 * pointent le bon fichier sur la machine cible.
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
     * Reecrit les binaires de fichiers (cle preservee) + metadonnees, et remplit la map de
     * remapping des ids de fichiers. Itere sur les METADONNEES (source de l'ancien id),
     * pas sur les binaires (meme logique que {@link ImageImporter}).
     *
     * @param export       contenu importe (source des metadonnees, dont l'ancien id)
     * @param fileBinaries {@code storageKey -> binaire} lus depuis le zip (prefixe files/)
     * @param maps         etat de remapping : {@code storedFileMap} est alimentee ici
     * @param result       compteurs a incrementer
     */
    void importFiles(ContentExport export,
                     Map<String, byte[]> fileBinaries,
                     ImportIdMaps maps,
                     ImportResult.Builder result) {
        int imported = 0;
        for (ContentExport.StoredFileDto meta : nullSafe(export.storedFiles())) {
            if (importOne(meta, fileBinaries, maps)) {
                imported++;
            }
        }
        result.count("storedFiles", imported);
    }

    /**
     * Traite une metadonnee de fichier. Reutilise la ligne existante si la cle est deja
     * connue, sinon materialise le binaire et cree la ligne.
     *
     * @return {@code true} si un nouveau binaire a ete stocke + une ligne creee (a compter
     *         dans les imports), {@code false} sinon (cle vide, reutilisation, binaire absent)
     */
    private boolean importOne(ContentExport.StoredFileDto meta,
                              Map<String, byte[]> fileBinaries,
                              ImportIdMaps maps) {
        String storageKey = meta.storageKey();
        if (storageKey == null || storageKey.isBlank()) {
            return false;
        }

        var existing = fileRepo.findByStorageKey(storageKey);
        if (existing.isPresent()) {
            // Deja present : reutilise (pas de reupload). L'ancien id pointe la ligne existante.
            mapId(maps, meta.id(), existing.get().getId());
            return false;
        }

        byte[] data = fileBinaries.get(storageKey);
        if (data == null) {
            return false; // metadonnee sans binaire : rien a materialiser
        }

        String contentType = meta.contentType() != null
                ? meta.contentType() : "application/octet-stream";
        long size = meta.sizeBytes() > 0 ? meta.sizeBytes() : data.length;

        fileStorage.store(storageKey, contentType, new ByteArrayInputStream(data), data.length);

        StoredFileJpaEntity e = new StoredFileJpaEntity();
        e.setFilename(meta.filename() != null ? meta.filename() : fileNameOf(storageKey));
        e.setContentType(contentType);
        e.setSizeBytes(size);
        e.setStorageKey(storageKey);
        mapId(maps, meta.id(), fileRepo.save(e).getId());
        return true;
    }

    private static void mapId(ImportIdMaps maps, Long oldId, Long newId) {
        if (oldId != null && newId != null) maps.storedFileMap.put(oldId, newId);
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list != null ? list : List.of();
    }

    private static String fileNameOf(String storageKey) {
        int slash = storageKey.lastIndexOf('/');
        return slash >= 0 ? storageKey.substring(slash + 1) : storageKey;
    }
}
