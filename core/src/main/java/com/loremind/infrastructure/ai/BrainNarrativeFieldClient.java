package com.loremind.infrastructure.ai;

import com.loremind.domain.campaigncontext.ports.exceptions.NarrativeAssistException;
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
    public List<ProposedField> assist(String entityType, String context, String instruction, List<FieldSpec> fields) {
        List<Map<String, String>> fieldPayload = buildFieldPayload(fields);
        Set<String> allowed = allowedKeys(fields);

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("entity_type", entityType == null ? "" : entityType);
        req.put("context", context == null ? "" : context);
        req.put("instruction", instruction == null ? "" : instruction);
        req.put("fields", fieldPayload);

        Map<String, Object> resp = callBrain(req);
        return parseProposedFields(resp.get("fields"), allowed);
    }

    /** Payload des champs à étoffer : {key, label} par champ valide (libellé = clé à défaut). */
    private static List<Map<String, String>> buildFieldPayload(List<FieldSpec> fields) {
        List<Map<String, String>> payload = new ArrayList<>();
        for (FieldSpec f : nullSafe(fields)) {
            if (f != null && f.key() != null) {
                Map<String, String> fm = new LinkedHashMap<>();
                fm.put("key", f.key());
                fm.put("label", f.label() == null ? f.key() : f.label());
                payload.add(fm);
            }
        }
        return payload;
    }

    /** Whitelist des clés autorisées en retour (garde-fou contre les clés inventées). */
    private static Set<String> allowedKeys(List<FieldSpec> fields) {
        Set<String> allowed = new HashSet<>();
        for (FieldSpec f : nullSafe(fields)) {
            if (f != null && f.key() != null) {
                allowed.add(f.key());
            }
        }
        return allowed;
    }

    /** POST one-shot vers le Brain ; toute erreur devient une NarrativeAssistException parlante. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> callBrain(Map<String, Object> req) {
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
        return resp;
    }

    /** Champs proposés retenus : clé dans la whitelist ET valeur non vide (trim). */
    private static List<ProposedField> parseProposedFields(Object rawFields, Set<String> allowed) {
        List<ProposedField> out = new ArrayList<>();
        if (rawFields instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                ProposedField field = toProposedField(e, allowed);
                if (field != null) {
                    out.add(field);
                }
            }
        }
        return out;
    }

    /** Un ProposedField depuis une entrée brute, ou null si clé hors whitelist / valeur vide. */
    private static ProposedField toProposedField(Map.Entry<?, ?> e, Set<String> allowed) {
        String key = e.getKey() == null ? null : e.getKey().toString();
        if (key == null || !allowed.contains(key)) {
            return null; // whitelist stricte
        }
        String value = e.getValue() == null ? null : e.getValue().toString();
        if (value == null || value.isBlank()) {
            return null; // pas de remplissage vide
        }
        return new ProposedField(key, value.trim());
    }

    private static List<FieldSpec> nullSafe(List<FieldSpec> fields) {
        return fields != null ? fields : List.of();
    }
}
