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
    void builder_preservesAllFields() {
        PageContext ctx = PageContext.builder()
                .title("Thorin")
                .templateName("PNJ")
                .templateFields(List.of("histoire", "apparence", "motto"))
                .values(Map.of("histoire", "Nee sous une etoile rouge"))
                .build();

        assertEquals("Thorin", ctx.getTitle());
        assertEquals("PNJ", ctx.getTemplateName());
        assertEquals(3, ctx.getTemplateFields().size());
        assertEquals(1, ctx.getValues().size());
    }

    @Test
    void emptyValues_areAllowed() {
        // Page vierge : template defini mais aucun champ rempli (cas generation ex-nihilo).
        PageContext ctx = PageContext.builder()
                .title("Nouveau PNJ")
                .templateName("PNJ")
                .templateFields(List.of("histoire", "apparence"))
                .values(Map.of())
                .build();

        assertTrue(ctx.getValues().isEmpty());
        assertEquals(2, ctx.getTemplateFields().size());
    }
}
