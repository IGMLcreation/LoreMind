package com.loremind.domain.generationcontext;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Tests unitaires pour NarrativeEntityContext.
 * Trois types attendus : "arc", "chapter", "scene" — mais le domaine ne
 * restreint pas la chaine (validation cote application layer).
 */
class NarrativeEntityContextTest {

    @Test
    void builder_preservesAllFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("themes", "trahison");
        fields.put("stakes", "la survie du royaume");

        NarrativeEntityContext ctx = NarrativeEntityContext.builder()
                .entityType("arc")
                .title("Acte I")
                .fields(fields)
                .build();

        assertEquals("arc", ctx.getEntityType());
        assertEquals("Acte I", ctx.getTitle());
        assertEquals(2, ctx.getFields().size());
        assertEquals("trahison", ctx.getFields().get("themes"));
    }

    @Test
    void fieldsOrder_isPreserved_whenUsingLinkedHashMap() {
        // L'ordre des champs est significatif : le prompt doit etre lisible.
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("location", "Taverne");
        fields.put("timing", "Soir");
        fields.put("atmosphere", "fumee");

        NarrativeEntityContext ctx = NarrativeEntityContext.builder()
                .entityType("scene")
                .title("L'auberge")
                .fields(fields)
                .build();

        assertEquals("[location, timing, atmosphere]", ctx.getFields().keySet().toString());
    }

    @Test
    void twoContexts_differingOnEntityType_areNotEqual() {
        NarrativeEntityContext a = NarrativeEntityContext.builder().entityType("arc").title("X").build();
        NarrativeEntityContext b = NarrativeEntityContext.builder().entityType("scene").title("X").build();
        assertNotEquals(a, b);
    }
}
