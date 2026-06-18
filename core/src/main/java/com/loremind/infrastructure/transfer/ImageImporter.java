package com.loremind.infrastructure.transfer;

import com.loremind.domain.images.ports.ImageStorage;
import com.loremind.infrastructure.persistence.entity.ImageJpaEntity;
import com.loremind.infrastructure.persistence.jpa.ImageJpaRepository;
import com.loremind.infrastructure.transfer.dto.ContentExport;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Réécriture des images lors d'un import (cf. {@link ImportService}).
 * <p>
 * Les binaires sont stockés sous LEUR CLÉ D'ORIGINE (pas de remapping de clé) :
 * une image dont la clé existe déjà est RÉUTILISÉE (pas de réupload), pour éviter
 * les doublons quand on agrège plusieurs exports dans la même base.
 */
@Component
class ImageImporter {

    private final ImageJpaRepository imageRepo;
    private final ImageStorage imageStorage;

    ImageImporter(ImageJpaRepository imageRepo, ImageStorage imageStorage) {
        this.imageRepo = imageRepo;
        this.imageStorage = imageStorage;
    }

    /**
     * Réécrit les binaires d'images (clé préservée) et leurs métadonnées.
     *
     * @param export        contenu importé (source des métadonnées par clé)
     * @param imageBinaries  {@code storageKey → binaire} lus depuis le zip
     * @param result         compteurs d'images (uploadées / réutilisées) à incrémenter
     */
    void importImages(ContentExport export,
                      Map<String, byte[]> imageBinaries,
                      ImportResult.Builder result) {
        // Index des métadonnées d'image par clé (depuis le data.json).
        Map<String, ContentExport.ImageDto> metaByKey = new HashMap<>();
        for (ContentExport.ImageDto img : nullSafe(export.images())) {
            if (img.storageKey() != null) metaByKey.put(img.storageKey(), img);
        }

        for (Map.Entry<String, byte[]> bin : imageBinaries.entrySet()) {
            String storageKey = bin.getKey();
            byte[] data = bin.getValue();
            if (imageRepo.findByStorageKey(storageKey).isPresent()) {
                // Image déjà présente : on réutilise, pas de réupload (éviter doublon).
                result.imageReused();
                continue;
            }
            ContentExport.ImageDto meta = metaByKey.get(storageKey);
            String contentType = meta != null && meta.contentType() != null
                    ? meta.contentType() : guessContentType(storageKey);
            long size = meta != null ? meta.sizeBytes() : data.length;

            imageStorage.store(storageKey, contentType, new ByteArrayInputStream(data), data.length);

            ImageJpaEntity e = new ImageJpaEntity();
            e.setFilename(meta != null && meta.filename() != null
                    ? meta.filename() : fileNameOf(storageKey));
            e.setContentType(contentType);
            e.setSizeBytes(size);
            e.setStorageKey(storageKey);
            imageRepo.save(e);
            result.imageUploaded();
        }
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list != null ? list : List.of();
    }

    private static String fileNameOf(String storageKey) {
        int slash = storageKey.lastIndexOf('/');
        return slash >= 0 ? storageKey.substring(slash + 1) : storageKey;
    }

    private static String guessContentType(String storageKey) {
        String lower = storageKey.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }
}
