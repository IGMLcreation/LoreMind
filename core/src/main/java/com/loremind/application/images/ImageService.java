package com.loremind.application.images;

import com.loremind.domain.images.Image;
import com.loremind.domain.images.ports.ImageRepository;
import com.loremind.domain.images.ports.ImageStorage;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service d'application pour le Shared Kernel images.
 * <p>
 * Orchestre l'upload / download / delete en combinant les deux ports du
 * domaine : ImageStorage (binaire) et ImageRepository (metadonnees).
 * <p>
 * Couche Application de l'Architecture Hexagonale : pas de JPA, pas de HTTP,
 * pas de MinIO ici. Juste de la logique metier pure.
 */
@Service
public class ImageService {

    /** MIME types autorises a l'upload. Evite les fichiers piegeux deguises en image. */
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    /** Taille max coherente avec la config Spring (application.properties). */
    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024; // 10 Mo

    private final ImageRepository imageRepository;
    private final ImageStorage imageStorage;

    public ImageService(ImageRepository imageRepository, ImageStorage imageStorage) {
        this.imageRepository = imageRepository;
        this.imageStorage = imageStorage;
    }

    /**
     * Use case upload : valide -> envoie le binaire -> persiste les metadonnees.
     * <p>
     * En cas d'echec de persistance DB apres un upload MinIO reussi, on tente
     * une compensation (suppression du binaire orphelin) pour eviter de
     * laisser trainer un fichier sans reference.
     */
    public Image upload(String filename, String contentType, InputStream data, long sizeBytes) {
        validateUpload(filename, contentType, sizeBytes);

        String storageKey = imageStorage.upload(filename, contentType, data, sizeBytes);

        try {
            Image image = Image.builder()
                    .filename(filename)
                    .contentType(contentType)
                    .sizeBytes(sizeBytes)
                    .storageKey(storageKey)
                    .uploadedAt(LocalDateTime.now())
                    .build();
            return imageRepository.save(image);
        } catch (RuntimeException ex) {
            // Compensation : on evite le binaire orphelin en MinIO si la DB a plante.
            imageStorage.delete(storageKey);
            throw ex;
        }
    }

    public Optional<Image> getById(String id) {
        return imageRepository.findById(id);
    }

    /**
     * Recupere le flux binaire d'une image via son ID metier.
     * Utilise par le controller pour servir GET /api/images/:id.
     */
    public Optional<InputStream> downloadById(String id) {
        return imageRepository.findById(id)
                .map(img -> imageStorage.download(img.getStorageKey()));
    }

    /** Suppression symetrique : binaire d'abord, metadonnees ensuite. */
    public void deleteById(String id) {
        imageRepository.findById(id).ifPresent(img -> {
            imageStorage.delete(img.getStorageKey());
            imageRepository.deleteById(id);
        });
    }

    // --- Validation --------------------------------------------------------

    private void validateUpload(String filename, String contentType, long sizeBytes) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Le nom du fichier est requis.");
        }
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException(
                    "Type de fichier non supporte. Types acceptes : " + List.copyOf(ALLOWED_MIME_TYPES));
        }
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("Le fichier est vide.");
        }
        if (sizeBytes > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "Fichier trop volumineux (max " + (MAX_SIZE_BYTES / 1024 / 1024) + " Mo).");
        }
    }
}
