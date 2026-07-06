package com.loremind.infrastructure.storage;

import com.loremind.domain.files.ports.FileStorage;
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
 * Adaptateur MinIO pour le port {@link FileStorage} (fichiers generiques).
 * <p>
 * Pendant de {@link MinioImageStorageAdapter} : reutilise le meme bucket, mais
 * prefixe de cle {@code files/} pour coexister sans collision avec les images.
 * Backend par defaut ({@code storage.backend=minio} ou propriete absente).
 */
@Component
@ConditionalOnProperty(name = "storage.backend", havingValue = "minio", matchIfMissing = true)
public class MinioFileStorageAdapter implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(MinioFileStorageAdapter.class);

    private final MinioClient minioClient;
    private final String bucket;

    public MinioFileStorageAdapter(MinioClient minioClient,
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
                            .contentType(contentType != null ? contentType : "application/octet-stream")
                            .build()
            );
            return storageKey;
        } catch (Exception e) {
            throw new RuntimeException("Echec de l'upload du fichier vers MinIO : " + e.getMessage(), e);
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
                            .contentType(contentType != null ? contentType : "application/octet-stream")
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
            log.warn("[MinIO] Erreur suppression fichier (non bloquante) : {}", e.getMessage());
        }
    }

    private String generateStorageKey(String originalFilename) {
        return "files/" + UUID.randomUUID() + extractExtension(originalFilename);
    }

    private String extractExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "";
        String ext = filename.substring(dot).toLowerCase();
        return ext.matches("\\.(jpg|jpeg|png|webp|gif|mp4|webm|json|dd2vtt|uvtt)") ? ext : "";
    }
}
