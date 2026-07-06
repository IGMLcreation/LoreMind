package com.loremind.infrastructure.storage;

import com.loremind.domain.images.ports.ImageStorage;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.UUID;

/**
 * Adaptateur d'infrastructure : implemente le port ImageStorage en utilisant
 * MinIO (compatible S3) comme backend de stockage d'objets.
 * <p>
 * Le domaine ne sait rien de MinIO : il manipule juste des cles opaques.
 * <p>
 * Backend par defaut ({@code storage.backend=minio} ou propriete absente).
 * Le mode local-first le remplace par {@link FilesystemImageStorageAdapter}
 * via {@code storage.backend=filesystem}.
 */
@Component
@ConditionalOnProperty(name = "storage.backend", havingValue = "minio", matchIfMissing = true)
public class MinioImageStorageAdapter implements ImageStorage {

    private static final Logger log = LoggerFactory.getLogger(MinioImageStorageAdapter.class);

    private final MinioClient minioClient;
    private final String bucket;

    public MinioImageStorageAdapter(MinioClient minioClient,
                                    @Value("${minio.bucket}") String bucket) {
        this.minioClient = minioClient;
        this.bucket = bucket;
    }

    @Override
    public String upload(String filename, String contentType, InputStream data, long sizeBytes) {
        String storageKey = generateStorageKey(filename);
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(storageKey)
                            .stream(data, sizeBytes, -1)
                            .contentType(contentType)
                            .build()
            );
            return storageKey;
        } catch (Exception e) {
            throw new RuntimeException("Echec de l'upload de l'image vers MinIO : " + e.getMessage(), e);
        }
    }

    @Override
    public void store(String storageKey, String contentType, InputStream data, long sizeBytes) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(storageKey)
                            .stream(data, sizeBytes, -1)
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Echec du store MinIO (cle imposee) : " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream download(String storageKey) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder().bucket(bucket).object(storageKey).build()
            );
        } catch (ErrorResponseException e) {
            // Objet inexistant (cle orpheline) : on retourne null plutot que de propager.
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return null;
            }
            throw new RuntimeException("Echec du download MinIO : " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Echec du download MinIO : " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket(bucket).object(storageKey).build()
            );
        } catch (Exception e) {
            // Suppression idempotente : on loggue mais on ne propage pas.
            log.warn("[MinIO] Erreur suppression (non bloquante) : {}", e.getMessage());
        }
    }

    /**
     * Genere une cle unique tout en gardant l'extension d'origine (utile pour
     * le Content-Disposition et les outils comme Foundry qui s'en servent).
     */
    private String generateStorageKey(String originalFilename) {
        String ext = extractExtension(originalFilename);
        return "images/" + UUID.randomUUID() + ext;
    }

    private String extractExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "";
        String ext = filename.substring(dot).toLowerCase();
        // On n'accepte que les extensions connues pour eviter les injections de path.
        return ext.matches("\\.(jpg|jpeg|png|webp|gif)") ? ext : "";
    }
}
