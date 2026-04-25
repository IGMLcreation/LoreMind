package com.loremind.domain.generationcontext;

import com.loremind.domain.generationcontext.CampaignStructuralContext.ArcSummary;
import com.loremind.domain.generationcontext.CampaignStructuralContext.BranchHint;
import com.loremind.domain.generationcontext.CampaignStructuralContext.ChapterSummary;
import com.loremind.domain.generationcontext.CampaignStructuralContext.SceneSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests unitaires pour CampaignStructuralContext et ses types imbriques.
 * Records purs : aucune dependance technique.
 */
class CampaignStructuralContextTest {

    @Test
    void constructor_buildsFullNarrativeTree() {
        BranchHint branch = new BranchHint("si les PJ fuient", "La poursuite", "PJ < moitie des HP");

        SceneSummary scene = new SceneSummary(
                "L'auberge",
                "Rencontre tendue avec le tavernier",
                2,
                List.of(branch));

        ChapterSummary chapter = new ChapterSummary(
                "L'arrivee",
                "Les PJ decouvrent la ville",
                0,
                List.of(scene));

        ArcSummary arc = new ArcSummary(
                "Acte I",
                "Mise en place",
                1,
                List.of(chapter));

        CampaignStructuralContext ctx = new CampaignStructuralContext(
                "Les Ombres",
                "Une campagne dark fantasy",
                List.of(arc),
                List.of());

        assertEquals("Les Ombres", ctx.campaignName());
        assertEquals(1, ctx.arcs().size());
        assertEquals(1, ctx.arcs().get(0).chapters().size());
        assertEquals(1, ctx.arcs().get(0).chapters().get(0).scenes().size());
        assertEquals(1, ctx.arcs().get(0).chapters().get(0).scenes().get(0).branches().size());
    }

    // --- BranchHint ---------------------------------------------------------

    @Test
    void branchHint_preservesAllFields() {
        BranchHint b = new BranchHint("combat", "La confrontation", "initiative > 15");

        assertEquals("combat", b.label());
        assertEquals("La confrontation", b.targetSceneName());
        assertEquals("initiative > 15", b.condition());
    }

    @Test
    void branchHint_conditionIsOptional() {
        BranchHint b = new BranchHint("suite normale", "Scene 2", null);

        assertNull(b.condition());
    }

    // --- illustrationCount --------------------------------------------------

    @Test
    void illustrationCount_defaultsToZero_onAllSummaryTypes() {
        ArcSummary arc = new ArcSummary("X", null, 0, List.of());
        ChapterSummary chapter = new ChapterSummary("X", null, 0, List.of());
        SceneSummary scene = new SceneSummary("X", null, 0, List.of());

        assertEquals(0, arc.illustrationCount());
        assertEquals(0, chapter.illustrationCount());
        assertEquals(0, scene.illustrationCount());
    }

    // --- Construction incrementale (chapitres multiples) -------------------

    @Test
    void multipleChapters_arePreserved() {
        ArcSummary arc = new ArcSummary(
                "Acte I",
                null,
                0,
                List.of(
                        new ChapterSummary("Ch1", null, 0, List.of()),
                        new ChapterSummary("Ch2", null, 0, List.of()),
                        new ChapterSummary("Ch3", null, 0, List.of())));

        assertEquals(3, arc.chapters().size());
        assertEquals("Ch2", arc.chapters().get(1).name());
    }
}
