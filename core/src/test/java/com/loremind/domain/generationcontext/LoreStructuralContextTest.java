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
 * Valide le @Singular de Lombok sur {@code tags} (alimentation incrementale via
 * {@code tag(...)} vs initialisation groupee via {@code tags(...)}).
 */
class LoreStructuralContextTest {

    @Test
    void builder_preservesFoldersAndTags() {
        PageSummary pnj = PageSummary.builder()
                .title("Thorin")
                .templateName("PNJ")
                .values(Map.of("histoire", "Nee sous une etoile rouge"))
                .tags(List.of("pnj", "allie"))
                .relatedPageTitles(List.of("Taverne du Dragon d'Or"))
                .build();

        LoreStructuralContext ctx = LoreStructuralContext.builder()
                .loreName("Ithoril")
                .loreDescription("Royaume sombre")
                .folders(Map.of("PNJ", List.of(pnj)))
                .tag("royaume")
                .tag("dark-fantasy")
                .build();

        assertEquals("Ithoril", ctx.getLoreName());
        assertEquals(1, ctx.getFolders().size());
        assertEquals(1, ctx.getFolders().get("PNJ").size());
        assertEquals(2, ctx.getTags().size(), "@Singular doit accumuler les appels tag()");
        assertTrue(ctx.getTags().contains("royaume"));
        assertTrue(ctx.getTags().contains("dark-fantasy"));
    }

    @Test
    void emptyFolders_areAllowed() {
        // Dossier vide : legitime (ex: dossier "Lieux" cree mais pas encore peuple).
        LoreStructuralContext ctx = LoreStructuralContext.builder()
                .loreName("Vide")
                .loreDescription("")
                .folders(Map.of("Lieux", List.of()))
                .build();

        assertNotNull(ctx.getFolders().get("Lieux"));
        assertTrue(ctx.getFolders().get("Lieux").isEmpty());
    }

    // --- PageSummary --------------------------------------------------------

    @Test
    void pageSummary_preservesAllFields() {
        PageSummary ps = PageSummary.builder()
                .title("Le Donjon du Chaos")
                .templateName("Lieu")
                .values(Map.of("histoire", "Bati il y a 1000 ans..."))
                .tags(List.of("donjon", "ancien"))
                .relatedPageTitles(List.of("Thorin", "Garde royale"))
                .build();

        assertEquals("Le Donjon du Chaos", ps.getTitle());
        assertEquals("Lieu", ps.getTemplateName());
        assertEquals(1, ps.getValues().size());
        assertEquals(2, ps.getTags().size());
        assertEquals(2, ps.getRelatedPageTitles().size());
    }
}
