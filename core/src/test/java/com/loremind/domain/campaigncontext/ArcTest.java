package com.loremind.domain.campaigncontext;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires du domaine pour Arc.
 * Focus sur les @Builder.Default des collections : la moindre omission
 * renverrait {@code null} et propagerait des NPE dans toute la pile.
 */
class ArcTest {

    @Test
    void builder_initializesAllCollectionsToEmptyList_whenNotSet() {
        Arc arc = Arc.builder()
                .id("arc-1")
                .name("Acte I")
                .campaignId("camp-1")
                .order(0)
                .build();

        assertNotNull(arc.getRelatedPageIds(), "relatedPageIds ne doit jamais etre null");
        assertNotNull(arc.getIllustrationImageIds(), "illustrationImageIds ne doit jamais etre null");
        assertTrue(arc.getRelatedPageIds().isEmpty());
        assertTrue(arc.getIllustrationImageIds().isEmpty());
    }

    @Test
    void builder_preservesProvidedCollections() {
        Arc arc = Arc.builder()
                .relatedPageIds(List.of("page-a", "page-b"))
                .illustrationImageIds(List.of("img-1"))
                .build();

        assertEquals(2, arc.getRelatedPageIds().size());
        assertEquals(1, arc.getIllustrationImageIds().size());
    }

    @Test
    void builder_preservesNarrativeEnrichmentFields() {
        Arc arc = Arc.builder()
                .themes("trahison, vengeance")
                .stakes("la survie du royaume")
                .gmNotes("secret : le roi est un imposteur")
                .rewards("artefact ancien")
                .resolution("couronnement du legitime heritier")
                .build();

        assertEquals("trahison, vengeance", arc.getThemes());
        assertEquals("la survie du royaume", arc.getStakes());
        assertEquals("secret : le roi est un imposteur", arc.getGmNotes());
        assertEquals("artefact ancien", arc.getRewards());
        assertEquals("couronnement du legitime heritier", arc.getResolution());
    }
}
