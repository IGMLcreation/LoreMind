package com.loremind.domain.shared;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test unitaire pour {@link ReorderSupport}. Couvre en particulier les ids inconnus,
 * qui doivent être ignorés silencieusement (contrat documenté du finder) et non lever.
 */
class ReorderSupportTest {

    private record Item(String id) {
    }

    @Test
    void reorder_assignsPositionsInOrderedIdsOrder() {
        Map<String, Integer> positions = new HashMap<>();
        Map<String, Item> byId = Map.of("a", new Item("a"), "b", new Item("b"), "c", new Item("c"));
        List<Item> saved = new ArrayList<>();

        ReorderSupport.reorder(List.of("c", "a", "b"),
                id -> Optional.ofNullable(byId.get(id)),
                (item, i) -> positions.put(item.id(), i),
                saved::add);

        assertEquals(0, positions.get("c"));
        assertEquals(1, positions.get("a"));
        assertEquals(2, positions.get("b"));
        assertEquals(3, saved.size());
    }

    @Test
    void reorder_unknownId_isSkippedWithoutThrowing() {
        Map<String, Integer> positions = new HashMap<>();
        Map<String, Item> byId = Map.of("a", new Item("a"));
        List<Item> saved = new ArrayList<>();

        ReorderSupport.reorder(List.of("a", "ghost"),
                id -> Optional.ofNullable(byId.get(id)),
                (item, i) -> positions.put(item.id(), i),
                saved::add);

        assertEquals(1, saved.size());
        assertEquals(0, positions.get("a"));
        assertFalse(positions.containsKey("ghost"));
    }

    @Test
    void reorder_nullOrderedIds_isNoOp() {
        List<Item> saved = new ArrayList<>();

        ReorderSupport.reorder(null,
                id -> Optional.of(new Item(id)),
                (item, i) -> { },
                saved::add);

        assertTrue(saved.isEmpty());
    }
}
