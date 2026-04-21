package com.loremind.infrastructure.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration Spring pour le client MinIO (S3-compatible).
 * <p>
 * Expose un bean MinioClient singleton injecte dans MinioImageStorageAdapter.
 * S'assure au demarrage que le bucket configure existe (filet de securite :
 * normalement docker-compose/minio-init l'a deja cree).
 */
@Configuration
public class MinioConfig {

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Value("${minio.bucket}")
    private String bucket;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    /**
     * Garantit l'existence du bucket au demarrage. Si MinIO n'est pas joignable,
     * on loggue juste l'erreur sans planter l'application : le developpeur
     * recevra une erreur claire au premier upload plutot qu'au boot.
     */
    @PostConstruct
    public void ensureBucketExists() {
        try {
            MinioClient client = minioClient();
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                System.out.println("[MinIO] Bucket '" + bucket + "' cree.");
            }
        } catch (Exception e) {
            System.err.println("[MinIO] Initialisation impossible (endpoint=" + endpoint
                    + "). Les uploads d'images echoueront tant que MinIO n'est pas joignable. "
                    + "Cause : " + e.getMessage());
        }
    }
}
