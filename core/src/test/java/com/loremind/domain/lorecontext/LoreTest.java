package com.loremind.domain.lorecontext;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests unitaires du domaine pour Lore.
 * Entite racine du Bounded Context Lore — POJO pur, on verifie juste la
 * preservation des champs par le builder.
 */
class LoreTest {

    @Test
    void builder_preservesAllFields() {
        Lore lore = Lore.builder()
                .id("lore-1")
                .name("Ithoril")
                .description("Royaume en decadence apres la guerre des eclipses.")
                .nodeCount(12)
                .pageCount(57)
                .build();

        assertEquals("lore-1", lore.getId());
        assertEquals("Ithoril", lore.getName());
        assertEquals("Royaume en decadence apres la guerre des eclipses.", lore.getDescription());
        assertEquals(12, lore.getNodeCount());
        assertEquals(57, lore.getPageCount());
    }

    @Test
    void builder_allowsNoArgs() {
        Lore lore = Lore.builder().build();
        assertNotNull(lore);
        assertEquals(0, lore.getNodeCount());
        assertEquals(0, lore.getPageCount());
    }
}
