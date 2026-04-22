package com.loremind.infrastructure.persistence.converter;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests pour MapJsonConverter (Map<String,Object> generique).
 * ATTENTION : contrairement aux autres converters, celui-ci renvoie null pour
 * null (pas "{}"), et "autoApply=false" ne s'applique qu'aux champs annotes
 * explicitement. Design historique — les tests documentent cette specificite.
 */
class MapJsonConverterTest {

    private final MapJsonConverter converter = new MapJsonConverter();

    @Test
    void toDb_nullMap_returnsNull() {
        assertNull(converter.convertToDatabaseColumn(null));
    }

    @Test
    void toDb_emptyMap_returnsEmptyJsonObject() {
        assertEquals("{}", converter.convertToDatabaseColumn(Map.of()));
    }

    @Test
    void toDb_populatedMap_returnsJson() {
        String json = converter.convertToDatabaseColumn(Map.of("n", 42));
        assertEquals("{\"n\":42}", json);
    }

    @Test
    void fromDb_nullString_returnsNull() {
        assertNull(converter.convertToEntityAttribute(null));
    }

    @Test
    void fromDb_emptyJsonObject_returnsEmptyMap() {
        assertEquals(Map.of(), converter.convertToEntityAttribute("{}"));
    }

    @Test
    void fromDb_populatedJson_returnsMap() {
        Map<String, Object> result = converter.convertToEntityAttribute("{\"age\":42,\"nom\":\"Thorin\"}");
        assertEquals(2, result.size());
        assertEquals(42, result.get("age"));
        assertEquals("Thorin", result.get("nom"));
    }

    @Test
    void fromDb_malformedJson_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> converter.convertToEntityAttribute("not json"));
    }

    @Test
    void roundTrip_preservesValues() {
        Map<String, Object> source = Map.of("s", "hello", "n", 7);
        String json = converter.convertToDatabaseColumn(source);
        assertEquals(source, converter.convertToEntityAttribute(json));
    }
}
