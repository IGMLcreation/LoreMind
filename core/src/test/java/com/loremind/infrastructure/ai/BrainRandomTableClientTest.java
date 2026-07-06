package com.loremind.infrastructure.ai;

import com.loremind.domain.campaigncontext.randomtable.RandomTableEntry;
import com.loremind.domain.campaigncontext.ports.exceptions.RandomTableGenerationException;
import com.loremind.domain.campaigncontext.ports.RandomTableGenerator.GeneratedTable;
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
 * Tests unitaires purs (JUnit 5 + Mockito, sans Spring) de {@link BrainRandomTableClient}.
 * Le RestTemplate est mocké : {@code postForObject(url, entity, Map.class)}.
 */
class BrainRandomTableClientTest {

    private RestTemplate rt;
    private BrainRandomTableClient client;

    @BeforeEach
    void setUp() {
        rt = mock(RestTemplate.class);
        client = new BrainRandomTableClient(rt, "http://brain");
    }

    /** Construit une map d'entrée pour le payload "entries". */
    private static Map<String, Object> entry(Object min, Object max, Object label, Object detail) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("min_roll", min);
        m.put("max_roll", max);
        m.put("label", label);
        m.put("detail", detail);
        return m;
    }

    // ---------- generate : cas nominal et branches de parsing ----------

    @Test
    void generate_reponseValide_plusieursEntrees_avecEntreesInvalidesIgnorees() {
        List<Object> entries = new ArrayList<>();
        entries.add(entry(1, 5, "Embuscade", "des gobelins"));         // valide (Number)
        entries.add(entry("6", "10", "Trésor", null));                 // valide (String -> asInt)
        entries.add(entry(null, 3, "min null", "x"));                  // ignorée : min null
        entries.add(entry(3, null, "max null", "x"));                  // ignorée : max null
        entries.add(entry(1, 2, null, "x"));                          // ignorée : label null
        entries.add(entry(1, 2, "   ", "x"));                         // ignorée : label blank
        entries.add(entry("abc", 2, "min non-num", "x"));             // ignorée : asInt String non-numérique
        entries.add("pas une map");                                   // ignorée : item non-Map

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("name", "Table des rencontres");
        resp.put("description", "En forêt");
        resp.put("entries", entries);

        when(rt.postForObject(anyString(), any(), eq(Map.class))).thenReturn(resp);

        GeneratedTable table = client.generate("desc", "1d10", "ctx");

        assertEquals("Table des rencontres", table.name());
        assertEquals("En forêt", table.description());
        assertEquals(2, table.entries().size());

        RandomTableEntry e0 = table.entries().get(0);
        assertEquals(1, e0.getMinRoll());
        assertEquals(5, e0.getMaxRoll());
        assertEquals("Embuscade", e0.getLabel());
        assertEquals("des gobelins", e0.getDetail());

        RandomTableEntry e1 = table.entries().get(1);
        assertEquals(6, e1.getMinRoll());
        assertEquals(10, e1.getMaxRoll());
        assertEquals("Trésor", e1.getLabel());
        assertNull(e1.getDetail()); // detail null -> asString(null) == null
    }

    @Test
    void generate_maxInferieurAMin_estCorrigeParMathMax() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("entries", List.of(entry(8, 3, "Inversé", null)));

        when(rt.postForObject(anyString(), any(), eq(Map.class))).thenReturn(resp);

        GeneratedTable table = client.generate("d", "1d8", "c");
        RandomTableEntry e = table.entries().get(0);
        assertEquals(8, e.getMinRoll());
        assertEquals(8, e.getMaxRoll()); // Math.max(8,3) == 8
    }

    @Test
    void generate_nameAbsent_fallbackSurDescription() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("entries", List.of(entry(1, 1, "Ok", null)));
        // pas de "name" -> asString(null) == null -> fallback description

        when(rt.postForObject(anyString(), any(), eq(Map.class))).thenReturn(resp);

        GeneratedTable table = client.generate("MaDescription", "1d20", "c");
        assertEquals("MaDescription", table.name());
        assertNull(table.description()); // description absente
    }

    @Test
    void generate_nameBlank_fallbackSurDescription() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("name", "   ");
        resp.put("entries", List.of(entry(1, 1, "Ok", null)));

        when(rt.postForObject(anyString(), any(), eq(Map.class))).thenReturn(resp);

        GeneratedTable table = client.generate("Fallback", "1d20", "c");
        assertEquals("Fallback", table.name());
    }

    @Test
    void generate_argumentsNull_remplisParDefauts_etAppelle() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("name", "T");
        resp.put("entries", List.of(entry(1, 1, "Ok", null)));

        when(rt.postForObject(anyString(), any(), eq(Map.class))).thenReturn(resp);

        // description/diceFormula/context null -> branches de défaut couvertes
        GeneratedTable table = client.generate(null, null, null);
        assertEquals("T", table.name());
    }

    @Test
    void generate_reponseNull_leveException() {
        when(rt.postForObject(anyString(), any(), eq(Map.class))).thenReturn(null);

        RandomTableGenerationException ex = assertThrows(RandomTableGenerationException.class,
                () -> client.generate("d", "1d20", "c"));
        assertTrue(ex.getMessage().contains("réponse vide"));
    }

    @Test
    void generate_entriesAbsentes_aucuneEntree_leveException() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("name", "T");
        // pas de "entries" -> rawEntries null, pas un List<?>

        when(rt.postForObject(anyString(), any(), eq(Map.class))).thenReturn(resp);

        RandomTableGenerationException ex = assertThrows(RandomTableGenerationException.class,
                () -> client.generate("d", "1d20", "c"));
        assertTrue(ex.getMessage().contains("Aucune entrée"));
    }

    @Test
    void generate_entriesVide_leveException() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("entries", new ArrayList<>()); // List mais vide

        when(rt.postForObject(anyString(), any(), eq(Map.class))).thenReturn(resp);

        RandomTableGenerationException ex = assertThrows(RandomTableGenerationException.class,
                () -> client.generate("d", "1d20", "c"));
        assertTrue(ex.getMessage().contains("Aucune entrée"));
    }

    @Test
    void generate_toutesEntreesInvalides_leveException() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("entries", List.of(entry(null, null, null, null)));

        when(rt.postForObject(anyString(), any(), eq(Map.class))).thenReturn(resp);

        assertThrows(RandomTableGenerationException.class,
                () -> client.generate("d", "1d20", "c"));
    }

    // ---------- generate : branches du catch ----------

    @Test
    void generate_brainInjoignable_resourceAccess() {
        when(rt.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(new ResourceAccessException("down"));

        RandomTableGenerationException ex = assertThrows(RandomTableGenerationException.class,
                () -> client.generate("d", "1d20", "c"));
        assertTrue(ex.getMessage().contains("injoignable"));
        assertInstanceOf(ResourceAccessException.class, ex.getCause());
    }

    @Test
    void generate_erreurHttp_restClientResponse() {
        when(rt.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(HttpServerErrorException.create(
                        HttpStatus.BAD_GATEWAY, "Bad Gateway", new HttpHeaders(), new byte[0], null));

        RandomTableGenerationException ex = assertThrows(RandomTableGenerationException.class,
                () -> client.generate("d", "1d20", "c"));
        assertTrue(ex.getMessage().contains("HTTP 502"));
    }

    @Test
    void generate_erreurInattendue_exceptionGenerique() {
        when(rt.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(new IllegalStateException("boom"));

        RandomTableGenerationException ex = assertThrows(RandomTableGenerationException.class,
                () -> client.generate("d", "1d20", "c"));
        assertTrue(ex.getMessage().contains("inattendue"));
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    // ---------- improvise ----------

    @Test
    void improvise_narrationPresente_retourneNarration() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("narration", "Les gobelins surgissent des fourrés.");

        when(rt.postForObject(anyString(), any(), eq(Map.class))).thenReturn(resp);

        String out = client.improvise("Table", "Embuscade", "détail", "ctx");
        assertEquals("Les gobelins surgissent des fourrés.", out);
    }

    @Test
    void improvise_argumentsNull_remplisParDefauts() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("narration", "ok");

        when(rt.postForObject(anyString(), any(), eq(Map.class))).thenReturn(resp);

        assertEquals("ok", client.improvise(null, null, null, null));
    }

    @Test
    void improvise_reponseNull_retourneChaineVide() {
        when(rt.postForObject(anyString(), any(), eq(Map.class))).thenReturn(null);

        assertEquals("", client.improvise("T", "L", "D", "C"));
    }

    @Test
    void improvise_narrationAbsente_retourneChaineVide() {
        Map<String, Object> resp = new LinkedHashMap<>(); // pas de "narration"

        when(rt.postForObject(anyString(), any(), eq(Map.class))).thenReturn(resp);

        assertEquals("", client.improvise("T", "L", "D", "C"));
    }

    @Test
    void improvise_brainInjoignable_propageException() {
        when(rt.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(new ResourceAccessException("down"));

        assertThrows(RandomTableGenerationException.class,
                () -> client.improvise("T", "L", "D", "C"));
    }
}
