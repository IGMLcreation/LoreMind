package com.loremind.infrastructure.transfer.pdf;

import com.loremind.infrastructure.transfer.pdf.PdfExportOptions.LoreScope;
import com.loremind.infrastructure.transfer.pdf.PdfExportOptions.PageFormat;
import com.loremind.infrastructure.transfer.pdf.PdfExportOptions.Section;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

/** Options d'export lues depuis la requête, et libellés de stats Foundry traduits. */
class PdfExportOptionsTest {

    @Test
    void parse_defaultsToTheFullGmBooklet() {
        PdfExportOptions o = PdfExportOptions.parse(null, null, null, null, null);
        assertEquals(PdfExportOptions.defaults(), o);
        assertTrue(o.gmContent());
        assertEquals(PageFormat.A4, o.format());
        assertEquals(EnumSet.allOf(Section.class), o.sections());
        assertEquals(LoreScope.ALL, o.loreScope());
    }

    @Test
    void parse_readsEveryOption_andIgnoresUnknownValues() {
        PdfExportOptions o = PdfExportOptions.parse("player", "a5", true, "narrative, lore,bogus", "linked");
        assertFalse(o.gmContent());
        assertEquals(PageFormat.A5, o.format());
        assertTrue(o.printFriendly());
        assertEquals(EnumSet.of(Section.NARRATIVE, Section.LORE), o.sections());
        assertEquals(LoreScope.LINKED, o.loreScope());

        PdfExportOptions bad = PdfExportOptions.parse("gm", "tabloid", false, "", "nope");
        assertEquals(PageFormat.A4, bad.format());
        assertEquals(LoreScope.ALL, bad.loreScope());
        assertEquals(EnumSet.allOf(Section.class), bad.sections(), "aucune partie reconnue = toutes");
    }

    @Test
    void formats_haveConsistentContentWidths() {
        assertEquals(17.6, PageFormat.A4.contentWidthCm(), 0.01);
        assertEquals(12.2, PageFormat.A5.contentWidthCm(), 0.01);
        assertTrue(PageFormat.LETTER.contentWidthCm() > PageFormat.A4.contentWidthCm());
    }

    @Test
    void foundryStatKeys_areTranslated() {
        assertEquals("PV", PdfExportService.humanizeStatKey("attributes.hp.value"));
        assertEquals("PV max", PdfExportService.humanizeStatKey("attributes.hp.max"));
        assertEquals("CA", PdfExportService.humanizeStatKey("attributes.ac.value"));
        assertEquals("FOR", PdfExportService.humanizeStatKey("abilities.str.value"));
        assertEquals("FP", PdfExportService.humanizeStatKey("system.details.cr"));
        assertEquals("Déplacement marche", PdfExportService.humanizeStatKey("attributes.movement.walk"));
        assertEquals("Type de créature", PdfExportService.humanizeStatKey("details.creatureType"));
        assertEquals("Spell slots", PdfExportService.humanizeStatKey("spellSlots"), "terme inconnu : humanisé");
    }
}
