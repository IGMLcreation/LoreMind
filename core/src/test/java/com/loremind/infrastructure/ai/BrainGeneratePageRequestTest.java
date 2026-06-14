package com.loremind.infrastructure.ai;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests unitaires des DTOs wire de l'Adapter IA :
 *  - BrainGeneratePageRequest (record envoye au Brain) ;
 *  - BrainGeneratePageResponse (@Data/@NoArgsConstructor recu du Brain).
 * On instancie et on appelle les accesseurs pour couvrir le code genere.
 */
class BrainGeneratePageRequestTest {

    // --- BrainGeneratePageRequest -------------------------------------------

    @Test
    void request_accesseursExposentLesChamps() {
        BrainGeneratePageRequest req = new BrainGeneratePageRequest(
                "Aetheria",
                "Un monde de cendres",
                "PNJ",
                "Fiche personnage",
                List.of("histoire", "motto"),
                "Garde rouge"
        );

        assertEquals("Aetheria", req.loreName());
        assertEquals("Un monde de cendres", req.loreDescription());
        assertEquals("PNJ", req.folderName());
        assertEquals("Fiche personnage", req.templateName());
        assertEquals(List.of("histoire", "motto"), req.templateFields());
        assertEquals("Garde rouge", req.pageTitle());
    }

    @Test
    void request_egaliteStructurelleEtToString() {
        BrainGeneratePageRequest a = new BrainGeneratePageRequest(
                "n", "d", "f", "t", List.of("x"), "p");
        BrainGeneratePageRequest b = new BrainGeneratePageRequest(
                "n", "d", "f", "t", List.of("x"), "p");
        BrainGeneratePageRequest c = new BrainGeneratePageRequest(
                "AUTRE", "d", "f", "t", List.of("x"), "p");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        // toString genere : on verifie juste qu'il est non vide et contient un champ
        org.junit.jupiter.api.Assertions.assertTrue(a.toString().contains("n"));
    }

    // --- BrainGeneratePageResponse ------------------------------------------

    @Test
    void response_setterEtGetterValues() {
        BrainGeneratePageResponse resp = new BrainGeneratePageResponse();
        assertNull(resp.getValues());

        Map<String, String> values = Map.of("histoire", "Nee sous une etoile rouge");
        resp.setValues(values);

        assertEquals(values, resp.getValues());
        assertEquals("Nee sous une etoile rouge", resp.getValues().get("histoire"));
    }

    @Test
    void response_egaliteEtToStringGeneresParLombok() {
        BrainGeneratePageResponse a = new BrainGeneratePageResponse();
        a.setValues(Map.of("f", "v"));
        BrainGeneratePageResponse b = new BrainGeneratePageResponse();
        b.setValues(Map.of("f", "v"));
        BrainGeneratePageResponse c = new BrainGeneratePageResponse();
        c.setValues(Map.of("f", "AUTRE"));

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        org.junit.jupiter.api.Assertions.assertTrue(a.toString().contains("values"));
    }
}
