package com.loremind.application.playcontext;

import com.loremind.application.campaigncontext.CampaignReadinessAssessment;
import com.loremind.application.campaigncontext.CampaignReadinessService;
import com.loremind.application.campaigncontext.QuestStatusEnricher;
import com.loremind.application.campaigncontext.ReadinessGap;
import com.loremind.domain.campaigncontext.Chapter;
import com.loremind.domain.campaigncontext.NodeType;
import com.loremind.domain.campaigncontext.Quest;
import com.loremind.domain.campaigncontext.QuestNodeRef;
import com.loremind.domain.campaigncontext.QuestStatus;
import com.loremind.domain.campaigncontext.ReadinessEntityType;
import com.loremind.domain.campaigncontext.ReadinessSeverity;
import com.loremind.domain.campaigncontext.ReadinessStatus;
import com.loremind.domain.campaigncontext.Scene;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
import com.loremind.domain.campaigncontext.ports.QuestRepository;
import com.loremind.domain.campaigncontext.ports.SceneRepository;
import com.loremind.domain.playcontext.Clock;
import com.loremind.domain.playcontext.Front;
import com.loremind.domain.playcontext.Playthrough;
import com.loremind.domain.playcontext.Session;
import com.loremind.domain.playcontext.ports.ClockRepository;
import com.loremind.domain.playcontext.ports.FrontRepository;
import com.loremind.domain.playcontext.ports.PlaythroughRepository;
import com.loremind.domain.playcontext.ports.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Test unitaire pour {@link SessionPrepService} (Phase 3 co-MJ — préparer la séance).
 * Mocks des ports + des read-models amont (QuestStatusEnricher / CampaignReadinessService).
 */
@ExtendWith(MockitoExtension.class)
class SessionPrepServiceTest {

    private static final String PT = "pt-1";
    private static final String CAMP = "camp-1";

    @Mock private PlaythroughRepository playthroughRepository;
    @Mock private SessionRepository sessionRepository;
    @Mock private ClockRepository clockRepository;
    @Mock private FrontRepository frontRepository;
    @Mock private QuestRepository questRepository;
    @Mock private ChapterRepository chapterRepository;
    @Mock private SceneRepository sceneRepository;
    @Mock private QuestStatusEnricher statusEnricher;
    @Mock private CampaignReadinessService readinessService;

    @InjectMocks private SessionPrepService service;

    @BeforeEach
    void stubPlaythrough() {
        // lenient : le test « playthrough inconnu » n'utilise pas ce stub (strict stubs sinon).
        lenient().when(playthroughRepository.findById(PT))
                .thenReturn(Optional.of(Playthrough.builder().id(PT).campaignId(CAMP).name("Table").build()));
    }

    private void stubEmptyAssessment() {
        when(readinessService.assess(CAMP)).thenReturn(new CampaignReadinessAssessment(
                CAMP, ReadinessStatus.POLISHED, Map.of(), List.of()));
    }

    private static ReadinessGap gap(ReadinessEntityType type, String id, String chapterId) {
        return new ReadinessGap(type, id, id, "RULE", "msg", ReadinessSeverity.BLOCKING, null, chapterId);
    }

    @Test
    void unknownPlaythrough_throws() {
        when(playthroughRepository.findById("nope")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.prepare("nope"));
    }

    @Test
    void partitionsQuestsByEffectiveStatus_andResolvesHotspots() {
        Quest inProgress = Quest.builder().id("q-run").name("En cours").campaignId(CAMP).order(0)
                .nodes(List.of(new QuestNodeRef(NodeType.CHAPTER, "chap-1", 0),
                        new QuestNodeRef(NodeType.SCENE, "scene-1", 1),
                        new QuestNodeRef(NodeType.SCENE, "ghost", 2))) // ref morte → ignorée
                .build();
        Quest available = Quest.builder().id("q-next").name("Dispo").campaignId(CAMP).order(1).build();
        Quest locked = Quest.builder().id("q-lock").name("Verrouillée").campaignId(CAMP).order(2).build();
        Quest done = Quest.builder().id("q-done").name("Finie").campaignId(CAMP).order(3).build();
        when(questRepository.findByCampaignId(CAMP)).thenReturn(List.of(inProgress, available, locked, done));
        when(statusEnricher.computeAll(anyList(), eq(PT))).thenReturn(Map.of(
                "q-run", QuestStatus.IN_PROGRESS,
                "q-next", QuestStatus.AVAILABLE,
                "q-lock", QuestStatus.LOCKED,
                "q-done", QuestStatus.COMPLETED));
        when(chapterRepository.findById("chap-1"))
                .thenReturn(Optional.of(Chapter.builder().id("chap-1").name("Chap").arcId("arc-1").build()));
        when(sceneRepository.findById("scene-1"))
                .thenReturn(Optional.of(Scene.builder().id("scene-1").name("Scène").chapterId("chap-9").build()));
        when(chapterRepository.findById("chap-9"))
                .thenReturn(Optional.of(Chapter.builder().id("chap-9").name("Autre").arcId("arc-2").build()));
        when(sceneRepository.findById("ghost")).thenReturn(Optional.empty());
        stubEmptyAssessment();

        SessionPrepReport report = service.prepare(PT);

        assertEquals(List.of("q-run"), report.questsInProgress().stream()
                .map(SessionPrepReport.QuestInfo::id).collect(Collectors.toList()));
        assertEquals(List.of("q-next"), report.questsAvailable().stream()
                .map(SessionPrepReport.QuestInfo::id).collect(Collectors.toList()));
        assertEquals(List.of("q-done"), report.questsCompleted().stream()
                .map(SessionPrepReport.QuestInfo::id).collect(Collectors.toList()));
        // Hotspots : chapitre + scène résolus (avec contexte de navigation), ref morte ignorée.
        assertEquals(2, report.hotspots().size());
        SessionPrepReport.NodeInfo chapterNode = report.hotspots().get(0);
        assertEquals("CHAPTER", chapterNode.nodeType());
        assertEquals("arc-1", chapterNode.arcId());
        SessionPrepReport.NodeInfo sceneNode = report.hotspots().get(1);
        assertEquals("SCENE", sceneNode.nodeType());
        assertEquals("arc-2", sceneNode.arcId());
        assertEquals("chap-9", sceneNode.chapterId());
    }

    @Test
    void focusesGapsOnProbableContent_countsOthers() {
        Quest active = Quest.builder().id("q-1").name("Q").campaignId(CAMP).order(0)
                .nodes(List.of(new QuestNodeRef(NodeType.CHAPTER, "chap-hot", 0))).build();
        when(questRepository.findByCampaignId(CAMP)).thenReturn(List.of(active));
        when(statusEnricher.computeAll(anyList(), eq(PT))).thenReturn(Map.of("q-1", QuestStatus.IN_PROGRESS));
        when(chapterRepository.findById("chap-hot"))
                .thenReturn(Optional.of(Chapter.builder().id("chap-hot").name("Hot").arcId("arc-1").build()));

        List<ReadinessGap> allGaps = List.of(
                gap(ReadinessEntityType.CHAPTER, "chap-hot", "chap-hot"),      // ciblé (chapitre probable)
                gap(ReadinessEntityType.SCENE, "scene-in-hot", "chap-hot"),     // ciblé (scène du chapitre probable)
                gap(ReadinessEntityType.QUEST, "q-1", null),                    // ciblé (quête active)
                gap(ReadinessEntityType.SCENE, "scene-far", "chap-cold"),       // ailleurs
                gap(ReadinessEntityType.ARC, "arc-9", null));                   // ailleurs
        when(readinessService.assess(CAMP)).thenReturn(new CampaignReadinessAssessment(
                CAMP, ReadinessStatus.DRAFT, Map.of(), allGaps));

        SessionPrepReport report = service.prepare(PT);

        assertEquals(3, report.gaps().size());
        assertEquals(2, report.otherGapCount());
    }

    @Test
    void noQuests_allGapsAreKept() {
        when(questRepository.findByCampaignId(CAMP)).thenReturn(List.of());
        when(statusEnricher.computeAll(anyList(), eq(PT))).thenReturn(Map.of());
        List<ReadinessGap> allGaps = List.of(
                gap(ReadinessEntityType.SCENE, "s-1", "c-1"),
                gap(ReadinessEntityType.ARC, "a-1", null));
        when(readinessService.assess(CAMP)).thenReturn(new CampaignReadinessAssessment(
                CAMP, ReadinessStatus.DRAFT, Map.of(), allGaps));

        SessionPrepReport report = service.prepare(PT);

        assertEquals(2, report.gaps().size());
        assertEquals(0, report.otherGapCount());
    }

    @Test
    void clocks_onlyStartedOnes_withFrontName() {
        when(questRepository.findByCampaignId(CAMP)).thenReturn(List.of());
        when(statusEnricher.computeAll(anyList(), eq(PT))).thenReturn(Map.of());
        stubEmptyAssessment();
        when(frontRepository.findByPlaythroughId(PT)).thenReturn(List.of(
                Front.builder().id("f-1").playthroughId(PT).name("La montée du Culte").build()));
        when(clockRepository.findByPlaythroughId(PT)).thenReturn(List.of(
                Clock.builder().id("cl-1").playthroughId(PT).name("Rituel").segments(4).filled(3).frontId("f-1").build(),
                Clock.builder().id("cl-2").playthroughId(PT).name("Intacte").segments(6).filled(0).build()));

        SessionPrepReport report = service.prepare(PT);

        assertEquals(1, report.clocks().size());
        SessionPrepReport.ClockInfo clock = report.clocks().get(0);
        assertEquals("Rituel", clock.name());
        assertEquals(3, clock.filled());
        assertEquals("La montée du Culte", clock.frontName());
    }

    @Test
    void lastSession_isTheLatestByStartDate() {
        when(questRepository.findByCampaignId(CAMP)).thenReturn(List.of());
        when(statusEnricher.computeAll(anyList(), eq(PT))).thenReturn(Map.of());
        stubEmptyAssessment();
        LocalDateTime old = LocalDateTime.of(2026, 6, 1, 20, 0);
        LocalDateTime recent = LocalDateTime.of(2026, 6, 23, 20, 0);
        when(sessionRepository.findByPlaythroughId(PT)).thenReturn(List.of(
                Session.builder().id("s-old").name("Séance 1").playthroughId(PT).startedAt(old).endedAt(old.plusHours(4)).build(),
                Session.builder().id("s-new").name("Séance 2").playthroughId(PT).startedAt(recent).endedAt(recent.plusHours(3)).build()));

        SessionPrepReport report = service.prepare(PT);

        assertNotNull(report.lastSession());
        assertEquals("s-new", report.lastSession().id());
        assertFalse(report.lastSession().active());
    }

    @Test
    void noSessions_lastSessionIsNull() {
        when(questRepository.findByCampaignId(CAMP)).thenReturn(List.of());
        when(statusEnricher.computeAll(anyList(), eq(PT))).thenReturn(Map.of());
        stubEmptyAssessment();

        assertNull(service.prepare(PT).lastSession());
    }
}
