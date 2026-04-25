package com.loremind.domain.generationcontext;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires pour PageContext (Value Object : focus IA sur une page).
 */
class PageContextTest {

    @Test
    void constructor_preservesAllFields() {
        PageContext ctx = new PageContext(
                "Thorin",
                "PNJ",
                List.of("histoire", "apparence", "motto"),
                Map.of("histoire", "Nee sous une etoile rouge"));

        assertEquals("Thorin", ctx.title());
        assertEquals("PNJ", ctx.templateName());
        assertEquals(3, ctx.templateFields().size());
        assertEquals(1, ctx.values().size());
    }

    @Test
    void emptyValues_areAllowed() {
        // Page vierge : template defini mais aucun champ rempli (cas generation ex-nihilo).
        PageContext ctx = new PageContext(
                "Nouveau PNJ",
                "PNJ",
                List.of("histoire", "apparence"),
                Map.of());

        assertTrue(ctx.values().isEmpty());
        assertEquals(2, ctx.templateFields().size());
    }
}
