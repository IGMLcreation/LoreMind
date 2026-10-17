package com.loremind.application.campaigncontext;

import com.loremind.application.playcontext.ClockService;
import com.loremind.domain.campaigncontext.ports.ArcRepository;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
import com.loremind.domain.campaigncontext.ports.SceneRepository;
import com.loremind.domain.campaigncontext.structure.Arc;
import com.loremind.domain.campaigncontext.structure.ArcType;
import com.loremind.domain.campaigncontext.structure.Chapter;
import com.loremind.domain.campaigncontext.structure.LinkType;
import com.loremind.domain.campaigncontext.structure.Scene;
import com.loremind.domain.campaigncontext.structure.SceneType;
import com.loremind.domain.playcontext.ClockTrigger;
import com.loremind.domain.playcontext.Playthrough;
import com.loremind.domain.playcontext.ports.PlaythroughRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test unitaire pour {@link GuidedSkeletonService} (Mentor d'écriture, phase M3) :
 * placement (nouvel arc / arc existant), champs reportés, chaînage des scènes, validations.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GuidedSkeletonServiceTest {

    private static final String CAMP = "camp";

    @Mock private ArcRepository arcRepository;
    @Mock private ChapterRepository chapterRepository;
    @Mock private SceneRepository sceneRepository;
    @Mock private ArcService arcService;
    @Mock private ChapterService chapterService;
    @Mock private SceneService sceneService;
    @Mock private PlaythroughRepository playthroughRepository;
    @Mock private ClockService clockService;

    @InjectMocks private GuidedSkeletonService service;

    private final List<Scene> createdScenes = new ArrayList<>();

    @BeforeEach
    void stubCreations() {
        AtomicInteger ids = new AtomicInteger();
        when(arcService.createArc(any(Arc.class))).thenAnswer(inv -> {
            Arc a = inv.getArgument(0);
            a.setId("new-arc");
            return a;
        });
        when(chapterService.createChapter(any(Chapter.class))).thenAnswer(inv -> {
            Chapter c = inv.getArgument(0);
            c.setId("new-chap");
            return c;
        });
        when(sceneService.createScene(any(Scene.class))).thenAnswer(inv -> {
            Scene s = inv.getArgument(0);
            s.setId("scene-" + ids.incrementAndGet());
            createdScenes.add(s);
            return s;
        });
    }

    private static GuidedSkeleton.SceneSpec room(String name, SceneType type) {
        return new GuidedSkeleton.SceneSpec(name, "Ce qui s'y passe", type, "La crypte",
                "Combattre ou négocier", "Le gardien protège son frère", null);
    }

    private static GuidedSkeleton skeleton(String arcId, String newArcName, boolean link) {
        return new GuidedSkeleton(arcId, newArcName,
                new GuidedSkeleton.ChapterSpec("La crypte oubliée", "Une accroche", "Retrouver le reliquaire", "Le village sera maudit"),
                List.of(room("Salle 1", SceneType.ENCOUNTER), room("Salle 2", SceneType.LOCATION), room("Salle 3", SceneType.REVELATION)),
                link, "Vers la salle suivante");
    }

    @Test
    void newArc_isLinear_andPlacedAfterVisibleArcs_beforeSystemArc() {
        when(arcRepository.findByCampaignId(CAMP)).thenReturn(List.of(
                Arc.builder().id("a1").type(ArcType.LINEAR).order(0).build(),
                Arc.builder().id("a2").type(ArcType.HUB).order(1).build(),
                Arc.builder().id("sys").type(ArcType.SYSTEM).order(9999).build()));

        GuidedSkeleton.Result result = service.create(CAMP, skeleton(null, "Les terres brûlées", false));

        ArgumentCaptor<Arc> arc = ArgumentCaptor.forClass(Arc.class);
        verify(arcService).createArc(arc.capture());
        assertEquals(ArcType.LINEAR, arc.getValue().getType());
        assertEquals(2, arc.getValue().getOrder());
        assertEquals(CAMP, arc.getValue().getCampaignId());
        assertEquals("new-arc", result.arcId());
        assertEquals("new-chap", result.chapterId());
        assertEquals(List.of("scene-1", "scene-2", "scene-3"), result.sceneIds());
    }

    @Test
    void existingArc_chapterAppendedAfterExistingChapters_withItsFields() {
        when(arcRepository.findByCampaignId(CAMP)).thenReturn(List.of(
                Arc.builder().id("a1").type(ArcType.LINEAR).order(0).build()));
        when(chapterRepository.findByArcId("a1")).thenReturn(List.of(
                Chapter.builder().id("c1").arcId("a1").order(0).build(),
                Chapter.builder().id("c2").arcId("a1").order(3).build()));

        service.create(CAMP, skeleton("a1", null, false));

        verify(arcService, never()).createArc(any(Arc.class));
        ArgumentCaptor<Chapter> chapter = ArgumentCaptor.forClass(Chapter.class);
        verify(chapterService).createChapter(chapter.capture());
        assertEquals("a1", chapter.getValue().getArcId());
        assertEquals(4, chapter.getValue().getOrder());
        assertEquals("Retrouver le reliquaire", chapter.getValue().getPlayerObjectives());
        assertEquals("Le village sera maudit", chapter.getValue().getNarrativeStakes());
    }

    @Test
    void scenes_keepOrderTypeAndFields() {
        when(arcRepository.findByCampaignId(CAMP)).thenReturn(List.of());

        service.create(CAMP, skeleton(null, "Arc", false));

        assertEquals(3, createdScenes.size());
        Scene first = createdScenes.get(0);
        assertEquals(0, first.getOrder());
        assertEquals("new-chap", first.getChapterId());
        assertEquals(SceneType.ENCOUNTER, first.getType());
        assertEquals("La crypte", first.getLocation());
        assertEquals("Combattre ou négocier", first.getChoicesConsequences());
        assertEquals("Le gardien protège son frère", first.getGmSecretNotes());
        assertEquals(SceneType.REVELATION, createdScenes.get(2).getType());
        assertEquals(2, createdScenes.get(2).getOrder());
    }

    @Test
    void linkSequentially_chainsEachSceneToTheNext_lastHasNoExit() {
        when(arcRepository.findByCampaignId(CAMP)).thenReturn(List.of());

        service.create(CAMP, skeleton(null, "Arc", true));

        verify(sceneRepository, times(2)).save(any(Scene.class));
        assertEquals("scene-2", createdScenes.get(0).getBranches().get(0).targetSceneId());
        assertEquals("Vers la salle suivante", createdScenes.get(0).getBranches().get(0).label());
        assertEquals("scene-3", createdScenes.get(1).getBranches().get(0).targetSceneId());
        assertTrue(createdScenes.get(2).getBranches() == null || createdScenes.get(2).getBranches().isEmpty());
    }

    @Test
    void unnamedScenes_areSkipped_andBlankFieldsStoredAsNull() {
        when(arcRepository.findByCampaignId(CAMP)).thenReturn(List.of());
        GuidedSkeleton s = new GuidedSkeleton(null, "Arc",
                new GuidedSkeleton.ChapterSpec("Chap", "  ", null, null),
                List.of(new GuidedSkeleton.SceneSpec("Salle", " ", null, "", null, null, null),
                        new GuidedSkeleton.SceneSpec("  ", "ignorée", SceneType.EVENT, null, null, null, null)),
                false, null);

        service.create(CAMP, s);

        assertEquals(1, createdScenes.size());
        assertNull(createdScenes.get(0).getDescription());
        assertNull(createdScenes.get(0).getLocation());
        assertEquals(SceneType.GENERIC, createdScenes.get(0).getType());
    }

    @Test
    void rejects_ambiguousPlacement() {
        assertThrows(IllegalArgumentException.class, () -> service.create(CAMP, skeleton("a1", "Arc", false)));
        assertThrows(IllegalArgumentException.class, () -> service.create(CAMP, skeleton(null, " ", false)));
    }

    @Test
    void rejects_arcOfAnotherCampaign_orNonLinearArc() {
        when(arcRepository.findByCampaignId(CAMP)).thenReturn(List.of(
                Arc.builder().id("hub").type(ArcType.HUB).order(0).build()));

        assertThrows(IllegalArgumentException.class, () -> service.create(CAMP, skeleton("ailleurs", null, false)));
        assertThrows(IllegalArgumentException.class, () -> service.create(CAMP, skeleton("hub", null, false)));
        verify(chapterService, never()).createChapter(any(Chapter.class));
    }

    @Test
    void rejects_missingChapterName_orNoNamedScene() {
        GuidedSkeleton noChapterName = new GuidedSkeleton(null, "Arc",
                new GuidedSkeleton.ChapterSpec(" ", null, null, null), List.of(room("S", null)), false, null);
        GuidedSkeleton noScene = new GuidedSkeleton(null, "Arc",
                new GuidedSkeleton.ChapterSpec("Chap", null, null, null),
                List.of(new GuidedSkeleton.SceneSpec("", null, null, null, null, null, null)), false, null);

        assertThrows(IllegalArgumentException.class, () -> service.create(CAMP, noChapterName));
        assertThrows(IllegalArgumentException.class, () -> service.create(CAMP, noScene));
    }

    // --- Liens explicites (Enquête en nœuds) et horloge (Plan du méchant) ---

    private static GuidedSkeleton withLinks(List<GuidedSkeleton.SceneSpec> scenes, List<GuidedSkeleton.LinkSpec> links,
                                            GuidedSkeleton.ClockSpec clock) {
        return new GuidedSkeleton(null, "Arc",
                new GuidedSkeleton.ChapterSpec("Enquête", "Accroche", null, null, "Le notaire est coupable"),
                scenes, false, null, links, clock);
    }

    @Test
    void explicitLinks_areAddedWithTheirKind_andIndexesSurviveSkippedScenes() {
        when(arcRepository.findByCampaignId(CAMP)).thenReturn(List.of());
        // La scène d'index 1 n'a pas de nom : ignorée, mais les index des liens restent ceux d'origine.
        List<GuidedSkeleton.SceneSpec> scenes = List.of(
                room("Accroche", SceneType.EVENT), room(" ", SceneType.LOCATION),
                room("Piste", SceneType.NPC), room("Conclusion", SceneType.REVELATION));

        service.create(CAMP, withLinks(scenes, List.of(
                new GuidedSkeleton.LinkSpec(0, 2, "Piste", LinkType.LEAD),
                new GuidedSkeleton.LinkSpec(2, 3, "Indice", LinkType.CLUE),
                new GuidedSkeleton.LinkSpec(0, 1, "Vers la scène ignorée", LinkType.LEAD),
                new GuidedSkeleton.LinkSpec(3, 3, "Boucle sur soi", LinkType.EXIT),
                new GuidedSkeleton.LinkSpec(0, 9, "Hors limites", LinkType.EXIT)), null));

        Scene hook = createdScenes.get(0);
        Scene lead = createdScenes.get(1);
        Scene end = createdScenes.get(2);
        assertEquals(1, hook.getBranches().size(), "seul le lien valide de l'accroche est gardé");
        assertEquals(lead.getId(), hook.getBranches().get(0).targetSceneId());
        assertEquals(LinkType.LEAD, hook.getBranches().get(0).kind());
        assertEquals(end.getId(), lead.getBranches().get(0).targetSceneId());
        assertEquals(LinkType.CLUE, lead.getBranches().get(0).kind());
        assertTrue(end.getBranches() == null || end.getBranches().isEmpty());
        verify(sceneRepository, times(2)).save(any(Scene.class));
    }

    @Test
    void chapterGmNotes_arePersisted() {
        when(arcRepository.findByCampaignId(CAMP)).thenReturn(List.of());
        service.create(CAMP, withLinks(List.of(room("S", SceneType.EVENT)), null, null));

        ArgumentCaptor<Chapter> chapter = ArgumentCaptor.forClass(Chapter.class);
        verify(chapterService).createChapter(chapter.capture());
        assertEquals("Le notaire est coupable", chapter.getValue().getGmNotes());
    }

    @Test
    void clock_isCreatedInEveryPlaythroughOfTheCampaign() {
        when(arcRepository.findByCampaignId(CAMP)).thenReturn(List.of());
        when(playthroughRepository.findByCampaignId(CAMP)).thenReturn(List.of(
                Playthrough.builder().id("p1").campaignId(CAMP).build(),
                Playthrough.builder().id("p2").campaignId(CAMP).build()));

        GuidedSkeleton.Result result = service.create(CAMP, withLinks(List.of(room("Étape 1", SceneType.EVENT)), null,
                new GuidedSkeleton.ClockSpec(" La régence ", "La loi martiale", 6)));

        assertEquals(2, result.clocks());
        verify(clockService).create("p1", "La régence", "La loi martiale", 6, ClockTrigger.NONE, null, null);
        verify(clockService).create("p2", "La régence", "La loi martiale", 6, ClockTrigger.NONE, null, null);
    }

    @Test
    void noClock_whenNotRequested() {
        when(arcRepository.findByCampaignId(CAMP)).thenReturn(List.of());
        GuidedSkeleton.Result result = service.create(CAMP, skeleton(null, "Arc", false));
        assertEquals(0, result.clocks());
        verify(clockService, never()).create(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any(), any());
    }
}
