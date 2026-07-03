package com.loremind.domain.campaigncontext;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires du domaine pour Scene.
 * Scene est la plus riche en champs : on valide les collections @Builder.Default
 * (relatedPageIds, illustrationImageIds, branches, battlemaps) et la preservation
 * de l'ensemble des champs narratifs.
 */
class SceneTest {

    @Test
    void builder_initializesAllCollectionsToEmptyList_whenNotSet() {
        Scene scene = Scene.builder()
                .id("sc-1")
                .name("L'auberge")
                .chapterId("ch-1")
                .order(0)
                .build();

        assertNotNull(scene.getRelatedPageIds());
        assertNotNull(scene.getIllustrationImageIds());
        assertNotNull(scene.getBranches(), "branches ne doit jamais etre null — une scene sans branche est une feuille");
        assertTrue(scene.getRelatedPageIds().isEmpty());
        assertTrue(scene.getIllustrationImageIds().isEmpty());
        assertTrue(scene.getBranches().isEmpty());
        // Battlemaps : aucune carte par defaut (liste vide, jamais null).
        assertNotNull(scene.getBattlemaps());
        assertTrue(scene.getBattlemaps().isEmpty());
    }

    @Test
    void builder_preservesBattlemaps_whenProvided() {
        Scene scene = Scene.builder()
                .battlemaps(List.of(
                        new SceneBattlemap("Jour", "42", "43"),
                        new SceneBattlemap("Nuit", "44", null)))
                .build();

        assertEquals(2, scene.getBattlemaps().size());
        assertEquals("Jour", scene.getBattlemaps().get(0).label());
        assertEquals("42", scene.getBattlemaps().get(0).mediaFileId());
        assertEquals("43", scene.getBattlemaps().get(0).dataFileId());
        assertNull(scene.getBattlemaps().get(1).dataFileId());
        // Libelle absent normalise en chaine vide par le constructeur canonique.
        assertEquals("", new SceneBattlemap(null, "1", null).label());
    }

    @Test
    void builder_preservesAllNarrativeFields() {
        Scene scene = Scene.builder()
                .location("Taverne du Dragon d'Or")
                .timing("Soir, a la tombee de la nuit")
                .atmosphere("fumee, rires rauques, odeur de biere")
                .playerNarration("Vous poussez la porte et entrez dans...")
                .gmSecretNotes("l'aubergiste est un espion de la guilde")
                .choicesConsequences("si les PJ parlent fort, ils attirent les gardes")
                .combatDifficulty("facile (CR 1/2)")
                .enemies("3 brigands armes de gourdins")
                .build();

        assertEquals("Taverne du Dragon d'Or", scene.getLocation());
        assertEquals("Soir, a la tombee de la nuit", scene.getTiming());
        assertEquals("fumee, rires rauques, odeur de biere", scene.getAtmosphere());
        assertEquals("Vous poussez la porte et entrez dans...", scene.getPlayerNarration());
        assertEquals("l'aubergiste est un espion de la guilde", scene.getGmSecretNotes());
        assertEquals("si les PJ parlent fort, ils attirent les gardes", scene.getChoicesConsequences());
        assertEquals("facile (CR 1/2)", scene.getCombatDifficulty());
        assertEquals("3 brigands armes de gourdins", scene.getEnemies());
    }

    @Test
    void builder_preservesBranches_whenProvided() {
        SceneBranch b1 = SceneBranch.of("fuite", "sc-2");
        SceneBranch b2 = SceneBranch.of("combat", "sc-3");

        Scene scene = Scene.builder()
                .branches(List.of(b1, b2))
                .build();

        assertEquals(2, scene.getBranches().size());
        assertEquals("fuite", scene.getBranches().get(0).label());
        assertEquals("sc-3", scene.getBranches().get(1).targetSceneId());
    }
}
