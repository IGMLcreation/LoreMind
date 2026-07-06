package com.loremind.infrastructure.transfer;

import com.loremind.domain.campaigncontext.structure.ArcType;
import com.loremind.domain.campaigncontext.quest.Prerequisite;
import com.loremind.domain.campaigncontext.structure.SceneBranch;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests unitaires PURS (sans Spring/DB) de {@link IdRemapper}, la logique de
 * remapping {@code oldId → newId} de l'import en mode FUSION. Verrouille les
 * invariants critiques : une référence absente de la map est CONSERVÉE, jamais
 * perdue ni remplacée par null.
 */
class IdRemapperTest {

    private static final Map<Long, Long> MAP = Map.of(1L, 100L, 2L, 200L);

    // --- remapId (FK Long) ---------------------------------------------------

    @Test
    void remapId_null_resteNull() {
        assertNull(IdRemapper.remapId(MAP, null));
    }

    @Test
    void remapId_present_estRemappe() {
        assertEquals(100L, IdRemapper.remapId(MAP, 1L));
    }

    @Test
    void remapId_absent_conserveLAncienneValeur() {
        assertEquals(42L, IdRemapper.remapId(MAP, 42L));
    }

    // --- remapStringId (ref faible String) -----------------------------------

    @Test
    void remapStringId_null_resteNull() {
        assertNull(IdRemapper.remapStringId(MAP, null));
    }

    @Test
    void remapStringId_blanc_resteInchange() {
        assertEquals("   ", IdRemapper.remapStringId(MAP, "   "));
    }

    @Test
    void remapStringId_numeriquePresent_estRemappe() {
        assertEquals("200", IdRemapper.remapStringId(MAP, "2"));
    }

    @Test
    void remapStringId_numeriqueAvecEspaces_estTrimmeEtRemappe() {
        assertEquals("100", IdRemapper.remapStringId(MAP, "  1  "));
    }

    @Test
    void remapStringId_numeriqueAbsent_conserveLAncien() {
        assertEquals("999", IdRemapper.remapStringId(MAP, "999"));
    }

    @Test
    void remapStringId_nonNumerique_resteInchange() {
        assertEquals("abc-uuid", IdRemapper.remapStringId(MAP, "abc-uuid"));
    }

    // --- remapStringList -----------------------------------------------------

    @Test
    void remapStringList_null_donneListeVide() {
        assertEquals(List.of(), IdRemapper.remapStringList(MAP, null));
    }

    @Test
    void remapStringList_remappeChaqueElement() {
        assertEquals(List.of("100", "999", "200"),
                IdRemapper.remapStringList(MAP, List.of("1", "999", "2")));
    }

    // --- remapPrerequisites --------------------------------------------------

    @Test
    void remapPrerequisites_null_donneListeVide() {
        assertEquals(List.of(), IdRemapper.remapPrerequisites(MAP, null));
    }

    @Test
    void remapPrerequisites_questCompleted_remappeLeQuestId() {
        List<Prerequisite> out = IdRemapper.remapPrerequisites(
                MAP, List.of(new Prerequisite.QuestCompleted("1")));
        assertEquals("100", ((Prerequisite.QuestCompleted) out.get(0)).questId());
    }

    @Test
    void remapPrerequisites_autresTypes_inchanges() {
        Prerequisite flag = new Prerequisite.FlagSet("porte_ouverte");
        Prerequisite session = new Prerequisite.SessionReached(3);
        List<Prerequisite> out = IdRemapper.remapPrerequisites(MAP, List.of(flag, session));
        // FlagSet / SessionReached ne sont pas réécrits : même instance.
        assertSame(flag, out.get(0));
        assertSame(session, out.get(1));
    }

    // --- remapBranches -------------------------------------------------------

    @Test
    void remapBranches_null_donneListeVide() {
        assertEquals(List.of(), IdRemapper.remapBranches(MAP, null));
    }

    @Test
    void remapBranches_remappeTargetSceneId_etPreserveLabelEtCondition() {
        SceneBranch in = new SceneBranch("Si attaque", "2", "notes MJ");
        SceneBranch out = IdRemapper.remapBranches(MAP, List.of(in)).get(0);
        assertEquals("Si attaque", out.label());
        assertEquals("200", out.targetSceneId());
        assertEquals("notes MJ", out.condition());
    }

    // --- parseArcType --------------------------------------------------------

    @Test
    void parseArcType_null_donneLinear() {
        assertEquals(ArcType.LINEAR, IdRemapper.parseArcType(null));
    }

    @Test
    void parseArcType_valeurValide_estParsee() {
        assertEquals(ArcType.HUB, IdRemapper.parseArcType("HUB"));
    }

    @Test
    void parseArcType_valeurInconnue_retombeSurLinear() {
        assertEquals(ArcType.LINEAR, IdRemapper.parseArcType("INEXISTANT"));
    }
}
