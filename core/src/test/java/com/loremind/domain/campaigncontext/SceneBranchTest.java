package com.loremind.domain.campaigncontext;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests unitaires pour SceneBranch (Value Object).
 * Verifie :
 *  - l'immuabilite (record : aucun setter, constructeur canonique uniquement),
 *  - l'egalite structurelle generee par record (equals/hashCode sur tous les
 *    champs) — deux branches aux memes champs sont strictement egales,
 *  - le support du champ optionnel {@code condition}.
 */
class SceneBranchTest {

    @Test
    void constructor_exposesAllFields() {
        SceneBranch branch = new SceneBranch(
                "Si les joueurs attaquent le garde",
                "sc-combat",
                "initiative > 15");

        assertEquals("Si les joueurs attaquent le garde", branch.label());
        assertEquals("sc-combat", branch.targetSceneId());
        assertEquals("initiative > 15", branch.condition());
    }

    @Test
    void condition_isOptional() {
        SceneBranch branch = SceneBranch.of("sortie par la porte", "sc-corridor");

        assertNull(branch.condition());
    }

    @Test
    void twoBranches_withSameFields_areEqual() {
        SceneBranch a = new SceneBranch("fuite", "sc-2", null);
        SceneBranch b = new SceneBranch("fuite", "sc-2", null);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void twoBranches_differingOnTargetSceneId_areNotEqual() {
        SceneBranch a = SceneBranch.of("X", "sc-1");
        SceneBranch b = SceneBranch.of("X", "sc-2");

        assertNotEquals(a, b);
    }

    @Test
    void twoBranches_differingOnCondition_areNotEqual() {
        SceneBranch a = new SceneBranch("X", "sc-1", "A");
        SceneBranch b = new SceneBranch("X", "sc-1", "B");

        assertNotEquals(a, b);
    }
}
