package com.loremind.infrastructure.ai;

import com.loremind.domain.campaigncontext.randomtable.RandomTableEntry;
import com.loremind.domain.campaigncontext.ports.exceptions.RandomTableGenerationException;
import com.loremind.domain.campaigncontext.ports.RandomTableGenerator;
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
 * Adapter de sortie : implémente {@link RandomTableGenerator} en appelant le Brain
 * (POST one-shot, RestTemplate). Le secret inter-service est injecté par l'intercepteur
 * du bean {@code brainRestTemplate}.
 */
@Component
public class BrainRandomTableClient implements RandomTableGenerator {

    private static final String GENERATE_PATH = "/generate/random-table";
    private static final String IMPROVISE_PATH = "/improvise/table-roll";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public BrainRandomTableClient(
            RestTemplate restTemplate,
            @Value("${brain.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    @Override
    public GeneratedTable generate(String description, String diceFormula, String context) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("description", description == null ? "" : description);
        req.put("dice_formula", diceFormula == null ? "1d20" : diceFormula);
        req.put("context", context == null ? "" : context);

        Map<String, Object> resp = post(GENERATE_PATH, req);
        if (resp == null) {
            throw new RandomTableGenerationException("Le Brain a renvoyé une réponse vide.");
        }
        List<RandomTableEntry> entries = parseEntries(resp.get("entries"));
        if (entries.isEmpty()) {
            throw new RandomTableGenerationException("Aucune entrée générée — réessaie ou reformule.");
        }
        String name = asString(resp.get("name"));
        return new GeneratedTable(
                name != null && !name.isBlank() ? name : description,
                asString(resp.get("description")),
                entries);
    }

    /** Entrées valides de la réponse (entrées malformées ou incomplètes ignorées). */
    private static List<RandomTableEntry> parseEntries(Object rawEntries) {
        List<RandomTableEntry> entries = new ArrayList<>();
        if (rawEntries instanceof List<?> list) {
            for (Object raw : list) {
                RandomTableEntry entry = toEntry(raw);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        }
        return entries;
    }

    /** Une RandomTableEntry depuis une entrée brute, ou null si malformée / bornes ou label absents. */
    private static RandomTableEntry toEntry(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) {
            return null;
        }
        Integer min = asInt(m.get("min_roll"));
        Integer max = asInt(m.get("max_roll"));
        String label = asString(m.get("label"));
        if (min == null || max == null || label == null || label.isBlank()) {
            return null;
        }
        return RandomTableEntry.builder()
                .minRoll(min)
                .maxRoll(Math.max(min, max))
                .label(label)
                .detail(asString(m.get("detail")))
                .build();
    }

    @Override
    public String improvise(String tableName, String resultLabel, String resultDetail, String context) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("table_name", tableName == null ? "" : tableName);
        req.put("result_label", resultLabel == null ? "" : resultLabel);
        req.put("result_detail", resultDetail == null ? "" : resultDetail);
        req.put("context", context == null ? "" : context);

        Map<String, Object> resp = post(IMPROVISE_PATH, req);
        String narration = resp != null ? asString(resp.get("narration")) : null;
        return narration != null ? narration : "";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        try {
            return restTemplate.postForObject(baseUrl + path, entity, Map.class);
        } catch (ResourceAccessException e) {
            throw new RandomTableGenerationException("Le Brain est injoignable (timeout ou arrêté).", e);
        } catch (RestClientResponseException e) {
            throw new RandomTableGenerationException(
                    "Le Brain a répondu HTTP " + e.getStatusCode().value() + " : " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new RandomTableGenerationException("Erreur inattendue lors de l'appel au Brain.", e);
        }
    }

    private static Integer asInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    private static String asString(Object o) {
        return o != null ? o.toString() : null;
    }
}
