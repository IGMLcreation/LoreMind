package com.loremind.domain.campaigncontext.bestiary;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Fiche « rôle dans l'histoire » d'un PNJ : nettoyage et rôle moteur de l'intrigue. */
class NpcStoryTest {

    @Test
    void normalized_cleansRoleAndTexts() {
        NpcStory s = new NpcStory(" ally ", "  Venger son frère ", " ", "x".repeat(400)).normalized();
        assertEquals("ALLY", s.role());
        assertEquals("Venger son frère", s.motivation());
        assertNull(s.knowledge());
        assertEquals(300, s.mannerism().length());
        assertNull(new NpcStory("BOSS", null, null, null).normalized().role(), "rôle inconnu écarté");
    }

    @Test
    void contentAndPlotRole() {
        assertFalse(new NpcStory(" ", "", null, null).hasContent());
        assertTrue(new NpcStory(null, null, null, "Se gratte la barbe").hasContent());
        assertTrue(new NpcStory("OPPONENT", null, null, null).drivesThePlot());
        assertTrue(new NpcStory("ally", null, null, null).drivesThePlot());
        assertFalse(new NpcStory("EXTRA", null, null, null).drivesThePlot());
        assertFalse(new NpcStory(null, "x", null, null).drivesThePlot());
    }
}
