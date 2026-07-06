package com.loremind.infrastructure.transfer;

import com.loremind.domain.images.ports.ImageStorage;
import com.loremind.infrastructure.persistence.entity.ImageJpaEntity;
import com.loremind.infrastructure.persistence.jpa.ImageJpaRepository;
import com.loremind.infrastructure.transfer.dto.ContentExport;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

/**
 * Réécriture des images lors d'un import (cf. {@link ImportService}).
 * <p>
 * Les binaires sont stockés sous LEUR CLÉ D'ORIGINE (pas de remapping de clé) :
 * une image dont la clé existe déjà est RÉUTILISÉE (pas de réupload), pour éviter
 * les doublons quand on agrège plusieurs exports dans la même base.
 * <p>
 * En revanche l'ID de ligne {@code images} est REMAPPÉ (comme tout le reste en mode
 * fusion) : on alimente {@link ImportIdMaps#imageMap} {@code ancienId → nouvelId} pour
 * que les entités importées (portraits, illustrations, galeries, plans de salle)
 * pointent la bonne image sur la machine cible. Sans ce remap, un export repris sur
 * une autre base montre des images absentes ou mélangées.
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
     * Réécrit les binaires d'images (clé préservée) + métadonnées, et remplit la map de
     * remapping des ids d'images.
     * <p>
     * On itère sur les MÉTADONNÉES ({@code export.images()}) et non sur les binaires, car
     * c'est là que vit l'{@code ancien id} nécessaire au remapping. Une image déjà présente
     * (même clé) est réutilisée : son id EXISTANT devient la cible du remap.
     *
     * @param export        contenu importé (source des métadonnées, dont l'ancien id)
     * @param imageBinaries {@code storageKey → binaire} lus depuis le zip
     * @param maps          état de remapping : {@code imageMap} est alimentée ici
     * @param result        compteurs d'images (uploadées / réutilisées) à incrémenter
     */
    void importImages(ContentExport export,
                      Map<String, byte[]> imageBinaries,
                      ImportIdMaps maps,
                      ImportResult.Builder result) {
        for (ContentExport.ImageDto meta : nullSafe(export.images())) {
            importImage(meta, imageBinaries, maps, result);
        }
    }

    /** Traite une métadonnée d'image : réutilise la ligne existante, sinon matérialise le binaire. */
    private void importImage(ContentExport.ImageDto meta, Map<String, byte[]> imageBinaries,
                             ImportIdMaps maps, ImportResult.Builder result) {
        String storageKey = meta.storageKey();
        if (storageKey == null || storageKey.isBlank()) return;

        var existing = imageRepo.findByStorageKey(storageKey);
        if (existing.isPresent()) {
            // Image déjà présente : réutilisée (pas de réupload). L'ancien id pointe
            // désormais la ligne existante.
            mapId(maps, meta.id(), existing.get().getId());
            result.imageReused();
            return;
        }

        byte[] data = imageBinaries.get(storageKey);
        if (data == null) {
            // Métadonnée sans binaire (ex. image orpheline non embarquée) : rien à
            // matérialiser, pas de cible de remap.
            return;
        }

        String contentType = meta.contentType() != null
                ? meta.contentType() : guessContentType(storageKey);
        long size = meta.sizeBytes() > 0 ? meta.sizeBytes() : data.length;

        imageStorage.store(storageKey, contentType, new ByteArrayInputStream(data), data.length);

        ImageJpaEntity e = new ImageJpaEntity();
        e.setFilename(meta.filename() != null ? meta.filename() : fileNameOf(storageKey));
        e.setContentType(contentType);
        e.setSizeBytes(size);
        e.setStorageKey(storageKey);
        mapId(maps, meta.id(), imageRepo.save(e).getId());
        result.imageUploaded();
    }

    private static void mapId(ImportIdMaps maps, Long oldId, Long newId) {
        if (oldId != null && newId != null) maps.imageMap.put(oldId, newId);
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
