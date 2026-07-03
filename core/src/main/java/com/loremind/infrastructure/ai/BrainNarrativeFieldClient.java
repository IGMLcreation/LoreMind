package com.loremind.infrastructure.ai;

import com.loremind.domain.campaigncontext.ports.NarrativeAssistException;
import com.loremind.domain.campaigncontext.ports.NarrativeFieldAssistant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adapter de sortie : étoffe les champs d'une entité narrative via le Brain (POST one-shot).
 * Générique par {@code entityType}. Le Core envoie les champs AVEC leur libellé (source de
 * vérité) ; double garde-fou : on ne retient que les clés autorisées, valeurs non vides.
 */
@Component
public class BrainNarrativeFieldClient implements NarrativeFieldAssistant {

    private static final String GENERATE_PATH = "/generate/narrative-fields";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public BrainNarrativeFieldClient(
            RestTemplate restTemplate,
            @Value("${brain.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ProposedField> assist(String entityType, String context, String instruction, List<FieldSpec> fields) {
        List<Map<String, String>> fieldPayload = new ArrayList<>();
        Set<String> allowed = new HashSet<>();
        if (fields != null) {
            for (FieldSpec f : fields) {
                if (f == null || f.key() == null) continue;
                allowed.add(f.key());
                Map<String, String> fm = new LinkedHashMap<>();
                fm.put("key", f.key());
                fm.put("label", f.label() == null ? f.key() : f.label());
                fieldPayload.add(fm);
            }
        }

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("entity_type", entityType == null ? "" : entityType);
        req.put("context", context == null ? "" : context);
        req.put("instruction", instruction == null ? "" : instruction);
        req.put("fields", fieldPayload);

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

        List<ProposedField> out = new ArrayList<>();
        Object rawFields = resp.get("fields");
        if (rawFields instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String key = e.getKey() == null ? null : e.getKey().toString();
                if (key == null || !allowed.contains(key)) continue;      // whitelist stricte
                String value = e.getValue() == null ? null : e.getValue().toString();
                if (value == null || value.isBlank()) continue;           // pas de remplissage vide
                out.add(new ProposedField(key, value.trim()));
            }
        }
        return out;
    }
}
