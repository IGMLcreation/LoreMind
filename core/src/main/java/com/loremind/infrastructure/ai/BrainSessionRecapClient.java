package com.loremind.infrastructure.ai;

import com.loremind.domain.playcontext.ports.SessionRecapAssistant;
import com.loremind.domain.playcontext.ports.exceptions.SessionRecapException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adapter de sortie : récap « précédemment… » d'une séance via le Brain (POST one-shot).
 * Calqué sur les autres clients Brain.
 */
@Component
public class BrainSessionRecapClient implements SessionRecapAssistant {

    private static final String GENERATE_PATH = "/generate/session-recap";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public BrainSessionRecapClient(
            RestTemplate restTemplate,
            @Value("${brain.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String generateRecap(String transcript, String context) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("transcript", transcript == null ? "" : transcript);
        req.put("context", context == null ? "" : context);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(req, headers);

        Map<String, Object> resp;
        try {
            resp = restTemplate.postForObject(baseUrl + GENERATE_PATH, entity, Map.class);
        } catch (ResourceAccessException e) {
            throw new SessionRecapException("Le Brain est injoignable (timeout ou arrêté).", e);
        } catch (RestClientResponseException e) {
            throw new SessionRecapException(
                    "Le Brain a répondu HTTP " + e.getStatusCode().value() + " : " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new SessionRecapException("Erreur inattendue lors de l'appel au Brain.", e);
        }
        Object recap = resp != null ? resp.get("recap") : null;
        if (recap == null || recap.toString().isBlank()) {
            throw new SessionRecapException("Le Brain a renvoyé un récap vide.");
        }
        return recap.toString().trim();
    }
}
