package com.loremind.infrastructure.ai;

import com.loremind.domain.campaigncontext.generation.SceneDraft;
import com.loremind.domain.campaigncontext.ports.exceptions.NarrativeAssistException;
import com.loremind.domain.campaigncontext.ports.SceneDraftAssistant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter de sortie : ébauche des scènes pour un chapitre via le Brain (POST one-shot).
 * Calqué sur les autres clients Brain. Ne retient que les ébauches avec un titre non vide.
 */
@Component
public class BrainSceneDraftClient implements SceneDraftAssistant {

    private static final String GENERATE_PATH = "/generate/scene-drafts";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public BrainSceneDraftClient(
            RestTemplate restTemplate,
            @Value("${brain.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<SceneDraft> draftScenes(String context, String instruction, int count) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("context", context == null ? "" : context);
        req.put("instruction", instruction == null ? "" : instruction);
        req.put("count", count);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(req, headers);

        Map<String, Object> resp;
        try {
            resp = restTemplate.postForObject(baseUrl + GENERATE_PATH, entity, Map.class);
        } catch (ResourceAccessException e) {
            throw new NarrativeAssistException("Le Brain est injoignable (timeout ou arrêté).", e);
        } catch (RestClientResponseException e) {
            throw new NarrativeAssistException(
                    "Le Brain a répondu HTTP " + e.getStatusCode().value() + " : " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new NarrativeAssistException("Erreur inattendue lors de l'appel au Brain.", e);
        }
        if (resp == null) {
            throw new NarrativeAssistException("Le Brain a renvoyé une réponse vide.");
        }
        return parseScenes(resp.get("scenes"));
    }

    /** Ébauches valides de la réponse (entrées malformées ou sans titre ignorées). */
    private static List<SceneDraft> parseScenes(Object rawScenes) {
        List<SceneDraft> out = new ArrayList<>();
        if (rawScenes instanceof List<?> list) {
            for (Object raw : list) {
                SceneDraft draft = toSceneDraft(raw);
                if (draft != null) {
                    out.add(draft);
                }
            }
        }
        return out;
    }

    /** Une SceneDraft depuis une entrée brute, ou null si malformée / sans titre. */
    private static SceneDraft toSceneDraft(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) {
            return null;
        }
        String name = asString(m.get("name"));
        if (name == null || name.isBlank()) {
            return null; // un titre est obligatoire
        }
        return new SceneDraft(
                name.trim(),
                trimOrNull(asString(m.get("description"))),
                trimOrNull(asString(m.get("playerNarration"))));
    }

    private static String asString(Object o) {
        return o != null ? o.toString() : null;
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
