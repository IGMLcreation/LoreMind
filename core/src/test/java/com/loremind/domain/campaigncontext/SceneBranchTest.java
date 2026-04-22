package com.loremind.domain.campaigncontext;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests unitaires pour SceneBranch (Value Object).
 * Verifie :
 *  - l'immuabilite (pas de setters : seul le builder permet la construction),
 *  - l'egalite structurelle generee par @Value (equals/hashCode sur tous les
 *    champs) — deux branches aux memes champs sont strictement egales,
 *  - le support du champ optionnel {@code condition}.
 */
class SceneBranchTest {

    @Test
    void builder_exposesAllFields() {
        SceneBranch branch = SceneBranch.builder()
                .label("Si les joueurs attaquent le garde")
                .targetSceneId("sc-combat")
                .condition("initiative > 15")
                .build();

        assertEquals("Si les joueurs attaquent le garde", branch.getLabel());
        assertEquals("sc-combat", branch.getTargetSceneId());
        assertEquals("initiative > 15", branch.getCondition());
    }

    @Test
    void condition_isOptional() {
        SceneBranch branch = SceneBranch.builder()
                .label("sortie par la porte")
                .targetSceneId("sc-corridor")
                .build();

        assertNull(branch.getCondition());
    }

    @Test
    void twoBranches_withSameFields_areEqual() {
        SceneBranch a = SceneBranch.builder()
                .label("fuite")
                .targetSceneId("sc-2")
                .condition(null)
                .build();
        SceneBranch b = SceneBranch.builder()
                .label("fuite")
                .targetSceneId("sc-2")
                .condition(null)
                .build();

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void twoBranches_differingOnTargetSceneId_areNotEqual() {
        SceneBranch a = SceneBranch.builder().label("X").targetSceneId("sc-1").build();
        SceneBranch b = SceneBranch.builder().label("X").targetSceneId("sc-2").build();

        assertNotEquals(a, b);
    }

    @Test
    void twoBranches_differingOnCondition_areNotEqual() {
        SceneBranch a = SceneBranch.builder().label("X").targetSceneId("sc-1").condition("A").build();
        SceneBranch b = SceneBranch.builder().label("X").targetSceneId("sc-1").condition("B").build();

        assertNotEquals(a, b);
    }
}
