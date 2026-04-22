package com.loremind.infrastructure.persistence.converter;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests pour StringListMapJsonConverter (Page.imageValues).
 * Structure : Map<String, List<String>> — pour chaque champ IMAGE, la liste
 * ordonnee des IDs d'images attachees.
 */
class StringListMapJsonConverterTest {

    private final StringListMapJsonConverter converter = new StringListMapJsonConverter();

    @Test
    void toDb_nullMap_yieldsEmptyJsonObject() {
        assertEquals("{}", converter.convertToDatabaseColumn(null));
    }

    @Test
    void toDb_emptyMap_yieldsEmptyJsonObject() {
        assertEquals("{}", converter.convertToDatabaseColumn(Map.of()));
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
    void fromDb_populatedJson_yieldsMap() {
        Map<String, List<String>> result = converter.convertToEntityAttribute(
                "{\"Portrait\":[\"42\",\"17\"],\"Carte\":[\"99\"]}");
        assertEquals(2, result.size());
        assertEquals(List.of("42", "17"), result.get("Portrait"));
        assertEquals(List.of("99"), result.get("Carte"));
    }

    @Test
    void fromDb_malformedJson_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class,
                () -> converter.convertToEntityAttribute("{bad"));
    }

    @Test
    void roundTrip_preservesStructureAndOrder() {
        Map<String, List<String>> source = Map.of(
                "Portrait", List.of("42", "17"),
                "Carte", List.of("99")
        );
        String json = converter.convertToDatabaseColumn(source);
        Map<String, List<String>> back = converter.convertToEntityAttribute(json);
        assertEquals(source, back);
        assertEquals(List.of("42", "17"), back.get("Portrait"),
                "L'ordre des IDs dans la liste est significatif (1ere = principale)");
    }
}
