package com.loremind.infrastructure.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration Spring pour le client MinIO (S3-compatible).
 * <p>
 * Expose un bean MinioClient singleton injecte dans MinioImageStorageAdapter.
 * S'assure au demarrage que le bucket configure existe (filet de securite :
 * normalement docker-compose/minio-init l'a deja cree).
 * <p>
 * Desactive en mode local-first ({@code storage.backend=filesystem}) : aucun
 * client MinIO n'est alors instancie, donc aucune tentative de connexion au
 * boot. Defaut = actif (propriete absente ou {@code minio}).
 */
@Configuration
@ConditionalOnProperty(name = "storage.backend", havingValue = "minio", matchIfMissing = true)
public class MinioConfig {

    private static final Logger log = LoggerFactory.getLogger(MinioConfig.class);

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
        return buildClient();
    }

    /** Fabrique directe (sans proxy Spring) — voir ensureBucketExists. */
    private MinioClient buildClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    /**
     * Garantit l'existence du bucket au demarrage. Si MinIO n'est pas joignable,
     * on loggue juste l'erreur sans planter l'application : le developpeur
     * recevra une erreur claire au premier upload plutot qu'au boot.
     * <p>
     * NB : on construit un client LOCAL au lieu d'appeler {@code minioClient()} —
     * depuis Spring 6.2, appeler une methode @Bean proxifiee pendant le
     * @PostConstruct de sa propre @Configuration leve "Requested bean is
     * currently in creation" et la verification ne tournait plus jamais.
     */
    @PostConstruct
    public void ensureBucketExists() {
        try {
            MinioClient client = buildClient();
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("[MinIO] Bucket '{}' cree.", bucket);
            }
        } catch (Exception e) {
            log.warn("[MinIO] Initialisation impossible (endpoint={}). Les uploads d'images "
                    + "echoueront tant que MinIO n'est pas joignable. Cause : {}", endpoint, e.getMessage());
        }
    }
}
