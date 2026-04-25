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
    void constructor_preservesAllFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("themes", "trahison");
        fields.put("stakes", "la survie du royaume");

        NarrativeEntityContext ctx = new NarrativeEntityContext("arc", "Acte I", fields);

        assertEquals("arc", ctx.entityType());
        assertEquals("Acte I", ctx.title());
        assertEquals(2, ctx.fields().size());
        assertEquals("trahison", ctx.fields().get("themes"));
    }

    @Test
    void fieldsOrder_isPreserved_whenUsingLinkedHashMap() {
        // L'ordre des champs est significatif : le prompt doit etre lisible.
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("location", "Taverne");
        fields.put("timing", "Soir");
        fields.put("atmosphere", "fumee");

        NarrativeEntityContext ctx = new NarrativeEntityContext("scene", "L'auberge", fields);

        assertEquals("[location, timing, atmosphere]", ctx.fields().keySet().toString());
    }

    @Test
    void twoContexts_differingOnEntityType_areNotEqual() {
        NarrativeEntityContext a = new NarrativeEntityContext("arc", "X", Map.of());
        NarrativeEntityContext b = new NarrativeEntityContext("scene", "X", Map.of());
        assertNotEquals(a, b);
    }
}
