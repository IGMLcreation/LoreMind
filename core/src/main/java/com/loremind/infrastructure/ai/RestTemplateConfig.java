package com.loremind.infrastructure.ai;

import com.loremind.infrastructure.web.config.UserLanguageHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration Spring fournissant un RestTemplate et un WebClient avec timeout
 * adapté aux appels vers le Brain (LLM local parfois lent) et ajout automatique
 * de l'entete X-Internal-Secret (auth inter-service Core <-> Brain).
 * <p>
 * Sans cette entete, le Brain refuse la requete (401) — defense contre
 * l'acces direct au Brain depuis un attaquant qui atteindrait son port.
 * <p>
 * Relaie aussi l'entete X-User-Language (langue choisie dans l'UI, capturee par
 * {@link com.loremind.infrastructure.web.config.UserLanguageFilter}) pour que le
 * Brain redige ses reponses IA dans la langue de l'utilisateur. Lu depuis le
 * ThreadLocal au moment de l'execution de la requete (thread servlet) — d'ou
 * l'usage d'un interceptor (et non d'un defaultHeader fige au demarrage).
 */
@Configuration
public class RestTemplateConfig {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    @Bean
    @Primary
    public RestTemplate brainRestTemplate(
            RestTemplateBuilder builder,
            @Value("${brain.timeout-seconds}") long timeoutSeconds,
            @Value("${brain.internal-secret}") String internalSecret) {
        return builder
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(timeoutSeconds))
                .additionalInterceptors((request, body, execution) -> {
                    if (internalSecret != null && !internalSecret.isBlank()) {
                        request.getHeaders().set(INTERNAL_SECRET_HEADER, internalSecret);
                    }
                    request.getHeaders().set(UserLanguageHolder.HEADER, UserLanguageHolder.get());
                    return execution.execute(request, body);
                })
                .build();
    }

    /**
     * RestTemplate dédié à l'import de PDF (règles/campagne) au timeout LONG :
     * l'extraction + la structuration map-reduce d'un livre entier enchaîne de
     * nombreux appels LLM et dépasse facilement le timeout des appels courts.
     * Même entête d'auth inter-service que {@link #brainRestTemplate}.
     */
    @Bean
    public RestTemplate brainImportRestTemplate(
            RestTemplateBuilder builder,
            @Value("${brain.import-timeout-seconds:600}") long importTimeoutSeconds,
            @Value("${brain.internal-secret}") String internalSecret) {
        return builder
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(importTimeoutSeconds))
                .additionalInterceptors((request, body, execution) -> {
                    if (internalSecret != null && !internalSecret.isBlank()) {
                        request.getHeaders().set(INTERNAL_SECRET_HEADER, internalSecret);
                    }
                    request.getHeaders().set(UserLanguageHolder.HEADER, UserLanguageHolder.get());
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
