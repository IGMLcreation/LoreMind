package com.loremind.domain.generationcontext;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Tests unitaires pour le record ChatUsage.
 * Verifie l'immuabilite (acces via accesseurs generes) et l'egalite
 * structurelle (equals/hashCode generes par le record).
 */
class ChatUsageTest {

    @Test
    void accessors_exposeAllComponents() {
        ChatUsage usage = new ChatUsage(1200, 3400, 150, 8192);
        assertEquals(1200, usage.system());
        assertEquals(3400, usage.history());
        assertEquals(150, usage.current());
        assertEquals(8192, usage.max());
    }

    @Test
    void twoUsages_withSameComponents_areEqual() {
        ChatUsage a = new ChatUsage(100, 200, 50, 4096);
        ChatUsage b = new ChatUsage(100, 200, 50, 4096);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void twoUsages_differingOnAnyComponent_areNotEqual() {
        ChatUsage base = new ChatUsage(100, 200, 50, 4096);
        assertNotEquals(base, new ChatUsage(101, 200, 50, 4096));
        assertNotEquals(base, new ChatUsage(100, 201, 50, 4096));
        assertNotEquals(base, new ChatUsage(100, 200, 51, 4096));
        assertNotEquals(base, new ChatUsage(100, 200, 50, 4097));
    }
}
