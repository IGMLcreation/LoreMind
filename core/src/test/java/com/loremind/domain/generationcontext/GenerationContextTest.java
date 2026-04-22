package com.loremind.domain.generationcontext;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Tests unitaires pour GenerationContext (Value Object pour la generation one-shot).
 * Verifie la construction via builder et l'egalite structurelle.
 */
class GenerationContextTest {

    @Test
    void builder_preservesAllFields() {
        GenerationContext ctx = GenerationContext.builder()
                .loreName("Ithoril")
                .loreDescription("Royaume sombre")
                .folderName("PNJ")
                .templateName("Fiche PNJ")
                .templateFields(List.of("histoire", "motto", "apparence"))
                .pageTitle("Thorin")
                .build();

        assertEquals("Ithoril", ctx.getLoreName());
        assertEquals("PNJ", ctx.getFolderName());
        assertEquals("Fiche PNJ", ctx.getTemplateName());
        assertEquals(3, ctx.getTemplateFields().size());
        assertEquals("Thorin", ctx.getPageTitle());
    }

    @Test
    void twoContexts_withSameFields_areEqual() {
        GenerationContext a = GenerationContext.builder()
                .loreName("X").pageTitle("A").templateFields(List.of("f1")).build();
        GenerationContext b = GenerationContext.builder()
                .loreName("X").pageTitle("A").templateFields(List.of("f1")).build();
        assertEquals(a, b);
    }

    @Test
    void twoContexts_differingOnPageTitle_areNotEqual() {
        GenerationContext a = GenerationContext.builder().pageTitle("A").build();
        GenerationContext b = GenerationContext.builder().pageTitle("B").build();
        assertNotEquals(a, b);
    }
}
