package com.loremind.domain.shared;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires pour CollectionUtils (copies defensives).
 * Classe simple mais critique : un oubli de copie peut laisser fuiter des
 * references mutables dans le domaine, ce qui casse l'immuabilite attendue
 * et ouvre la porte a des mutations a distance.
 */
class CollectionUtilsTest {

    // --- copyMap ------------------------------------------------------------

    @Test
    void copyMap_returnsEmptyMap_whenSourceIsNull() {
        Map<String, String> copy = CollectionUtils.copyMap(null);
        assertTrue(copy.isEmpty());
    }

    @Test
    void copyMap_returnsDefensiveCopy_distinctFromSource() {
        Map<String, Integer> source = Map.of("a", 1, "b", 2);
        Map<String, Integer> copy = CollectionUtils.copyMap(source);

        assertEquals(source, copy);
        assertNotSame(source, copy, "La copie doit etre un objet distinct");
    }

    @Test
    void copyMap_mutatingCopyDoesNotAffectSource() {
        Map<String, Integer> source = new HashMap<>();
        source.put("a", 1);
        Map<String, Integer> copy = CollectionUtils.copyMap(source);
        copy.put("b", 2);

        assertEquals(1, source.size(), "La mutation de la copie ne doit pas fuiter sur la source");
        assertEquals(2, copy.size());
    }

    // --- copyList -----------------------------------------------------------

    @Test
    void copyList_returnsEmptyList_whenSourceIsNull() {
        List<String> copy = CollectionUtils.copyList(null);
        assertTrue(copy.isEmpty());
    }

    @Test
    void copyList_returnsDefensiveCopy_distinctFromSource() {
        List<String> source = List.of("x", "y", "z");
        List<String> copy = CollectionUtils.copyList(source);

        assertEquals(source, copy);
        assertNotSame(source, copy);
    }

    @Test
    void copyList_mutatingCopyDoesNotAffectSource() {
        List<String> source = new java.util.ArrayList<>(List.of("a"));
        List<String> copy = CollectionUtils.copyList(source);
        copy.add("b");

        assertEquals(1, source.size());
        assertEquals(2, copy.size());
    }

    @Test
    void copyList_preservesOrder() {
        List<String> source = List.of("zebre", "alpha", "mousse");
        List<String> copy = CollectionUtils.copyList(source);
        assertEquals(List.of("zebre", "alpha", "mousse"), copy);
    }
}
