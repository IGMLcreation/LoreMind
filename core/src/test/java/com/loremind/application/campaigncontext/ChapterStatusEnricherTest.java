package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Chapter;
import com.loremind.domain.campaigncontext.Prerequisite;
import com.loremind.domain.campaigncontext.ProgressionStatus;
import com.loremind.domain.campaigncontext.QuestStatus;
import com.loremind.domain.playcontext.Playthrough;
import com.loremind.domain.playcontext.QuestProgression;
import com.loremind.domain.playcontext.Session;
import com.loremind.domain.playcontext.ports.PlaythroughFlagRepository;
import com.loremind.domain.playcontext.ports.PlaythroughRepository;
import com.loremind.domain.playcontext.ports.QuestProgressionRepository;
import com.loremind.domain.playcontext.ports.SessionRepository;
import com.loremind.infrastructure.web.dto.campaigncontext.ChapterDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test unitaire pour ChapterStatusEnricher.
 * Mocks des ports Play Context (playthrough, progression, flags, sessions).
 * Vérifie la construction du snapshot d'évaluation + le calcul du statut effectif.
 */
@ExtendWith(MockitoExtension.class)
public class ChapterStatusEnricherTest {

    @Mock
    private PlaythroughRepository playthroughRepository;
    @Mock
    private QuestProgressionRepository progressionRepository;
    @Mock
    private PlaythroughFlagRepository flagRepository;
    @Mock
    private SessionRepository sessionRepository;

    @InjectMocks
    private ChapterStatusEnricher enricher;

    // --- buildSnapshot ---

    @Test
    void testBuildSnapshot_NullPlaythroughId_ReturnsEmptyContext() {
        // Aucune interaction avec les repos de données : court-circuit.
        ChapterStatusEnricher.PlaythroughEvalSnapshot snap = enricher.buildSnapshot(null);

        assertNotNull(snap);
        assertTrue(snap.progressionByChapterId().isEmpty());
        assertTrue(snap.ctx().completedQuestIds().isEmpty());
        assertEquals(0, snap.ctx().currentSessionCount());
        assertTrue(snap.ctx().campaignFlags().isEmpty());
        verify(flagRepository, never()).findByPlaythroughId(anyString());
        verify(sessionRepository, never()).findByPlaythroughId(anyString());
    }

    @Test
    void testBuildSnapshot_UnknownPlaythrough_ReturnsEmptyContext() {
        when(playthroughRepository.findById("pt-x")).thenReturn(Optional.empty());

        ChapterStatusEnricher.PlaythroughEvalSnapshot snap = enricher.buildSnapshot("pt-x");

        assertTrue(snap.progressionByChapterId().isEmpty());
        assertTrue(snap.ctx().completedQuestIds().isEmpty());
        verify(flagRepository, never()).findByPlaythroughId(anyString());
    }

    @Test
    void testBuildSnapshot_PopulatesContextAndProgressionMap() {
        when(playthroughRepository.findById("pt-1"))
                .thenReturn(Optional.of(Playthrough.builder().id("pt-1").build()));
        when(flagRepository.findByPlaythroughId("pt-1")).thenReturn(Map.of("door_open", true));
        when(progressionRepository.findCompletedChapterIdsByPlaythroughId("pt-1"))
                .thenReturn(Set.of("chap-done"));
        when(sessionRepository.findByPlaythroughId("pt-1")).thenReturn(List.of(
                Session.builder().id("s1").build(),
                Session.builder().id("s2").build()));
        when(progressionRepository.findByPlaythroughId("pt-1")).thenReturn(List.of(
                QuestProgression.builder().chapterId("chap-1").status(ProgressionStatus.IN_PROGRESS).build(),
                QuestProgression.builder().chapterId("chap-2").status(ProgressionStatus.COMPLETED).build()));

        ChapterStatusEnricher.PlaythroughEvalSnapshot snap = enricher.buildSnapshot("pt-1");

        assertEquals(Set.of("chap-done"), snap.ctx().completedQuestIds());
        assertEquals(2, snap.ctx().currentSessionCount());
        assertEquals(Map.of("door_open", true), snap.ctx().campaignFlags());
        assertEquals(ProgressionStatus.IN_PROGRESS, snap.progressionByChapterId().get("chap-1"));
        assertEquals(ProgressionStatus.COMPLETED, snap.progressionByChapterId().get("chap-2"));
    }

    // --- computeFor ---

    @Test
    void testComputeFor_NotStartedWithNoPrereqs_Available() {
        when(playthroughRepository.findById("pt-1"))
                .thenReturn(Optional.of(Playthrough.builder().id("pt-1").build()));
        when(flagRepository.findByPlaythroughId("pt-1")).thenReturn(Map.of());
        when(progressionRepository.findCompletedChapterIdsByPlaythroughId("pt-1")).thenReturn(Set.of());
        when(sessionRepository.findByPlaythroughId("pt-1")).thenReturn(List.of());
        when(progressionRepository.findByPlaythroughId("pt-1")).thenReturn(List.of());

        Chapter chapter = Chapter.builder().id("chap-1").name("C").prerequisites(List.of()).build();

        QuestStatus status = enricher.computeFor(chapter, "pt-1");

        assertEquals(QuestStatus.AVAILABLE, status);
    }

    @Test
    void testComputeFor_NotStartedWithUnmetPrereq_Locked() {
        when(playthroughRepository.findById("pt-1"))
                .thenReturn(Optional.of(Playthrough.builder().id("pt-1").build()));
        when(flagRepository.findByPlaythroughId("pt-1")).thenReturn(Map.of());
        when(progressionRepository.findCompletedChapterIdsByPlaythroughId("pt-1")).thenReturn(Set.of());
        when(sessionRepository.findByPlaythroughId("pt-1")).thenReturn(List.of());
        when(progressionRepository.findByPlaythroughId("pt-1")).thenReturn(List.of());

        Chapter chapter = Chapter.builder().id("chap-1").name("C")
                .prerequisites(List.of(new Prerequisite.QuestCompleted("other"))).build();

        QuestStatus status = enricher.computeFor(chapter, "pt-1");

        assertEquals(QuestStatus.LOCKED, status);
    }

    @Test
    void testComputeFor_CompletedProgression_Completed() {
        when(playthroughRepository.findById("pt-1"))
                .thenReturn(Optional.of(Playthrough.builder().id("pt-1").build()));
        when(flagRepository.findByPlaythroughId("pt-1")).thenReturn(Map.of());
        when(progressionRepository.findCompletedChapterIdsByPlaythroughId("pt-1")).thenReturn(Set.of());
        when(sessionRepository.findByPlaythroughId("pt-1")).thenReturn(List.of());
        when(progressionRepository.findByPlaythroughId("pt-1")).thenReturn(List.of(
                QuestProgression.builder().chapterId("chap-1").status(ProgressionStatus.COMPLETED).build()));

        Chapter chapter = Chapter.builder().id("chap-1").name("C").prerequisites(List.of()).build();

        QuestStatus status = enricher.computeFor(chapter, "pt-1");

        assertEquals(QuestStatus.COMPLETED, status);
    }

    @Test
    void testComputeFor_NoPlaythrough_FallsBackToAvailableWhenNoPrereqs() {
        // playthroughId null -> snapshot vide -> NOT_STARTED par défaut.
        Chapter chapter = Chapter.builder().id("chap-1").name("C").prerequisites(List.of()).build();

        QuestStatus status = enricher.computeFor(chapter, null);

        assertEquals(QuestStatus.AVAILABLE, status);
    }

    // --- enrich ---

    @Test
    void testEnrich_NullOrEmptyDtos_NoSnapshotBuilt() {
        enricher.enrich(null, List.of(), "pt-1");
        enricher.enrich(List.of(), List.of(), "pt-1");

        // Aucun snapshot construit -> pas d'accès au repo playthrough.
        verify(playthroughRepository, never()).findById(anyString());
    }

    @Test
    void testEnrich_InjectsStatusesIntoMatchingDtos() {
        when(playthroughRepository.findById("pt-1"))
                .thenReturn(Optional.of(Playthrough.builder().id("pt-1").build()));
        when(flagRepository.findByPlaythroughId("pt-1")).thenReturn(Map.of());
        when(progressionRepository.findCompletedChapterIdsByPlaythroughId("pt-1")).thenReturn(Set.of());
        when(sessionRepository.findByPlaythroughId("pt-1")).thenReturn(List.of());
        when(progressionRepository.findByPlaythroughId("pt-1")).thenReturn(List.of(
                QuestProgression.builder().chapterId("chap-1").status(ProgressionStatus.IN_PROGRESS).build()));

        Chapter c1 = Chapter.builder().id("chap-1").name("C1").prerequisites(List.of()).build();
        Chapter c2 = Chapter.builder().id("chap-2").name("C2").prerequisites(List.of()).build();

        ChapterDTO d1 = new ChapterDTO();
        d1.setId("chap-1");
        ChapterDTO d2 = new ChapterDTO();
        d2.setId("chap-2");
        ChapterDTO orphan = new ChapterDTO();
        orphan.setId("chap-unknown"); // pas dans domain -> ignoré

        enricher.enrich(List.of(d1, d2, orphan), List.of(c1, c2), "pt-1");

        // chap-1 : progression IN_PROGRESS -> effectif IN_PROGRESS.
        assertEquals("IN_PROGRESS", d1.getProgressionStatus());
        assertEquals("IN_PROGRESS", d1.getEffectiveStatus());
        // chap-2 : pas de progression -> NOT_STARTED + sans prereq -> AVAILABLE.
        assertEquals("NOT_STARTED", d2.getProgressionStatus());
        assertEquals("AVAILABLE", d2.getEffectiveStatus());
        // orphelin : non touché.
        assertNull(orphan.getProgressionStatus());
        assertNull(orphan.getEffectiveStatus());
    }
}
