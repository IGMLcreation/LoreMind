package com.loremind.infrastructure.persistence.converter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests pour StringListJsonConverter (JPA AttributeConverter).
 * Convention : null/vide -> "[]" en DB, DB null/blank -> liste vide en entite.
 */
class StringListJsonConverterTest {

    private final StringListJsonConverter converter = new StringListJsonConverter();

    // ---------- convertToDatabaseColumn ------------------------------------

    @Test
    void toDb_nullList_yieldsEmptyJsonArray() {
        assertEquals("[]", converter.convertToDatabaseColumn(null));
    }

    @Test
    void toDb_emptyList_yieldsEmptyJsonArray() {
        assertEquals("[]", converter.convertToDatabaseColumn(List.of()));
    }

    @Test
    void toDb_populatedList_yieldsJsonArray() {
        assertEquals("[\"a\",\"b\",\"c\"]",
                converter.convertToDatabaseColumn(List.of("a", "b", "c")));
    }

    @Test
    void toDb_preservesOrder() {
        assertEquals("[\"zebre\",\"alpha\",\"mousse\"]",
                converter.convertToDatabaseColumn(List.of("zebre", "alpha", "mousse")));
    }

    // ---------- convertToEntityAttribute -----------------------------------

    @Test
    void fromDb_nullString_yieldsEmptyList() {
        assertTrue(converter.convertToEntityAttribute(null).isEmpty());
    }

    @Test
    void fromDb_blankString_yieldsEmptyList() {
        assertTrue(converter.convertToEntityAttribute("   ").isEmpty());
    }

    @Test
    void fromDb_emptyJsonArray_yieldsEmptyList() {
        assertTrue(converter.convertToEntityAttribute("[]").isEmpty());
    }

    @Test
    void fromDb_populatedJsonArray_yieldsList() {
        assertEquals(List.of("x", "y"), converter.convertToEntityAttribute("[\"x\",\"y\"]"));
    }

    @Test
    void fromDb_malformedJson_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class,
                () -> converter.convertToEntityAttribute("not a json"));
    }

    // ---------- Round-trip --------------------------------------------------

    @Test
    void roundTrip_preservesAllEntries() {
        List<String> source = List.of("pnj", "allie", "royaume");
        String json = converter.convertToDatabaseColumn(source);
        assertEquals(source, converter.convertToEntityAttribute(json));
    }
}
