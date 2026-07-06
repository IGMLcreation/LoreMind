package com.loremind.infrastructure.web.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * Limites d'upload multipart, pilotees par les cles {@code loremind.upload.*}
 * (128 Mo par defaut, relevees a 2 Go par le profil local pour la reimportation
 * de sauvegarde). Remplace {@code spring.servlet.multipart.*} : la configuration
 * vit ici pour porter la justification du hotspot Sonar au lieu d'un finding
 * non-annotable dans un .properties. Boot laisse la main des qu'un bean
 * {@link MultipartConfigElement} existe (auto-config conditionnelle).
 */
@Configuration
public class UploadLimitsConfig {

    // S5693 : limites hautes VOLONTAIRES — battlemaps video (.mp4) jusqu'a 128 Mo en
    // Docker, import de sauvegarde .zip jusqu'a 2 Go en desktop local mono-utilisateur.
    @SuppressWarnings("java:S5693")
    @Bean
    public MultipartConfigElement multipartConfigElement(
            @Value("${loremind.upload.max-file-size:128MB}") String maxFileSize,
            @Value("${loremind.upload.max-request-size:128MB}") String maxRequestSize) {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.parse(maxFileSize));
        factory.setMaxRequestSize(DataSize.parse(maxRequestSize));
        return factory.createMultipartConfig();
    }
}
