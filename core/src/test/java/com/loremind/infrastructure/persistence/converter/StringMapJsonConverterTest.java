package com.loremind.infrastructure.persistence.converter;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests pour StringMapJsonConverter (Page.values).
 * Convention : null/vide -> "{}" en DB, DB null/blank -> map vide en entite.
 */
class StringMapJsonConverterTest {

    private final StringMapJsonConverter converter = new StringMapJsonConverter();

    @Test
    void toDb_nullMap_yieldsEmptyJsonObject() {
        assertEquals("{}", converter.convertToDatabaseColumn(null));
    }

    @Test
    void toDb_emptyMap_yieldsEmptyJsonObject() {
        assertEquals("{}", converter.convertToDatabaseColumn(Map.of()));
    }

    @Test
    void toDb_populatedMap_yieldsJsonObject() {
        // LinkedHashMap pour un ordre deterministe dans l'assertion.
        Map<String, String> m = new LinkedHashMap<>();
        m.put("a", "1");
        m.put("b", "2");
        assertEquals("{\"a\":\"1\",\"b\":\"2\"}", converter.convertToDatabaseColumn(m));
    }

    @Test
    void fromDb_nullString_yieldsEmptyMap() {
        assertTrue(converter.convertToEntityAttribute(null).isEmpty());
    }

    @Test
    void fromDb_blankString_yieldsEmptyMap() {
        assertTrue(converter.convertToEntityAttribute("  ").isEmpty());
    }

    @Test
    void fromDb_emptyJsonObject_yieldsEmptyMap() {
        assertTrue(converter.convertToEntityAttribute("{}").isEmpty());
    }

    @Test
    void fromDb_populatedJson_yieldsMap() {
        Map<String, String> result = converter.convertToEntityAttribute("{\"histoire\":\"Nee sous une etoile rouge\"}");
        assertEquals(1, result.size());
        assertEquals("Nee sous une etoile rouge", result.get("histoire"));
    }

    @Test
    void fromDb_malformedJson_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class,
                () -> converter.convertToEntityAttribute("{not valid"));
    }

    @Test
    void roundTrip_preservesEntries() {
        Map<String, String> source = Map.of("histoire", "Nee sous une etoile rouge", "motto", "Jamais");
        String json = converter.convertToDatabaseColumn(source);
        assertEquals(source, converter.convertToEntityAttribute(json));
    }
}
