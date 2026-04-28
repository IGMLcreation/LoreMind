package com.loremind.infrastructure.licensing;

import com.fasterxml.jackson.databind.JsonNode;
import com.loremind.domain.licensing.RegistryCredentials;
import com.loremind.domain.licensing.ports.LicenseRelay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Client HTTP du relais OAuth Patreon (deploye sur Cloudflare Workers).
 * Voir {@code relay/} pour le code du relais.
 */
@Component
public class HttpLicenseRelay implements LicenseRelay {

    private static final Logger log = LoggerFactory.getLogger(HttpLicenseRelay.class);

    private final RestTemplate http;
    private final String baseUrl;

    public HttpLicenseRelay(
            RestTemplateBuilder builder,
            @Value("${licensing.relay.base-url:}") String baseUrl) {
        this.http = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(15))
                .build();
        this.baseUrl = stripTrailingSlash(baseUrl);
    }

    @Override
    public String buildConnectUrl(String instanceId) {
        if (baseUrl.isBlank()) {
            throw new IllegalStateException("Licensing relay base URL not configured");
        }
        String encoded = URLEncoder.encode(instanceId, StandardCharsets.UTF_8);
        return baseUrl + "/oauth/start?instance_id=" + encoded;
    }

    @Override
    public String refreshToken(String currentJwt) throws RelayException {
        if (baseUrl.isBlank()) {
            throw new RelayException(RelayErrorKind.TRANSIENT, "relay not configured");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of("jwt", currentJwt);

        ResponseEntity<JsonNode> resp;
        try {
            resp = http.exchange(
                    baseUrl + "/token/refresh",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    JsonNode.class);
        } catch (HttpClientErrorException e) {
            throw new RelayException(RelayErrorKind.REJECTED,
                    "relay rejected refresh: " + e.getStatusCode() + " " + e.getStatusText());
        } catch (HttpServerErrorException e) {
            throw new RelayException(RelayErrorKind.TRANSIENT,
                    "relay 5xx: " + e.getStatusCode());
        } catch (RestClientException e) {
            throw new RelayException(RelayErrorKind.TRANSIENT, "relay unreachable: " + e.getMessage(), e);
        }

        JsonNode payload = resp.getBody();
        if (payload == null || !payload.hasNonNull("jwt")) {
            throw new RelayException(RelayErrorKind.BAD_RESPONSE, "missing jwt in refresh response");
        }
        return payload.get("jwt").asText();
    }

    @Override
    public RegistryCredentials fetchRegistryCredentials(String currentJwt) throws RelayException {
        if (baseUrl.isBlank()) {
            throw new RelayException(RelayErrorKind.TRANSIENT, "relay not configured");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of("jwt", currentJwt);

        ResponseEntity<JsonNode> resp;
        try {
            resp = http.exchange(
                    baseUrl + "/registry/credentials",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    JsonNode.class);
        } catch (HttpClientErrorException e) {
            throw new RelayException(RelayErrorKind.REJECTED,
                    "relay rejected creds: " + e.getStatusCode() + " " + e.getStatusText());
        } catch (HttpServerErrorException e) {
            throw new RelayException(RelayErrorKind.TRANSIENT,
                    "relay 5xx: " + e.getStatusCode());
        } catch (RestClientException e) {
            throw new RelayException(RelayErrorKind.TRANSIENT, "relay unreachable: " + e.getMessage(), e);
        }

        JsonNode payload = resp.getBody();
        if (payload == null
                || !payload.hasNonNull("registry")
                || !payload.hasNonNull("username")
                || !payload.hasNonNull("password")) {
            throw new RelayException(RelayErrorKind.BAD_RESPONSE, "incomplete credentials response");
        }
        Instant expiresAt = null;
        if (payload.hasNonNull("expires_at")) {
            try {
                expiresAt = Instant.parse(payload.get("expires_at").asText());
            } catch (Exception e) {
                log.warn("Cannot parse expires_at from relay creds response: {}", e.getMessage());
            }
        }
        return new RegistryCredentials(
                payload.get("registry").asText(),
                payload.get("username").asText(),
                payload.get("password").asText(),
                expiresAt
        );
    }

    private static String stripTrailingSlash(String s) {
        if (s == null) return "";
        String v = s.trim();
        if (v.endsWith("/")) v = v.substring(0, v.length() - 1);
        return v;
    }
}
