package com.loremind.domain.lorecontext;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests unitaires du domaine pour LoreNode.
 * Verifie la preservation des champs et l'absence de parent (racine de l'arbre).
 */
class LoreNodeTest {

    @Test
    void builder_preservesAllFields() {
        LoreNode node = LoreNode.builder()
                .id("n-1")
                .name("Personnages")
                .icon("users")
                .parentId("n-root")
                .loreId("lore-1")
                .build();

        assertEquals("n-1", node.getId());
        assertEquals("Personnages", node.getName());
        assertEquals("users", node.getIcon());
        assertEquals("n-root", node.getParentId());
        assertEquals("lore-1", node.getLoreId());
    }

    @Test
    void parentId_isNull_forRootNodes() {
        // Un node racine a parentId == null : invariant de l'arborescence.
        LoreNode root = LoreNode.builder()
                .id("n-root")
                .name("Racine")
                .loreId("lore-1")
                .build();

        assertNull(root.getParentId());
    }
}
