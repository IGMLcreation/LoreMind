package com.loremind.domain.lorecontext;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires du domaine pour Page.
 * Valide :
 *  - la methode metier {@code hasTemplate()} (null / blank / valide),
 *  - la preservation des deux maps de valeurs (TEXT vs IMAGE),
 *  - la preservation des metadonnees editoriales (notes, tags, relatedPageIds).
 */
class PageTest {

    @Test
    void hasTemplate_returnsFalse_whenTemplateIdIsNull() {
        Page page = Page.builder().templateId(null).build();
        assertFalse(page.hasTemplate());
    }

    @Test
    void hasTemplate_returnsFalse_whenTemplateIdIsBlank() {
        Page page = Page.builder().templateId("   ").build();
        assertFalse(page.hasTemplate());
    }

    @Test
    void hasTemplate_returnsTrue_whenTemplateIdIsPresent() {
        Page page = Page.builder().templateId("tpl-1").build();
        assertTrue(page.hasTemplate());
    }

    @Test
    void builder_preservesTextAndImageValuesSeparately() {
        Page page = Page.builder()
                .values(Map.of("histoire", "Nee sous une etoile rouge...", "motto", "Jamais genou en terre"))
                .imageValues(Map.of(
                        "portraits", List.of("img-1", "img-2"),
                        "cartes", List.of("img-3")
                ))
                .build();

        assertEquals(2, page.getValues().size());
        assertEquals("Nee sous une etoile rouge...", page.getValues().get("histoire"));
        assertEquals(2, page.getImageValues().size());
        assertEquals(2, page.getImageValues().get("portraits").size());
        assertEquals("img-3", page.getImageValues().get("cartes").get(0));
    }

    @Test
    void builder_preservesEditorialMetadata() {
        Page page = Page.builder()
                .notes("secret MJ : trahison a venir")
                .tags(List.of("pnj", "faction-ombre"))
                .relatedPageIds(List.of("page-a", "page-b"))
                .build();

        assertEquals("secret MJ : trahison a venir", page.getNotes());
        assertEquals(2, page.getTags().size());
        assertTrue(page.getTags().contains("faction-ombre"));
        assertEquals(2, page.getRelatedPageIds().size());
    }
}
