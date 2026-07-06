package com.loremind.infrastructure.ai;

import com.loremind.domain.campaigncontext.itemcatalog.CatalogItem;
import com.loremind.domain.campaigncontext.ports.exceptions.ItemCatalogGenerationException;
import com.loremind.domain.campaigncontext.ports.ItemCatalogGenerator;
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
 * Adapter de sortie : génère un catalogue d'objets via le Brain (POST one-shot).
 */
@Component
public class BrainItemCatalogClient implements ItemCatalogGenerator {

    private static final String GENERATE_PATH = "/generate/item-catalog";
    private static final String KEY_DESCRIPTION = "description";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public BrainItemCatalogClient(
            RestTemplate restTemplate,
            @Value("${brain.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    @Override
    public GeneratedCatalog generate(String description, String context) {
        Map<String, Object> resp = callBrain(description, context);

        List<CatalogItem> items = parseItems(resp.get("items"));
        if (items.isEmpty()) {
            throw new ItemCatalogGenerationException("Aucun objet généré — réessaie ou reformule.");
        }
        String name = asString(resp.get("name"));
        return new GeneratedCatalog(
                name != null && !name.isBlank() ? name : description,
                asString(resp.get(KEY_DESCRIPTION)),
                items);
    }

    /** POST one-shot vers le Brain ; toute erreur devient une ItemCatalogGenerationException parlante. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> callBrain(String description, String context) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put(KEY_DESCRIPTION, description == null ? "" : description);
        req.put("context", context == null ? "" : context);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(req, headers);

        Map<String, Object> resp;
        try {
            resp = restTemplate.postForObject(baseUrl + GENERATE_PATH, entity, Map.class);
        } catch (ResourceAccessException e) {
            throw new ItemCatalogGenerationException("Le Brain est injoignable (timeout ou arrêté).", e);
        } catch (RestClientResponseException e) {
            throw new ItemCatalogGenerationException(
                    "Le Brain a répondu HTTP " + e.getStatusCode().value() + " : " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new ItemCatalogGenerationException("Erreur inattendue lors de l'appel au Brain.", e);
        }
        if (resp == null) {
            throw new ItemCatalogGenerationException("Le Brain a renvoyé une réponse vide.");
        }
        return resp;
    }

    /** Items valides de la réponse (entrées malformées ou sans nom ignorées). */
    private static List<CatalogItem> parseItems(Object rawItems) {
        List<CatalogItem> items = new ArrayList<>();
        if (rawItems instanceof List<?> list) {
            for (Object raw : list) {
                CatalogItem item = toItem(raw);
                if (item != null) {
                    items.add(item);
                }
            }
        }
        return items;
    }

    /** Un CatalogItem depuis une entrée brute, ou null si malformée / sans nom. */
    private static CatalogItem toItem(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) {
            return null;
        }
        String name = asString(m.get("name"));
        if (name == null || name.isBlank()) {
            return null;
        }
        return CatalogItem.builder()
                .name(name)
                .price(asString(m.get("price")))
                .category(asString(m.get("category")))
                .description(asString(m.get(KEY_DESCRIPTION)))
                .build();
    }

    private static String asString(Object o) {
        return o != null ? o.toString() : null;
    }
}
