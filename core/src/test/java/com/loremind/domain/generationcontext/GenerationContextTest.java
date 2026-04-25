package com.loremind.domain.generationcontext;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Tests unitaires pour GenerationContext (Value Object pour la generation one-shot).
 * Verifie la construction et l'egalite structurelle (record).
 */
class GenerationContextTest {

    @Test
    void constructor_preservesAllFields() {
        GenerationContext ctx = new GenerationContext(
                "Ithoril",
                "Royaume sombre",
                "PNJ",
                "Fiche PNJ",
                List.of("histoire", "motto", "apparence"),
                "Thorin");

        assertEquals("Ithoril", ctx.loreName());
        assertEquals("PNJ", ctx.folderName());
        assertEquals("Fiche PNJ", ctx.templateName());
        assertEquals(3, ctx.templateFields().size());
        assertEquals("Thorin", ctx.pageTitle());
    }

    @Test
    void twoContexts_withSameFields_areEqual() {
        GenerationContext a = new GenerationContext("X", null, null, null, List.of("f1"), "A");
        GenerationContext b = new GenerationContext("X", null, null, null, List.of("f1"), "A");
        assertEquals(a, b);
    }

    @Test
    void twoContexts_differingOnPageTitle_areNotEqual() {
        GenerationContext a = new GenerationContext(null, null, null, null, null, "A");
        GenerationContext b = new GenerationContext(null, null, null, null, null, "B");
        assertNotEquals(a, b);
    }
}
