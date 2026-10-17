package com.loremind.domain.campaigncontext;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Fiche « Publication » : nettoyage de saisie et regroupements utilisés par l'export PDF. */
class CampaignPublicationTest {

    @Test
    void normalized_trimsBlanksAndCapsLengths() {
        CampaignPublication p = new CampaignPublication("  Sous-titre  ", "   ", null, null, null, null,
                "x".repeat(25_000), null, null, null, null, null, "v".repeat(300), "");

        CampaignPublication n = p.normalized();

        assertEquals("Sous-titre", n.subtitle());
        assertNull(n.author(), "un texte blanc devient null");
        assertNull(n.coverImageId());
        assertEquals(20_000, n.pitch().length(), "texte long borné");
        assertEquals(200, n.version().length(), "texte court borné");
    }

    @Test
    void emptiness_andGroups() {
        assertFalse(CampaignPublication.empty().hasContent());
        assertFalse(new CampaignPublication(" ", "", null, null, null, null, null, null, null, null,
                null, null, null, null).hasContent(), "que des blancs = vide");

        CampaignPublication tech = new CampaignPublication(null, null, null, null, "3 à 5", null,
                null, null, null, null, null, null, null, null);
        assertTrue(tech.hasContent());
        assertTrue(tech.hasTechnicalSheet());
        assertFalse(tech.hasCredits());

        CampaignPublication credits = new CampaignPublication(null, null, null, null, null, null,
                null, null, null, null, null, "CC BY 4.0", null, null);
        assertTrue(credits.hasCredits());
        assertFalse(credits.hasTechnicalSheet());
    }
}
