package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.ports.ArcRepository;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
import com.loremind.domain.campaigncontext.ports.QuestRepository;
import com.loremind.domain.campaigncontext.ports.SceneRepository;
import com.loremind.domain.campaigncontext.quest.Quest;
import com.loremind.domain.campaigncontext.readiness.ReadinessEntityType;
import com.loremind.domain.campaigncontext.structure.Arc;
import com.loremind.domain.campaigncontext.structure.ArcType;
import com.loremind.domain.campaigncontext.structure.Chapter;
import com.loremind.domain.campaigncontext.structure.Scene;
import com.loremind.domain.campaigncontext.structure.SceneBranch;
import com.loremind.domain.campaigncontext.structure.SceneType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Test unitaire pour {@link WritingReviewService} (Mentor d'écriture, phase M2).
 * Chaque règle WRITE-xxx a son cas positif ET un cas qui ne doit PAS la déclencher
 * (le mentor doit rester sobre : un faux positif décourage le débutant).
 */
@ExtendWith(MockitoExtension.class)
class WritingReviewServiceTest {

    private static final String CAMP = "camp";

    @Mock private CampaignRepository campaignRepository;
    @Mock private ArcRepository arcRepository;
    @Mock private ChapterRepository chapterRepository;
    @Mock private SceneRepository sceneRepository;
    @Mock private QuestRepository questRepository;

    @InjectMocks private WritingReviewService service;

    // --- helpers ---

    private static Arc arc(String id, ArcType type) {
        return Arc.builder().id(id).name(id).campaignId(CAMP).type(type).order(0).stakes("Le village brûle").build();
    }

    private static Chapter chapter(String id, String arcId) {
        return Chapter.builder().id(id).name(id).arcId(arcId).order(0).narrativeStakes("Un allié en danger").build();
    }

    /** Scène « propre » : typée, avec un choix décrit (ne déclenche aucune règle seule). */
    private static Scene.SceneBuilder scene(String id, int order) {
        return Scene.builder().id(id).name(id).chapterId("chap").order(order)
                .type(SceneType.NPC).choicesConsequences("Convaincre ou menacer");
    }

    private void tree(Arc arc, Chapter chapter, List<Scene> scenes) {
        when(arcRepository.findByCampaignId(CAMP)).thenReturn(List.of(arc));
        when(chapterRepository.findByArcId(arc.getId())).thenReturn(List.of(chapter));
        when(sceneRepository.findByChapterId(chapter.getId())).thenReturn(scenes);
    }

    private void tree(List<Scene> scenes) {
        tree(arc("arc", ArcType.HUB), chapter("chap", "arc"), scenes);
    }

    private Set<String> ruleIds() {
        return service.review(CAMP).remarks().stream().map(WritingRemark::ruleId).collect(Collectors.toSet());
    }

    private WritingRemark remark(String ruleId) {
        return service.review(CAMP).remarks().stream().filter(r -> r.ruleId().equals(ruleId))
                .findFirst().orElseThrow(() -> new AssertionError("Règle absente : " + ruleId));
    }

    @Test
    void emptyCampaign_hasNoRemark() {
        assertTrue(service.review(CAMP).remarks().isEmpty());
    }

    @Test
    void wellWrittenChapter_hasNoRemark() {
        tree(List.of(scene("s1", 0).build(), scene("s2", 1).type(SceneType.LOCATION).build()));
        assertTrue(service.review(CAMP).remarks().isEmpty());
    }

    // --- WRITE-001 : couloir ---

    @Test
    void corridor_threeScenesWithoutChoiceNorBranch() {
        tree(List.of(
                scene("s1", 0).choicesConsequences(null).build(),
                scene("s2", 1).choicesConsequences(null).type(SceneType.LOCATION).build(),
                scene("s3", 2).choicesConsequences(null).build()));

        WritingRemark r = remark("WRITE-001-CORRIDOR");
        assertEquals(ReadinessEntityType.CHAPTER, r.entityType());
        assertEquals("3", r.params().get("count"));
    }

    @Test
    void corridor_notRaised_whenOneSceneOffersAChoice() {
        tree(List.of(
                scene("s1", 0).choicesConsequences(null).build(),
                scene("s2", 1).type(SceneType.LOCATION).build(),
                scene("s3", 2).choicesConsequences(null).build()));
        assertFalse(ruleIds().contains("WRITE-001-CORRIDOR"));
    }

    // --- WRITE-002 : faux carrefour ---

    @Test
    void fakeChoice_allBranchesLeadToSameScene() {
        Scene target = scene("s2", 1).name("La crypte").type(SceneType.LOCATION).build();
        tree(List.of(
                scene("s1", 0).branches(List.of(SceneBranch.of("A", "s2"), SceneBranch.of("B", "s2"))).build(),
                target));

        WritingRemark r = remark("WRITE-002-FAKE-CHOICE");
        assertEquals("s1", r.entityId());
        assertEquals("La crypte", r.params().get("target"));
        assertEquals("2", r.params().get("count"));
    }

    @Test
    void fakeChoice_notRaised_whenBranchesDiverge() {
        tree(List.of(
                scene("s1", 0).branches(List.of(SceneBranch.of("A", "s2"), SceneBranch.of("B", "s3"))).build(),
                scene("s2", 1).type(SceneType.LOCATION).build(),
                scene("s3", 2).build()));
        assertFalse(ruleIds().contains("WRITE-002-FAKE-CHOICE"));
    }

    // --- WRITE-003 : révélation à chemin unique ---

    @Test
    void singleClue_revelationReachableByOnePath() {
        tree(List.of(
                scene("s1", 0).branches(List.of(SceneBranch.of("A", "rev"))).build(),
                scene("rev", 1).type(SceneType.REVELATION).build()));

        assertEquals("1", remark("WRITE-003-SINGLE-CLUE").params().get("incoming"));
    }

    @Test
    void singleClue_notRaised_withTwoPaths_orInChapterWithoutLinks() {
        tree(List.of(
                scene("s1", 0).branches(List.of(SceneBranch.of("A", "rev"))).build(),
                scene("s2", 1).type(SceneType.LOCATION).branches(List.of(SceneBranch.of("B", "rev"))).build(),
                scene("rev", 2).type(SceneType.REVELATION).build()));
        assertFalse(ruleIds().contains("WRITE-003-SINGLE-CLUE"));
    }

    @Test
    void singleClue_notRaised_inChapterWithoutAnyLink() {
        tree(List.of(scene("s1", 0).build(), scene("rev", 1).type(SceneType.REVELATION).build()));
        assertFalse(ruleIds().contains("WRITE-003-SINGLE-CLUE"));
    }

    // --- WRITE-004 : rythme monotone ---

    @Test
    void monotonousPacing_threeFightsInARow() {
        tree(List.of(
                scene("s1", 0).type(SceneType.ENCOUNTER).build(),
                scene("s2", 1).enemies("Deux gobelins").build(),
                scene("s3", 2).combatDifficulty("Difficile").build(),
                scene("s4", 3).type(SceneType.LOCATION).build()));

        WritingRemark r = remark("WRITE-004-MONOTONOUS-PACING");
        assertEquals("COMBAT", r.params().get("kind"));
        assertEquals("3", r.params().get("count"));
    }

    @Test
    void monotonousPacing_notRaised_whenVaried_orUntyped() {
        tree(List.of(
                scene("s1", 0).type(SceneType.ENCOUNTER).build(),
                scene("s2", 1).type(SceneType.NPC).build(),
                scene("s3", 2).type(SceneType.ENCOUNTER).build(),
                scene("s4", 3).type(SceneType.GENERIC).build(),
                scene("s5", 4).type(SceneType.GENERIC).build(),
                scene("s6", 5).type(SceneType.GENERIC).build()));
        assertFalse(ruleIds().contains("WRITE-004-MONOTONOUS-PACING"));
    }

    // --- WRITE-005 : sans enjeu ---

    @Test
    void noStakes_onChapterAndArc() {
        Arc a = Arc.builder().id("arc").name("Arc").campaignId(CAMP).type(ArcType.LINEAR).order(0).build();
        Chapter c = Chapter.builder().id("chap").name("Chap").arcId("arc").order(0).build();
        tree(a, c, List.of(scene("s1", 0).branches(List.of(SceneBranch.of("A", "s1"))).build()));

        List<WritingRemark> remarks = service.review(CAMP).remarks().stream()
                .filter(r -> r.ruleId().equals("WRITE-005-NO-STAKES")).toList();
        assertEquals(Set.of(ReadinessEntityType.ARC, ReadinessEntityType.CHAPTER),
                remarks.stream().map(WritingRemark::entityType).collect(Collectors.toSet()));
    }

    @Test
    void noStakes_notRaised_onEmptyArc_norOnSystemArc() {
        Arc empty = Arc.builder().id("empty").name("Vide").campaignId(CAMP).type(ArcType.LINEAR).order(0).build();
        Arc system = Arc.builder().id("sys").name("Quêtes libres").campaignId(CAMP).type(ArcType.SYSTEM).order(9999).build();
        Chapter container = Chapter.builder().id("box").name("Q").arcId("sys").order(0).build();
        when(arcRepository.findByCampaignId(CAMP)).thenReturn(List.of(empty, system));
        when(chapterRepository.findByArcId("empty")).thenReturn(List.of());
        when(chapterRepository.findByArcId("sys")).thenReturn(List.of(container));
        when(sceneRepository.findByChapterId("box")).thenReturn(List.of(scene("s1", 0).build()));

        assertFalse(ruleIds().contains("WRITE-005-NO-STAKES"));
    }

    @Test
    void noStakes_raisedOnHubArcCarriedByQuests() {
        Arc hub = Arc.builder().id("hub").name("Hub").campaignId(CAMP).type(ArcType.HUB).order(0).build();
        when(arcRepository.findByCampaignId(CAMP)).thenReturn(List.of(hub));
        when(questRepository.findByCampaignId(CAMP))
                .thenReturn(List.of(Quest.builder().id("q").campaignId(CAMP).arcId("hub").build()));

        assertEquals(ReadinessEntityType.ARC, remark("WRITE-005-NO-STAKES").entityType());
    }

    // --- WRITE-006 / WRITE-007 : narration ---

    @Test
    void longNarration_overThreshold() {
        String text = "a".repeat(WritingReviewService.LONG_NARRATION_CHARS + 1);
        tree(List.of(scene("s1", 0).playerNarration(text).build()));

        assertEquals(String.valueOf(text.length()), remark("WRITE-006-LONG-NARRATION").params().get("chars"));
    }

    @Test
    void narrationDecides_capturesThePhrase_inFrenchAndEnglish() {
        tree(List.of(scene("s1", 0).playerNarration("La porte grince. Vous avez peur et reculez.").build()));
        assertEquals("Vous avez peur", remark("WRITE-007-NARRATION-DECIDES").params().get("phrase"));
    }

    @Test
    void narrationDecides_english() {
        tree(List.of(scene("s1", 0).playerNarration("The door creaks open. You enter the hall.").build()));
        assertEquals("You enter", remark("WRITE-007-NARRATION-DECIDES").params().get("phrase"));
    }

    @Test
    void narration_notRaised_whenDescriptiveAndShort() {
        tree(List.of(scene("s1", 0)
                .playerNarration("La porte grince. Une odeur de cire flotte dans l'air. Que faites-vous ?").build()));
        Set<String> ids = ruleIds();
        assertFalse(ids.contains("WRITE-006-LONG-NARRATION"));
        assertFalse(ids.contains("WRITE-007-NARRATION-DECIDES"));
    }

    // --- WRITE-008 : combat sans but ---

    @Test
    void combatWithoutPurpose() {
        tree(List.of(scene("s1", 0).type(SceneType.ENCOUNTER).choicesConsequences(null).build()));
        assertEquals("s1", remark("WRITE-008-COMBAT-NO-PURPOSE").entityId());
    }

    @Test
    void combatWithoutPurpose_notRaised_whenGmNotesExplainMotives() {
        tree(List.of(scene("s1", 0).type(SceneType.ENCOUNTER).choicesConsequences(null)
                .gmSecretNotes("Ils veulent couler la barque").build()));
        assertFalse(ruleIds().contains("WRITE-008-COMBAT-NO-PURPOSE"));
    }

    // --- WRITE-009 : fin unique ---

    @Test
    void singleEnding_onLastSceneOfLinearArc() {
        tree(arc("arc", ArcType.LINEAR), chapter("chap", "arc"), List.of(
                scene("s1", 0).build(),
                scene("final", 1).type(SceneType.LOCATION).choicesConsequences(null).build()));

        WritingRemark r = remark("WRITE-009-SINGLE-ENDING");
        assertEquals("final", r.entityId());
        assertEquals("arc", r.params().get("arc"));
    }

    @Test
    void singleEnding_notRaised_onHubArc_orWhenFinaleHasChoices() {
        tree(arc("arc", ArcType.HUB), chapter("chap", "arc"),
                List.of(scene("final", 0).choicesConsequences(null).build()));
        assertFalse(ruleIds().contains("WRITE-009-SINGLE-ENDING"));
    }

    // --- WRITE-010 : scènes non typées ---

    @Test
    void untypedScenes_whenMostScenesAreGeneric() {
        List<Scene> scenes = new ArrayList<>(IntStream.range(0, 5)
                .mapToObj(i -> scene("s" + i, i).type(SceneType.GENERIC).build()).toList());
        tree(scenes);
        when(campaignRepository.findById(CAMP))
                .thenReturn(Optional.of(Campaign.builder().id(CAMP).name("Ma campagne").build()));

        WritingRemark r = remark("WRITE-010-UNTYPED-SCENES");
        assertEquals(ReadinessEntityType.CAMPAIGN, r.entityType());
        assertEquals("100", r.params().get("percent"));
        // Remarque de campagne en tête de liste.
        assertEquals("WRITE-010-UNTYPED-SCENES", service.review(CAMP).remarks().get(0).ruleId());
    }

    @Test
    void untypedScenes_notRaised_belowMinimumSceneCount() {
        tree(List.of(scene("s1", 0).type(SceneType.GENERIC).build()));
        assertFalse(ruleIds().contains("WRITE-010-UNTYPED-SCENES"));
    }
}
