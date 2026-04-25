package com.loremind.domain.generationcontext;

import com.loremind.domain.generationcontext.LoreStructuralContext.PageSummary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires pour LoreStructuralContext et son type imbrique PageSummary.
 * Records purs : aucune dependance technique.
 */
class LoreStructuralContextTest {

    @Test
    void constructor_preservesFoldersAndTags() {
        PageSummary pnj = new PageSummary(
                "Thorin",
                "PNJ",
                Map.of("histoire", "Nee sous une etoile rouge"),
                List.of("pnj", "allie"),
                List.of("Taverne du Dragon d'Or"));

        LoreStructuralContext ctx = new LoreStructuralContext(
                "Ithoril",
                "Royaume sombre",
                Map.of("PNJ", List.of(pnj)),
                List.of("royaume", "dark-fantasy"));

        assertEquals("Ithoril", ctx.loreName());
        assertEquals(1, ctx.folders().size());
        assertEquals(1, ctx.folders().get("PNJ").size());
        assertEquals(2, ctx.tags().size());
        assertTrue(ctx.tags().contains("royaume"));
        assertTrue(ctx.tags().contains("dark-fantasy"));
    }

    @Test
    void emptyFolders_areAllowed() {
        // Dossier vide : legitime (ex: dossier "Lieux" cree mais pas encore peuple).
        LoreStructuralContext ctx = new LoreStructuralContext(
                "Vide",
                "",
                Map.of("Lieux", List.of()),
                List.of());

        assertNotNull(ctx.folders().get("Lieux"));
        assertTrue(ctx.folders().get("Lieux").isEmpty());
    }

    // --- PageSummary --------------------------------------------------------

    @Test
    void pageSummary_preservesAllFields() {
        PageSummary ps = new PageSummary(
                "Le Donjon du Chaos",
                "Lieu",
                Map.of("histoire", "Bati il y a 1000 ans..."),
                List.of("donjon", "ancien"),
                List.of("Thorin", "Garde royale"));

        assertEquals("Le Donjon du Chaos", ps.title());
        assertEquals("Lieu", ps.templateName());
        assertEquals(1, ps.values().size());
        assertEquals(2, ps.tags().size());
        assertEquals(2, ps.relatedPageTitles().size());
    }
}
