package com.loremind.infrastructure.ai;

import com.loremind.domain.campaigncontext.CatalogItem;
import com.loremind.domain.campaigncontext.ports.ItemCatalogGenerationException;
import com.loremind.domain.campaigncontext.ports.ItemCatalogGenerator.GeneratedCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires purs (JUnit 5 + Mockito, sans Spring) de {@link BrainItemCatalogClient}.
 * Le RestTemplate est mocké : {@code postForObject(url, entity, Map.class)}.
 */
class BrainItemCatalogClientTest {

    private RestTemplate rt;
    private BrainItemCatalogClient client;

    @BeforeEach
    void setUp() {
        rt = mock(RestTemplate.class);
        client = new BrainItemCatalogClient(rt, "http://brain");
    }

    /** Construit une map d'objet pour le payload "items". */
    private static Map<String, Object> item(Object name, Object price, Object category, Object description) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("price", price);
        m.put("category", category);
        m.put("description", description);
        return m;
    }

    // ---------- generate : cas nominal et mapping ----------

    @Test
    void generate_reponseValide_mappeItems_etIgnoreInvalides() {
        List<Object> items = new ArrayList<>();
        items.add(item("Épée longue", "15 po", "Armes", "Tranchante"));   // valide
        items.add(item("Potion", null, null, null));                       // valide, champs null
        items.add(item(null, "1 po", "x", "y"));                          // ignoré : name null
        items.add(item("   ", "1 po", "x", "y"));                         // ignoré : name blank
        items.add("pas une map");                                         // ignoré : item non-Map

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("name", "Boutique du forgeron");
        resp.put("description", "Au village");
        resp.put("items", items);

        when(rt.postForObject(anyString(), any(), eq(Map.class))).thenReturn(resp);

        GeneratedCatalog cat = client.generate("desc", "ctx");

        assertEquals("Boutique du forgeron", cat.name());
        assertEquals("Au village", cat.description());
        assertEquals(2, cat.items().size());

        CatalogItem i0 = cat.items().get(0);
        assertEquals("Épée longue", i0.getName());
        assertEquals("15 po", i0.getPrice());
        assertEquals("Armes", i0.getCategory());
        assertEquals("Tranchante", i0.getDescription());

        CatalogItem i1 = cat.items().get(1);
        assertEquals("Potion", i1.getName());
        assertNull(i1.getPrice());
        assertNull(i1.getCategory());
        assertNull(i1.getDescription());
    }

    @Test
    void generate_nameAbsent_fallbackSurDescription() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("items", List.of(item("Objet", null, null, null)));
        // pas de "name" -> fallback description

        when(rt.postForObject(anyString(), any(), eq(Map.class))).thenReturn(resp);

        GeneratedCatalog cat = client.generate("MaDescription", "ctx");
        assertEquals("MaDescription", cat.name());
        assertNull(cat.description());
    }

    @Test
    void generate_nameBlank_fallbackSurDescription() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("name", "  ");
        resp.put("items", List.of(item("Objet", null, null, null)));

        when(rt.postForObject(anyString(), any(), eq(Map.class))).thenReturn(resp);

        GeneratedCatalog cat = client.generate("Fallback", "ctx");
        assertEquals("Fallback", cat.name());
    }

    @Test
    void generate_argumentsNull_remplisParDefauts() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("name", "Cat");
        resp.put("items", List.of(item("Objet", null, null, null)));

        when(rt.postForObject(anyString(), any(), eq(Map.class))).thenReturn(resp);

        GeneratedCatalog cat = client.generate(null, null);
        assertEquals("Cat", cat.name());
    }

    // ---------- generate : réponse vide / pas d'items ----------

    @Test
    void generate_reponseNull_leveException() {
        when(rt.postForObject(anyString(), any(), eq(Map.class))).thenReturn(null);

        ItemCatalogGenerationException ex = assertThrows(ItemCatalogGenerationException.class,
                () -> client.generate("d", "c"));
        assertTrue(ex.getMessage().contains("réponse vide"));
    }

    @Test
    void generate_itemsAbsents_leveException() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("name", "Cat"); // pas de "items"

        when(rt.postForObject(anyString(), any(), eq(Map.class))).thenReturn(resp);

        ItemCatalogGenerationException ex = assertThrows(ItemCatalogGenerationException.class,
                () -> client.generate("d", "c"));
        assertTrue(ex.getMessage().contains("Aucun objet"));
    }

    @Test
    void generate_itemsVide_leveException() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("items", new ArrayList<>());

        when(rt.postForObject(anyString(), any(), eq(Map.class))).thenReturn(resp);

        assertThrows(ItemCatalogGenerationException.class, () -> client.generate("d", "c"));
    }

    @Test
    void generate_tousItemsInvalides_leveException() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("items", List.of(item(null, null, null, null)));

        when(rt.postForObject(anyString(), any(), eq(Map.class))).thenReturn(resp);

        assertThrows(ItemCatalogGenerationException.class, () -> client.generate("d", "c"));
    }

    // ---------- generate : branches du catch ----------

    @Test
    void generate_brainInjoignable_resourceAccess() {
        when(rt.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(new ResourceAccessException("down"));

        ItemCatalogGenerationException ex = assertThrows(ItemCatalogGenerationException.class,
                () -> client.generate("d", "c"));
        assertTrue(ex.getMessage().contains("injoignable"));
        assertInstanceOf(ResourceAccessException.class, ex.getCause());
    }

    @Test
    void generate_erreurHttp_restClientResponse() {
        when(rt.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(HttpServerErrorException.create(
                        HttpStatus.BAD_GATEWAY, "Bad Gateway", new HttpHeaders(), new byte[0], null));

        ItemCatalogGenerationException ex = assertThrows(ItemCatalogGenerationException.class,
                () -> client.generate("d", "c"));
        assertTrue(ex.getMessage().contains("HTTP 502"));
    }

    @Test
    void generate_erreurInattendue_exceptionGenerique() {
        when(rt.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(new IllegalStateException("boom"));

        ItemCatalogGenerationException ex = assertThrows(ItemCatalogGenerationException.class,
                () -> client.generate("d", "c"));
        assertTrue(ex.getMessage().contains("inattendue"));
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }
}
