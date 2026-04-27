package com.loremind.application.generationcontext;

import com.loremind.domain.campaigncontext.Arc;
import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.Chapter;
import com.loremind.domain.campaigncontext.Character;
import com.loremind.domain.campaigncontext.Npc;
import com.loremind.domain.campaigncontext.Scene;
import com.loremind.domain.campaigncontext.SceneBranch;
import com.loremind.domain.campaigncontext.ports.ArcRepository;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
import com.loremind.domain.campaigncontext.ports.CharacterRepository;
import com.loremind.domain.campaigncontext.ports.NpcRepository;
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
    @Mock
    private NpcRepository npcRepository;

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

        assertEquals("Les Terres Brisées", ctx.campaignName());
        assertEquals("Campagne dark fantasy", ctx.campaignDescription());
        assertTrue(ctx.arcs().isEmpty());
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

        assertEquals(2, ctx.arcs().size());
        assertEquals("Arc A", ctx.arcs().get(0).name());
        assertEquals("Arc B", ctx.arcs().get(1).name());

        // Chapitres tries : ch2 (order 1) avant ch1 (order 2)
        assertEquals(2, ctx.arcs().get(0).chapters().size());
        assertEquals("Ch B", ctx.arcs().get(0).chapters().get(0).name());
        assertEquals("Ch A", ctx.arcs().get(0).chapters().get(1).name());

        // Scenes dans ch-1 : s2 (order 1) avant s1 (order 2)
        var chADto = ctx.arcs().get(0).chapters().get(1);
        assertEquals("Scene B", chADto.scenes().get(0).name());
        assertEquals("Scene A", chADto.scenes().get(1).name());
    }

    @Test
    void testBuild_ResolvesBranchTargetSceneName() {
        Arc arc = Arc.builder().id("arc-1").name("Arc").description("").order(1).build();
        Chapter ch = Chapter.builder().id("ch-1").arcId("arc-1").name("Ch").description("").order(1).build();

        SceneBranch validBranch = new SceneBranch("Si les joueurs fuient", "s-2", "en cas de combat perdu");
        SceneBranch danglingBranch = SceneBranch.of("Vers l'inconnu", "s-inconnu");

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

        var scene1Summary = ctx.arcs().get(0).chapters().get(0).scenes().get(0);
        assertEquals(2, scene1Summary.branches().size());
        assertEquals("Fuite", scene1Summary.branches().get(0).targetSceneName());
        assertEquals("en cas de combat perdu", scene1Summary.branches().get(0).condition());
        // ID inconnu → libellé de fallback
        assertEquals("(scène inconnue)", scene1Summary.branches().get(1).targetSceneName());
    }

    @Test
    void testBuild_ProjectsCharactersAndNpcsWithSnippets() {
        Character pj1 = Character.builder().id("c-1").campaignId("camp-1").order(1)
                .name("Aragorn")
                .markdownContent("# Aragorn\n\nRôdeur du Nord, héritier d'Isildur.")
                .build();
        Character pj2 = Character.builder().id("c-2").campaignId("camp-1").order(2)
                .name("Legolas")
                .markdownContent(null) // pas de snippet → string vide
                .build();
        Npc npc1 = Npc.builder().id("n-1").campaignId("camp-1").order(2)
                .name("Borin le forgeron")
                .markdownContent("# Borin\n\nNain barbu au regard perçant, ancien clan Feuillefer.")
                .build();
        Npc npc2 = Npc.builder().id("n-2").campaignId("camp-1").order(1)
                .name("Dame Elara")
                .markdownContent("")
                .build();

        when(campaignRepository.findById("camp-1")).thenReturn(Optional.of(campaign));
        when(arcRepository.findByCampaignId("camp-1")).thenReturn(List.of());
        when(characterRepository.findByCampaignId("camp-1")).thenReturn(List.of(pj2, pj1));
        when(npcRepository.findByCampaignId("camp-1")).thenReturn(List.of(npc1, npc2));

        CampaignStructuralContext ctx = builder.build("camp-1");

        // PJ triés par order croissant
        assertEquals(2, ctx.characters().size());
        assertEquals("Aragorn", ctx.characters().get(0).name());
        assertEquals("Rôdeur du Nord, héritier d'Isildur.", ctx.characters().get(0).snippet());
        assertEquals("Legolas", ctx.characters().get(1).name());
        assertEquals("", ctx.characters().get(1).snippet());

        // PNJ triés par order croissant : Elara (1) avant Borin (2)
        assertEquals(2, ctx.npcs().size());
        assertEquals("Dame Elara", ctx.npcs().get(0).name());
        assertEquals("", ctx.npcs().get(0).snippet());
        assertEquals("Borin le forgeron", ctx.npcs().get(1).name());
        assertEquals("Nain barbu au regard perçant, ancien clan Feuillefer.",
                ctx.npcs().get(1).snippet());
    }

    @Test
    void testBuild_TruncatesLongSnippet() {
        // Snippet > 160 chars : doit être tronqué à 159 + "…"
        String longLine = "x".repeat(200);
        Npc longNpc = Npc.builder().id("n-1").campaignId("camp-1").order(1)
                .name("Verbeux").markdownContent(longLine).build();

        when(campaignRepository.findById("camp-1")).thenReturn(Optional.of(campaign));
        when(arcRepository.findByCampaignId("camp-1")).thenReturn(List.of());
        when(npcRepository.findByCampaignId("camp-1")).thenReturn(List.of(longNpc));

        CampaignStructuralContext ctx = builder.build("camp-1");

        String snippet = ctx.npcs().get(0).snippet();
        assertEquals(160, snippet.length());
        assertTrue(snippet.endsWith("…"));
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

        assertEquals(2, ctx.arcs().get(0).illustrationCount());
        assertEquals(0, ctx.arcs().get(0).chapters().get(0).illustrationCount());
        assertEquals(1, ctx.arcs().get(0).chapters().get(0).scenes().get(0).illustrationCount());
        assertTrue(ctx.arcs().get(0).chapters().get(0).scenes().get(0).branches().isEmpty());
    }
}
