package com.loremind.application.generationcontext;

import com.loremind.domain.campaigncontext.Arc;
import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.Chapter;
import com.loremind.domain.campaigncontext.Scene;
import com.loremind.domain.campaigncontext.SceneBranch;
import com.loremind.domain.campaigncontext.ports.ArcRepository;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
import com.loremind.domain.campaigncontext.ports.CharacterRepository;
import com.loremind.domain.campaigncontext.ports.SceneRepository;
import com.loremind.domain.generationcontext.CampaignStructuralContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test unitaire pour CampaignStructuralContextBuilder.
 * Vérifie la projection Campaign Context → Generation Context (arcs → chapitres → scènes),
 * le tri par `order`, la résolution des branches via la map id→nom, et le comptage
 * null-safe des illustrations.
 */
@ExtendWith(MockitoExtension.class)
public class CampaignStructuralContextBuilderTest {

    @Mock
    private CampaignRepository campaignRepository;
    @Mock
    private ArcRepository arcRepository;
    @Mock
    private ChapterRepository chapterRepository;
    @Mock
    private SceneRepository sceneRepository;
    @Mock
    private CharacterRepository characterRepository;

    @InjectMocks
    private CampaignStructuralContextBuilder builder;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        campaign = Campaign.builder()
                .id("camp-1")
                .name("Les Terres Brisées")
                .description("Campagne dark fantasy")
                .build();
    }

    @Test
    void testBuild_CampaignNotFound() {
        when(campaignRepository.findById("missing")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> builder.build("missing"));
        assertTrue(ex.getMessage().contains("missing"));
    }

    @Test
    void testBuild_EmptyCampaign() {
        when(campaignRepository.findById("camp-1")).thenReturn(Optional.of(campaign));
        when(arcRepository.findByCampaignId("camp-1")).thenReturn(List.of());

        CampaignStructuralContext ctx = builder.build("camp-1");

        assertEquals("Les Terres Brisées", ctx.getCampaignName());
        assertEquals("Campagne dark fantasy", ctx.getCampaignDescription());
        assertTrue(ctx.getArcs().isEmpty());
    }

    @Test
    void testBuild_SortsArcsChaptersScenesByOrder() {
        Arc arc1 = Arc.builder().id("arc-1").name("Arc A").description("first").order(1).build();
        Arc arc2 = Arc.builder().id("arc-2").name("Arc B").description("second").order(2).build();

        Chapter ch1 = Chapter.builder().id("ch-1").arcId("arc-1").name("Ch A").description("d").order(2).build();
        Chapter ch2 = Chapter.builder().id("ch-2").arcId("arc-1").name("Ch B").description("d").order(1).build();

        Scene s1 = Scene.builder().id("s-1").chapterId("ch-1").name("Scene A").description("d").order(2).build();
        Scene s2 = Scene.builder().id("s-2").chapterId("ch-1").name("Scene B").description("d").order(1).build();

        when(campaignRepository.findById("camp-1")).thenReturn(Optional.of(campaign));
        // Volontairement inverse pour verifier le tri.
        when(arcRepository.findByCampaignId("camp-1")).thenReturn(List.of(arc2, arc1));
        when(chapterRepository.findByArcId("arc-1")).thenReturn(List.of(ch1, ch2));
        when(chapterRepository.findByArcId("arc-2")).thenReturn(List.of());
        when(sceneRepository.findByChapterId("ch-1")).thenReturn(List.of(s1, s2));
        when(sceneRepository.findByChapterId("ch-2")).thenReturn(List.of());

        CampaignStructuralContext ctx = builder.build("camp-1");

        assertEquals(2, ctx.getArcs().size());
        assertEquals("Arc A", ctx.getArcs().get(0).getName());
        assertEquals("Arc B", ctx.getArcs().get(1).getName());

        // Chapitres tries : ch2 (order 1) avant ch1 (order 2)
        assertEquals(2, ctx.getArcs().get(0).getChapters().size());
        assertEquals("Ch B", ctx.getArcs().get(0).getChapters().get(0).getName());
        assertEquals("Ch A", ctx.getArcs().get(0).getChapters().get(1).getName());

        // Scenes dans ch-1 : s2 (order 1) avant s1 (order 2)
        var chADto = ctx.getArcs().get(0).getChapters().get(1);
        assertEquals("Scene B", chADto.getScenes().get(0).getName());
        assertEquals("Scene A", chADto.getScenes().get(1).getName());
    }

    @Test
    void testBuild_ResolvesBranchTargetSceneName() {
        Arc arc = Arc.builder().id("arc-1").name("Arc").description("").order(1).build();
        Chapter ch = Chapter.builder().id("ch-1").arcId("arc-1").name("Ch").description("").order(1).build();

        SceneBranch validBranch = SceneBranch.builder()
                .label("Si les joueurs fuient")
                .targetSceneId("s-2")
                .condition("en cas de combat perdu")
                .build();
        SceneBranch danglingBranch = SceneBranch.builder()
                .label("Vers l'inconnu")
                .targetSceneId("s-inconnu")
                .build();

        Scene s1 = Scene.builder().id("s-1").chapterId("ch-1").name("Entrée").description("")
                .order(1)
                .branches(List.of(validBranch, danglingBranch))
                .build();
        Scene s2 = Scene.builder().id("s-2").chapterId("ch-1").name("Fuite").description("").order(2).build();

        when(campaignRepository.findById("camp-1")).thenReturn(Optional.of(campaign));
        when(arcRepository.findByCampaignId("camp-1")).thenReturn(List.of(arc));
        when(chapterRepository.findByArcId("arc-1")).thenReturn(List.of(ch));
        when(sceneRepository.findByChapterId("ch-1")).thenReturn(List.of(s1, s2));

        CampaignStructuralContext ctx = builder.build("camp-1");

        var scene1Summary = ctx.getArcs().get(0).getChapters().get(0).getScenes().get(0);
        assertEquals(2, scene1Summary.getBranches().size());
        assertEquals("Fuite", scene1Summary.getBranches().get(0).getTargetSceneName());
        assertEquals("en cas de combat perdu", scene1Summary.getBranches().get(0).getCondition());
        // ID inconnu → libellé de fallback
        assertEquals("(scène inconnue)", scene1Summary.getBranches().get(1).getTargetSceneName());
    }

    @Test
    void testBuild_CountsIllustrationsNullSafe() {
        Arc arc = Arc.builder().id("arc-1").name("Arc").description("").order(1)
                .illustrationImageIds(List.of("img-1", "img-2")).build();
        Chapter ch = Chapter.builder().id("ch-1").arcId("arc-1").name("Ch").description("").order(1)
                .illustrationImageIds(null) // null-safe attendu
                .build();
        Scene s = Scene.builder().id("s-1").chapterId("ch-1").name("S").description("").order(1)
                .illustrationImageIds(List.of("img-3"))
                .branches(null)
                .build();

        when(campaignRepository.findById("camp-1")).thenReturn(Optional.of(campaign));
        when(arcRepository.findByCampaignId("camp-1")).thenReturn(List.of(arc));
        when(chapterRepository.findByArcId("arc-1")).thenReturn(List.of(ch));
        when(sceneRepository.findByChapterId("ch-1")).thenReturn(List.of(s));

        CampaignStructuralContext ctx = builder.build("camp-1");

        assertEquals(2, ctx.getArcs().get(0).getIllustrationCount());
        assertEquals(0, ctx.getArcs().get(0).getChapters().get(0).getIllustrationCount());
        assertEquals(1, ctx.getArcs().get(0).getChapters().get(0).getScenes().get(0).getIllustrationCount());
        assertTrue(ctx.getArcs().get(0).getChapters().get(0).getScenes().get(0).getBranches().isEmpty());
    }
}
