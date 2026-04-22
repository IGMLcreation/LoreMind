package com.loremind.domain.generationcontext;

import com.loremind.domain.generationcontext.CampaignStructuralContext.ArcSummary;
import com.loremind.domain.generationcontext.CampaignStructuralContext.BranchHint;
import com.loremind.domain.generationcontext.CampaignStructuralContext.ChapterSummary;
import com.loremind.domain.generationcontext.CampaignStructuralContext.SceneSummary;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires pour CampaignStructuralContext et ses types imbriques.
 * Focus sur les annotations @Singular (chapters/scenes/branches/arcs) qui
 * permettent une construction incrementale du graphe narratif.
 */
class CampaignStructuralContextTest {

    @Test
    void builder_constructsFullNarrativeTree() {
        BranchHint branch = BranchHint.builder()
                .label("si les PJ fuient")
                .targetSceneName("La poursuite")
                .condition("PJ < moitie des HP")
                .build();

        SceneSummary scene = SceneSummary.builder()
                .name("L'auberge")
                .description("Rencontre tendue avec le tavernier")
                .illustrationCount(2)
                .branch(branch)
                .build();

        ChapterSummary chapter = ChapterSummary.builder()
                .name("L'arrivee")
                .description("Les PJ decouvrent la ville")
                .scene(scene)
                .build();

        ArcSummary arc = ArcSummary.builder()
                .name("Acte I")
                .description("Mise en place")
                .illustrationCount(1)
                .chapter(chapter)
                .build();

        CampaignStructuralContext ctx = CampaignStructuralContext.builder()
                .campaignName("Les Ombres")
                .campaignDescription("Une campagne dark fantasy")
                .arc(arc)
                .build();

        assertEquals("Les Ombres", ctx.getCampaignName());
        assertEquals(1, ctx.getArcs().size());
        assertEquals(1, ctx.getArcs().get(0).getChapters().size());
        assertEquals(1, ctx.getArcs().get(0).getChapters().get(0).getScenes().size());
        assertEquals(1, ctx.getArcs().get(0).getChapters().get(0).getScenes().get(0).getBranches().size());
    }

    // --- BranchHint ---------------------------------------------------------

    @Test
    void branchHint_preservesAllFields() {
        BranchHint b = BranchHint.builder()
                .label("combat")
                .targetSceneName("La confrontation")
                .condition("initiative > 15")
                .build();

        assertEquals("combat", b.getLabel());
        assertEquals("La confrontation", b.getTargetSceneName());
        assertEquals("initiative > 15", b.getCondition());
    }

    @Test
    void branchHint_conditionIsOptional() {
        BranchHint b = BranchHint.builder()
                .label("suite normale")
                .targetSceneName("Scene 2")
                .build();

        assertNull(b.getCondition());
    }

    // --- illustrationCount --------------------------------------------------

    @Test
    void illustrationCount_defaultsToZero_onAllSummaryTypes() {
        ArcSummary arc = ArcSummary.builder().name("X").build();
        ChapterSummary chapter = ChapterSummary.builder().name("X").build();
        SceneSummary scene = SceneSummary.builder().name("X").build();

        assertEquals(0, arc.getIllustrationCount());
        assertEquals(0, chapter.getIllustrationCount());
        assertEquals(0, scene.getIllustrationCount());
    }

    // --- @Singular : accumulation incrementale -----------------------------

    @Test
    void singular_accumulatesMultipleCalls() {
        ArcSummary arc = ArcSummary.builder()
                .name("Acte I")
                .chapter(ChapterSummary.builder().name("Ch1").build())
                .chapter(ChapterSummary.builder().name("Ch2").build())
                .chapter(ChapterSummary.builder().name("Ch3").build())
                .build();

        assertEquals(3, arc.getChapters().size());
        assertTrue(arc.getChapters().stream().anyMatch(c -> "Ch2".equals(c.getName())));
    }
}
