package com.loremind.domain.generationcontext;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Tests unitaires pour le record GenerationResult.
 * Structure triviale (map name -> value) — on verifie juste l'acces et
 * l'egalite structurelle.
 */
class GenerationResultTest {

    @Test
    void accessor_exposesValuesMap() {
        GenerationResult result = new GenerationResult(Map.of(
                "histoire", "Nee sous une etoile rouge...",
                "motto", "Jamais genou en terre"
        ));

        assertEquals(2, result.values().size());
        assertEquals("Jamais genou en terre", result.values().get("motto"));
    }

    @Test
    void twoResults_withSameMap_areEqual() {
        GenerationResult a = new GenerationResult(Map.of("f", "v"));
        GenerationResult b = new GenerationResult(Map.of("f", "v"));
        assertEquals(a, b);
    }

    @Test
    void twoResults_withDifferentMaps_areNotEqual() {
        GenerationResult a = new GenerationResult(Map.of("f", "v1"));
        GenerationResult b = new GenerationResult(Map.of("f", "v2"));
        assertNotEquals(a, b);
    }
}
