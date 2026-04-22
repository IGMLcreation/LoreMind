package com.loremind.domain.campaigncontext;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires du domaine pour Campaign.
 * Valide la seule methode metier ({@code isLinkedToLore}) et les invariants
 * de construction du builder Lombok (defaults).
 */
class CampaignTest {

    // --- isLinkedToLore : regle metier du Bounded Context --------------------

    @Test
    void isLinkedToLore_returnsFalse_whenLoreIdIsNull() {
        Campaign campaign = Campaign.builder()
                .id("c1")
                .name("One-shot")
                .loreId(null)
                .build();

        assertFalse(campaign.isLinkedToLore());
    }

    @Test
    void isLinkedToLore_returnsFalse_whenLoreIdIsEmpty() {
        Campaign campaign = Campaign.builder().loreId("").build();
        assertFalse(campaign.isLinkedToLore());
    }

    @Test
    void isLinkedToLore_returnsFalse_whenLoreIdIsBlank() {
        // Blank = espaces / tabulations uniquement → ne doit pas compter comme un lien valide.
        Campaign campaign = Campaign.builder().loreId("   ").build();
        assertFalse(campaign.isLinkedToLore());
    }

    @Test
    void isLinkedToLore_returnsTrue_whenLoreIdIsPresent() {
        Campaign campaign = Campaign.builder().loreId("lore-42").build();
        assertTrue(campaign.isLinkedToLore());
    }

    // --- Invariants de construction -----------------------------------------

    @Test
    void builder_preservesAllScalarFields() {
        Campaign campaign = Campaign.builder()
                .id("c-1")
                .name("Les Ombres d'Ithoril")
                .description("Une campagne de faction dans un royaume en decadence.")
                .loreId("lore-1")
                .arcsCount(3)
                .build();

        assertEquals("c-1", campaign.getId());
        assertEquals("Les Ombres d'Ithoril", campaign.getName());
        assertEquals("Une campagne de faction dans un royaume en decadence.", campaign.getDescription());
        assertEquals("lore-1", campaign.getLoreId());
        assertEquals(3, campaign.getArcsCount());
    }

    @Test
    void builder_allowsNoArgs_forFlexibility() {
        // Un Campaign peut etre cree sans champ rempli (cas pre-hydratation depuis DB).
        Campaign campaign = Campaign.builder().build();
        assertNotNull(campaign);
        assertFalse(campaign.isLinkedToLore());
    }
}
