package com.loremind.domain.campaigncontext;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires du domaine pour Chapter.
 * Verifie l'initialisation des collections via @Builder.Default et la
 * preservation des champs narratifs enrichis.
 */
class ChapterTest {

    @Test
    void builder_initializesAllCollectionsToEmptyList_whenNotSet() {
        Chapter chapter = Chapter.builder()
                .id("ch-1")
                .name("L'arrivee")
                .arcId("arc-1")
                .order(0)
                .build();

        assertNotNull(chapter.getRelatedPageIds());
        assertNotNull(chapter.getIllustrationImageIds());
        assertNotNull(chapter.getMapImageIds());
        assertTrue(chapter.getRelatedPageIds().isEmpty());
        assertTrue(chapter.getIllustrationImageIds().isEmpty());
        assertTrue(chapter.getMapImageIds().isEmpty());
    }

    @Test
    void builder_preservesProvidedCollections() {
        Chapter chapter = Chapter.builder()
                .relatedPageIds(List.of("page-x"))
                .illustrationImageIds(List.of("img-1", "img-2"))
                .mapImageIds(List.of("map-dungeon"))
                .build();

        assertEquals(1, chapter.getRelatedPageIds().size());
        assertEquals(2, chapter.getIllustrationImageIds().size());
        assertEquals(1, chapter.getMapImageIds().size());
    }

    @Test
    void builder_preservesNarrativeEnrichmentFields() {
        Chapter chapter = Chapter.builder()
                .gmNotes("les joueurs doivent decouvrir la trahison")
                .playerObjectives("trouver l'indice dans la bibliotheque")
                .narrativeStakes("si echec, l'allie meurt")
                .build();

        assertEquals("les joueurs doivent decouvrir la trahison", chapter.getGmNotes());
        assertEquals("trouver l'indice dans la bibliotheque", chapter.getPlayerObjectives());
        assertEquals("si echec, l'allie meurt", chapter.getNarrativeStakes());
    }
}
