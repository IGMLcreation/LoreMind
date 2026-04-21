package com.loremind.infrastructure.web.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

/**
 * Configuration CORS. Origines configurables via la propriete
 * `app.cors.allowed-origins` (liste separee par virgules) ou l'env var
 * APP_CORS_ALLOWED_ORIGINS. Defaut : Angular dev server + port Docker par defaut.
 */
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:http://localhost:4200,http://localhost:8081}")
    private String allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(config::addAllowedOrigin);
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setAllowCredentials(true);

        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
