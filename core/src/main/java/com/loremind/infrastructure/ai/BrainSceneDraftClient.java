package com.loremind.infrastructure.ai;

import com.loremind.domain.campaigncontext.SceneDraft;
import com.loremind.domain.campaigncontext.ports.NarrativeAssistException;
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

        List<SceneDraft> out = new ArrayList<>();
        Object rawScenes = resp.get("scenes");
        if (rawScenes instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> m)) continue;
                String name = asString(m.get("name"));
                if (name == null || name.isBlank()) continue;      // un titre est obligatoire
                out.add(new SceneDraft(
                        name.trim(),
                        trimOrNull(asString(m.get("description"))),
                        trimOrNull(asString(m.get("playerNarration")))));
            }
        }
        return out;
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
