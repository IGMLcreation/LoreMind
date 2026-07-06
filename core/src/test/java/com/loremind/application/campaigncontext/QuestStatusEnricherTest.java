package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.quest.Prerequisite;
import com.loremind.domain.campaigncontext.quest.ProgressionStatus;
import com.loremind.domain.campaigncontext.quest.Quest;
import com.loremind.domain.campaigncontext.quest.QuestStatus;
import com.loremind.domain.playcontext.Playthrough;
import com.loremind.domain.playcontext.QuestProgression;
import com.loremind.domain.playcontext.Session;
import com.loremind.domain.playcontext.ports.PlaythroughFlagRepository;
import com.loremind.domain.playcontext.ports.PlaythroughRepository;
import com.loremind.domain.playcontext.ports.QuestProgressionRepository;
import com.loremind.domain.playcontext.ports.SessionRepository;
import com.loremind.infrastructure.web.dto.campaigncontext.QuestDTO;
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
 * Test unitaire pour QuestStatusEnricher.
 * Mocks des ports Play Context (playthrough, progression, flags, sessions).
 * Vérifie la construction du snapshot d'évaluation + le calcul du statut effectif.
 */
@ExtendWith(MockitoExtension.class)
public class QuestStatusEnricherTest {

    @Mock private PlaythroughRepository playthroughRepository;
    @Mock private QuestProgressionRepository progressionRepository;
    @Mock private PlaythroughFlagRepository flagRepository;
    @Mock private SessionRepository sessionRepository;

    @InjectMocks private QuestStatusEnricher enricher;

    // --- buildSnapshot ---

    @Test
    void testBuildSnapshot_NullPlaythroughId_ReturnsEmptyContext() {
        // Aucune interaction avec les repos de données : court-circuit.
        QuestStatusEnricher.PlaythroughEvalSnapshot snap = enricher.buildSnapshot(null);

        assertNotNull(snap);
        assertTrue(snap.progressionByQuestId().isEmpty());
        assertTrue(snap.ctx().completedQuestIds().isEmpty());
        assertEquals(0, snap.ctx().currentSessionCount());
        assertTrue(snap.ctx().campaignFlags().isEmpty());
        verify(flagRepository, never()).findByPlaythroughId(anyString());
        verify(sessionRepository, never()).findByPlaythroughId(anyString());
    }

    @Test
    void testBuildSnapshot_UnknownPlaythrough_ReturnsEmptyContext() {
        when(playthroughRepository.findById("pt-x")).thenReturn(Optional.empty());

        QuestStatusEnricher.PlaythroughEvalSnapshot snap = enricher.buildSnapshot("pt-x");

        assertTrue(snap.progressionByQuestId().isEmpty());
        assertTrue(snap.ctx().completedQuestIds().isEmpty());
        verify(flagRepository, never()).findByPlaythroughId(anyString());
    }

    @Test
    void testBuildSnapshot_PopulatesContextAndProgressionMap() {
        when(playthroughRepository.findById("pt-1"))
                .thenReturn(Optional.of(Playthrough.builder().id("pt-1").build()));
        when(flagRepository.findByPlaythroughId("pt-1")).thenReturn(Map.of("door_open", true));
        when(progressionRepository.findCompletedQuestIdsByPlaythroughId("pt-1"))
                .thenReturn(Set.of("quest-done"));
        when(sessionRepository.findByPlaythroughId("pt-1")).thenReturn(List.of(
                Session.builder().id("s1").build(),
                Session.builder().id("s2").build()));
        when(progressionRepository.findByPlaythroughId("pt-1")).thenReturn(List.of(
                QuestProgression.builder().questId("quest-1").status(ProgressionStatus.IN_PROGRESS).build(),
                QuestProgression.builder().questId("quest-2").status(ProgressionStatus.COMPLETED).build()));

        QuestStatusEnricher.PlaythroughEvalSnapshot snap = enricher.buildSnapshot("pt-1");

        assertEquals(Set.of("quest-done"), snap.ctx().completedQuestIds());
        assertEquals(2, snap.ctx().currentSessionCount());
        assertEquals(Map.of("door_open", true), snap.ctx().campaignFlags());
        assertEquals(ProgressionStatus.IN_PROGRESS, snap.progressionByQuestId().get("quest-1"));
        assertEquals(ProgressionStatus.COMPLETED, snap.progressionByQuestId().get("quest-2"));
    }

    // --- computeFor ---

    @Test
    void testComputeFor_NotStartedWithNoPrereqs_Available() {
        stubEmptyPlaythrough();
        Quest quest = Quest.builder().id("quest-1").name("Q").prerequisites(List.of()).build();
        assertEquals(QuestStatus.AVAILABLE, enricher.computeFor(quest, "pt-1"));
    }

    @Test
    void testComputeFor_NotStartedWithUnmetPrereq_Locked() {
        stubEmptyPlaythrough();
        Quest quest = Quest.builder().id("quest-1").name("Q")
                .prerequisites(List.of(new Prerequisite.QuestCompleted("other"))).build();
        assertEquals(QuestStatus.LOCKED, enricher.computeFor(quest, "pt-1"));
    }

    @Test
    void testComputeFor_CompletedProgression_Completed() {
        when(playthroughRepository.findById("pt-1"))
                .thenReturn(Optional.of(Playthrough.builder().id("pt-1").build()));
        when(flagRepository.findByPlaythroughId("pt-1")).thenReturn(Map.of());
        when(progressionRepository.findCompletedQuestIdsByPlaythroughId("pt-1")).thenReturn(Set.of());
        when(sessionRepository.findByPlaythroughId("pt-1")).thenReturn(List.of());
        when(progressionRepository.findByPlaythroughId("pt-1")).thenReturn(List.of(
                QuestProgression.builder().questId("quest-1").status(ProgressionStatus.COMPLETED).build()));

        Quest quest = Quest.builder().id("quest-1").name("Q").prerequisites(List.of()).build();
        assertEquals(QuestStatus.COMPLETED, enricher.computeFor(quest, "pt-1"));
    }

    @Test
    void testComputeFor_NoPlaythrough_FallsBackToAvailableWhenNoPrereqs() {
        // playthroughId null -> snapshot vide -> NOT_STARTED par défaut -> AVAILABLE sans prereq.
        Quest quest = Quest.builder().id("quest-1").name("Q").prerequisites(List.of()).build();
        assertEquals(QuestStatus.AVAILABLE, enricher.computeFor(quest, null));
    }

    // --- enrich ---

    @Test
    void testEnrich_NullOrEmptyDtos_NoSnapshotBuilt() {
        enricher.enrich(null, List.of(), "pt-1");
        enricher.enrich(List.of(), List.of(), "pt-1");
        verify(playthroughRepository, never()).findById(anyString());
    }

    @Test
    void testEnrich_InjectsStatusesIntoMatchingDtos() {
        when(playthroughRepository.findById("pt-1"))
                .thenReturn(Optional.of(Playthrough.builder().id("pt-1").build()));
        when(flagRepository.findByPlaythroughId("pt-1")).thenReturn(Map.of());
        when(progressionRepository.findCompletedQuestIdsByPlaythroughId("pt-1")).thenReturn(Set.of());
        when(sessionRepository.findByPlaythroughId("pt-1")).thenReturn(List.of());
        when(progressionRepository.findByPlaythroughId("pt-1")).thenReturn(List.of(
                QuestProgression.builder().questId("quest-1").status(ProgressionStatus.IN_PROGRESS).build()));

        Quest q1 = Quest.builder().id("quest-1").name("Q1").prerequisites(List.of()).build();
        Quest q2 = Quest.builder().id("quest-2").name("Q2").prerequisites(List.of()).build();

        QuestDTO d1 = new QuestDTO();
        d1.setId("quest-1");
        QuestDTO d2 = new QuestDTO();
        d2.setId("quest-2");
        QuestDTO orphan = new QuestDTO();
        orphan.setId("quest-unknown"); // pas dans domain -> ignoré

        enricher.enrich(List.of(d1, d2, orphan), List.of(q1, q2), "pt-1");

        // quest-1 : progression IN_PROGRESS -> effectif IN_PROGRESS.
        assertEquals("IN_PROGRESS", d1.getProgressionStatus());
        assertEquals("IN_PROGRESS", d1.getEffectiveStatus());
        // quest-2 : pas de progression -> NOT_STARTED + sans prereq -> AVAILABLE.
        assertEquals("NOT_STARTED", d2.getProgressionStatus());
        assertEquals("AVAILABLE", d2.getEffectiveStatus());
        // orphelin : non touché.
        assertNull(orphan.getProgressionStatus());
        assertNull(orphan.getEffectiveStatus());
    }

    private void stubEmptyPlaythrough() {
        when(playthroughRepository.findById("pt-1"))
                .thenReturn(Optional.of(Playthrough.builder().id("pt-1").build()));
        when(flagRepository.findByPlaythroughId("pt-1")).thenReturn(Map.of());
        when(progressionRepository.findCompletedQuestIdsByPlaythroughId("pt-1")).thenReturn(Set.of());
        when(sessionRepository.findByPlaythroughId("pt-1")).thenReturn(List.of());
        when(progressionRepository.findByPlaythroughId("pt-1")).thenReturn(List.of());
    }
}
