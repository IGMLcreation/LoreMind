package com.loremind.application.files;

import com.loremind.domain.files.StoredFile;
import com.loremind.domain.files.ports.FileStorage;
import com.loremind.domain.files.ports.StoredFileRepository;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;

/**
 * Service d'application pour les fichiers generiques (port {@link FileStorage}
 * + {@link StoredFileRepository}). Pendant de
 * {@link com.loremind.application.images.ImageService}, mais SANS la contrainte
 * "image" : accepte aussi video et sidecars JSON (battlemaps Universal VTT).
 * <p>
 * Validation MIME volontairement permissive : l'appli est mono-utilisateur
 * (bureau / self-hosted), le risque "upload piege" est faible, et les outils de
 * cartes (Dungeon Alchemist...) servent parfois le sidecar {@code .dd2vtt} sans
 * type MIME fiable.
 */
@Service
public class StoredFileService {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    /** Types MIME explicitement autorises (en plus de tout {@code image/*} et {@code video/*}). */
    private static final Set<String> ALLOWED_EXACT_MIME = Set.of(
            "application/json",
            DEFAULT_CONTENT_TYPE,
            "text/plain"
    );

    /** Coherent avec spring.servlet.multipart.max-file-size (application.properties). */
    private static final long MAX_SIZE_BYTES = 128L * 1024 * 1024; // 128 Mo

    private final StoredFileRepository repository;
    private final FileStorage storage;

    public StoredFileService(StoredFileRepository repository, FileStorage storage) {
        this.repository = repository;
        this.storage = storage;
    }

    /**
     * Use case upload : valide -> envoie le binaire -> persiste les metadonnees.
     * En cas d'echec DB apres ecriture du binaire, compense (supprime l'orphelin).
     */
    public StoredFile upload(String filename, String contentType, InputStream data, long sizeBytes) {
        String resolvedType = resolveContentType(contentType);
        validateUpload(filename, resolvedType, sizeBytes);

        String storageKey = storage.upload(filename, resolvedType, data, sizeBytes);

        try {
            StoredFile file = StoredFile.builder()
                    .filename(filename)
                    .contentType(resolvedType)
                    .sizeBytes(sizeBytes)
                    .storageKey(storageKey)
                    .uploadedAt(LocalDateTime.now(ZoneId.systemDefault()))
                    .build();
            return repository.save(file);
        } catch (RuntimeException ex) {
            storage.delete(storageKey);
            throw ex;
        }
    }

    public Optional<StoredFile> getById(String id) {
        return repository.findById(id);
    }

    public Optional<InputStream> downloadById(String id) {
        return repository.findById(id)
                .map(f -> storage.download(f.getStorageKey()));
    }

    public void deleteById(String id) {
        repository.findById(id).ifPresent(f -> {
            storage.delete(f.getStorageKey());
            repository.deleteById(id);
        });
    }

    // --- Validation --------------------------------------------------------

    private String resolveContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return DEFAULT_CONTENT_TYPE;
        }
        return contentType.toLowerCase();
    }

    private void validateUpload(String filename, String contentType, long sizeBytes) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Le nom du fichier est requis.");
        }
        if (!isAllowedMime(contentType)) {
            throw new IllegalArgumentException("Type de fichier non supporte : " + contentType);
        }
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("Le fichier est vide.");
        }
        if (sizeBytes > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "Fichier trop volumineux (max " + (MAX_SIZE_BYTES / 1024 / 1024) + " Mo).");
        }
    }

    private boolean isAllowedMime(String contentType) {
        return contentType.startsWith("image/")
                || contentType.startsWith("video/")
                || ALLOWED_EXACT_MIME.contains(contentType);
    }
}
