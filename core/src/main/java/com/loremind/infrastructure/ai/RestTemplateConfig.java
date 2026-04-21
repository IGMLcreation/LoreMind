package com.loremind.infrastructure.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration Spring fournissant un RestTemplate et un WebClient avec timeout
 * adapté aux appels vers le Brain (LLM local parfois lent) et ajout automatique
 * de l'entete X-Internal-Secret (auth inter-service Core <-> Brain).
 * <p>
 * Sans cette entete, le Brain refuse la requete (401) — defense contre
 * l'acces direct au Brain depuis un attaquant qui atteindrait son port.
 */
@Configuration
public class RestTemplateConfig {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    @Bean
    public RestTemplate brainRestTemplate(
            RestTemplateBuilder builder,
            @Value("${brain.timeout-seconds}") long timeoutSeconds,
            @Value("${brain.internal-secret}") String internalSecret) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(timeoutSeconds))
                .additionalInterceptors((request, body, execution) -> {
                    if (internalSecret != null && !internalSecret.isBlank()) {
                        request.getHeaders().set(INTERNAL_SECRET_HEADER, internalSecret);
                    }
                    return execution.execute(request, body);
                })
                .build();
    }

    /**
     * Ajoute X-Internal-Secret comme header par defaut a tous les WebClient
     * construits via le builder auto-configure par Spring Boot. Evite de
     * recreer un builder (qui perdrait les codecs/logging auto-configures).
     */
    @Bean
    public WebClientCustomizer internalSecretWebClientCustomizer(
            @Value("${brain.internal-secret}") String internalSecret) {
        return builder -> {
            if (internalSecret != null && !internalSecret.isBlank()) {
                builder.defaultHeader(INTERNAL_SECRET_HEADER, internalSecret);
            }
        };
    }
}
