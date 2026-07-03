package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Arc;
import com.loremind.domain.campaigncontext.ArcType;
import com.loremind.domain.campaigncontext.Chapter;
import com.loremind.domain.campaigncontext.Enemy;
import com.loremind.domain.campaigncontext.NodeType;
import com.loremind.domain.campaigncontext.Prerequisite;
import com.loremind.domain.campaigncontext.Quest;
import com.loremind.domain.campaigncontext.QuestNodeRef;
import com.loremind.domain.campaigncontext.ReadinessSeverity;
import com.loremind.domain.campaigncontext.ReadinessStatus;
import com.loremind.domain.campaigncontext.Room;
import com.loremind.domain.campaigncontext.RoomBranch;
import com.loremind.domain.campaigncontext.Scene;
import com.loremind.domain.campaigncontext.SceneBranch;
import com.loremind.domain.campaigncontext.ports.ArcRepository;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
import com.loremind.domain.campaigncontext.ports.EnemyRepository;
import com.loremind.domain.campaigncontext.ports.QuestRepository;
import com.loremind.domain.campaigncontext.ports.SceneRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Test unitaire pour {@link CampaignReadinessService} (Pilier B — guidage / readiness).
 * Mocks des ports Campaign Context ; les stubs manquants renvoient des listes vides.
 * Couvre chaque règle du périmètre MVP + l'agrégation de statut + le mode plat.
 */
@ExtendWith(MockitoExtension.class)
class CampaignReadinessServiceTest {

    private static final String CAMP = "camp";

    @Mock private CampaignRepository campaignRepository;
    @Mock private ArcRepository arcRepository;
    @Mock private ChapterRepository chapterRepository;
    @Mock private SceneRepository sceneRepository;
    @Mock private QuestRepository questRepository;
    @Mock private EnemyRepository enemyRepository;

    @InjectMocks private CampaignReadinessService service;

    // --- helpers ---

    private Arc arc(String id) {
        return Arc.builder().id(id).name(id).campaignId(CAMP).order(0).build();
    }

    private Chapter chapter(String id, String arcId) {
        return Chapter.builder().id(id).name(id).arcId(arcId).order(0).build();
    }

    private Scene scene(String id, String chapterId) {
        return Scene.builder().id(id).name(id).chapterId(chapterId).order(0).build();
    }

    private Set<String> ruleIds(CampaignReadinessAssessment a) {
        return a.gaps().stream().map(ReadinessGap::ruleId).collect(Collectors.toSet());
    }

    /** Câble un arbre à un seul arc/chapitre/liste de scènes. */
    private void tree(String arcId, String chapterId, List<Scene> scenes) {
        when(arcRepository.findByCampaignId(CAMP)).thenReturn(List.of(arc(arcId)));
        when(chapterRepository.findByArcId(arcId)).thenReturn(List.of(chapter(chapterId, arcId)));
        when(sceneRepository.findByChapterId(chapterId)).thenReturn(scenes);
    }

    // --- CAMP-001 : campagne vide ---

    @Test
    void emptyCampaign_isDraft_withNoContentGap() {
        // Tous les repos renvoient vide par défaut.
        CampaignReadinessAssessment a = service.assess(CAMP);

        assertEquals(ReadinessStatus.DRAFT, a.overallStatus());
        assertTrue(ruleIds(a).contains("CAMP-001-NO-CONTENT"));
        assertEquals(1, a.counts().get("BLOCKING"));
        assertEquals(0, a.counts().get("RECOMMENDED"));
    }

    @Test
    void campaignWithOneScene_notEmpty_noContentGap() {
        tree("arc-1", "chap-1", List.of(scene("scene-1", "chap-1")));

        CampaignReadinessAssessment a = service.assess(CAMP);

        assertFalse(ruleIds(a).contains("CAMP-001-NO-CONTENT"));
        assertEquals(ReadinessStatus.POLISHED, a.overallStatus());
        assertTrue(a.gaps().isEmpty());
    }

    // --- ARC-001 / CHAP-001 : vides ---

    @Test
    void emptyArc_yieldsArcGap() {
        when(arcRepository.findByCampaignId(CAMP)).thenReturn(List.of(arc("arc-1")));
        // chapterRepository.findByArcId -> vide par défaut
        // Une quête porte le contenu pour ne pas déclencher CAMP-001.
        Quest q = Quest.builder().id("q-1").name("Q").campaignId(CAMP)
                .nodes(List.of(new QuestNodeRef(NodeType.CHAPTER, "chap-x", 0))).build();
        when(questRepository.findByCampaignId(CAMP)).thenReturn(List.of(q));

        CampaignReadinessAssessment a = service.assess(CAMP);

        assertTrue(ruleIds(a).contains("ARC-001-EMPTY"));
        assertFalse(ruleIds(a).contains("CAMP-001-NO-CONTENT"));
    }

    @Test
    void hubArcWithAttachedQuest_isNotEmpty_noArcGap() {
        // Arc HUB sans chapitre MAIS avec une quête rattachée (arcId = arc) → pas ARC-001.
        Arc hub = Arc.builder().id("arc-h").name("Hub").campaignId(CAMP).type(ArcType.HUB).order(0).build();
        when(arcRepository.findByCampaignId(CAMP)).thenReturn(List.of(hub));
        Quest q = Quest.builder().id("q-1").name("Q").campaignId(CAMP).arcId("arc-h")
                .nodes(List.of(new QuestNodeRef(NodeType.CHAPTER, "chap-x", 0))).build();
        when(questRepository.findByCampaignId(CAMP)).thenReturn(List.of(q));

        assertFalse(ruleIds(service.assess(CAMP)).contains("ARC-001-EMPTY"));
    }

    @Test
    void hubArcWithoutChapterNorQuest_yieldsArcGap() {
        Arc hub = Arc.builder().id("arc-h").name("Hub").campaignId(CAMP).type(ArcType.HUB).order(0).build();
        when(arcRepository.findByCampaignId(CAMP)).thenReturn(List.of(hub));
        // Aucun chapitre, aucune quête (repos vides par défaut).
        assertTrue(ruleIds(service.assess(CAMP)).contains("ARC-001-EMPTY"));
    }

    @Test
    void linearArcWithAttachedQuest_stillYieldsArcGap() {
        // Les quêtes ne « sauvent » que les arcs HUB : un arc LINÉAIRE vide reste signalé,
        // même si une quête pointe dessus (état incohérent non produit par l'UI, mais toléré).
        Arc linear = Arc.builder().id("arc-l").name("Linear").campaignId(CAMP).type(ArcType.LINEAR).order(0).build();
        when(arcRepository.findByCampaignId(CAMP)).thenReturn(List.of(linear));
        Quest q = Quest.builder().id("q-1").name("Q").campaignId(CAMP).arcId("arc-l")
                .nodes(List.of(new QuestNodeRef(NodeType.CHAPTER, "chap-x", 0))).build();
        when(questRepository.findByCampaignId(CAMP)).thenReturn(List.of(q));

        assertTrue(ruleIds(service.assess(CAMP)).contains("ARC-001-EMPTY"));
    }

    @Test
    void chapterWithoutScene_yieldsChapterGap() {
        when(arcRepository.findByCampaignId(CAMP)).thenReturn(List.of(arc("arc-1")));
        when(chapterRepository.findByArcId("arc-1")).thenReturn(List.of(chapter("chap-1", "arc-1")));
        // sceneRepository.findByChapterId -> vide par défaut
        Quest q = Quest.builder().id("q-1").name("Q").campaignId(CAMP)
                .nodes(List.of(new QuestNodeRef(NodeType.CHAPTER, "chap-1", 0))).build();
        when(questRepository.findByCampaignId(CAMP)).thenReturn(List.of(q));

        CampaignReadinessAssessment a = service.assess(CAMP);

        assertTrue(ruleIds(a).contains("CHAP-001-NO-SCENE"));
    }

    // --- SCENE-001 : sans titre ---

    @Test
    void sceneWithoutName_yieldsBlockingGap() {
        Scene s = Scene.builder().id("scene-1").name("  ").chapterId("chap-1").order(0).build();
        tree("arc-1", "chap-1", List.of(s));

        CampaignReadinessAssessment a = service.assess(CAMP);

        assertTrue(ruleIds(a).contains("SCENE-001-NO-NAME"));
        assertEquals(ReadinessStatus.DRAFT, a.overallStatus());
    }

    // --- SCENE-010 : branches ---

    @Test
    void sceneBranchToGhost_isBroken() {
        Scene s1 = Scene.builder().id("scene-1").name("S1").chapterId("chap-1").order(0)
                .branches(List.of(SceneBranch.of("Aller", "ghost"))).build();
        Scene s2 = scene("scene-2", "chap-1");
        tree("arc-1", "chap-1", List.of(s1, s2));

        assertTrue(ruleIds(service.assess(CAMP)).contains("SCENE-010-BRANCH-INVALID"));
    }

    @Test
    void sceneBranchToSelf_isBroken() {
        Scene s1 = Scene.builder().id("scene-1").name("S1").chapterId("chap-1").order(0)
                .branches(List.of(SceneBranch.of("Boucle", "scene-1"))).build();
        tree("arc-1", "chap-1", List.of(s1));

        assertTrue(ruleIds(service.assess(CAMP)).contains("SCENE-010-BRANCH-INVALID"));
    }

    @Test
    void sceneBranchToSiblingInChapter_isValid() {
        Scene s1 = Scene.builder().id("scene-1").name("S1").chapterId("chap-1").order(0)
                .branches(List.of(SceneBranch.of("Aller", "scene-2"))).build();
        Scene s2 = scene("scene-2", "chap-1");
        tree("arc-1", "chap-1", List.of(s1, s2));

        assertFalse(ruleIds(service.assess(CAMP)).contains("SCENE-010-BRANCH-INVALID"));
    }

    // --- SCENE-011 : combat sans ennemi ---

    @Test
    void combatWithoutEnemy_yieldsRecommendedGap_andPlayableStatus() {
        Scene s = Scene.builder().id("scene-1").name("S1").chapterId("chap-1").order(0)
                .combatDifficulty("Moyen").build();
        tree("arc-1", "chap-1", List.of(s));

        CampaignReadinessAssessment a = service.assess(CAMP);

        assertTrue(ruleIds(a).contains("SCENE-011-COMBAT-NO-ENEMY"));
        assertEquals(ReadinessStatus.PLAYABLE, a.overallStatus());
        assertEquals(0, a.counts().get("BLOCKING"));
        assertEquals(1, a.counts().get("RECOMMENDED"));
    }

    @Test
    void combatWithFreeTextEnemies_noGap() {
        Scene s = Scene.builder().id("scene-1").name("S1").chapterId("chap-1").order(0)
                .combatDifficulty("Moyen").enemies("2 gobelins").build();
        tree("arc-1", "chap-1", List.of(s));

        assertFalse(ruleIds(service.assess(CAMP)).contains("SCENE-011-COMBAT-NO-ENEMY"));
    }

    @Test
    void combatWithResolvedEnemyId_noGap() {
        Scene s = Scene.builder().id("scene-1").name("S1").chapterId("chap-1").order(0)
                .combatDifficulty("Moyen").enemyIds(List.of("enemy-1")).build();
        tree("arc-1", "chap-1", List.of(s));
        when(enemyRepository.findByCampaignId(CAMP)).thenReturn(List.of(
                Enemy.builder().id("enemy-1").name("Gobelin").campaignId(CAMP).build()));

        Set<String> rules = ruleIds(service.assess(CAMP));
        assertFalse(rules.contains("SCENE-011-COMBAT-NO-ENEMY"));
        assertFalse(rules.contains("SCENE-012-ENEMY-REF-BROKEN"));
    }

    // --- SCENE-012 : réf d'ennemi cassée ---

    @Test
    void brokenEnemyRef_yieldsGap() {
        Scene s = Scene.builder().id("scene-1").name("S1").chapterId("chap-1").order(0)
                .enemyIds(List.of("ghost-enemy")).build();
        tree("arc-1", "chap-1", List.of(s));

        assertTrue(ruleIds(service.assess(CAMP)).contains("SCENE-012-ENEMY-REF-BROKEN"));
    }

    // --- SCENE-041 / SCENE-042 : pièces ---

    @Test
    void roomBranchToGhost_isBroken() {
        Room room = Room.builder().id("room-1").name("R1")
                .branches(List.of(new RoomBranch("Porte", "ghost-room", null))).build();
        Scene s = Scene.builder().id("scene-1").name("S1").chapterId("chap-1").order(0)
                .rooms(List.of(room)).build();
        tree("arc-1", "chap-1", List.of(s));

        assertTrue(ruleIds(service.assess(CAMP)).contains("SCENE-041-ROOMBRANCH-INVALID"));
    }

    @Test
    void roomEnemyRefBroken_yieldsGap() {
        Room room = Room.builder().id("room-1").name("R1").enemyIds(List.of("ghost-enemy")).build();
        Scene s = Scene.builder().id("scene-1").name("S1").chapterId("chap-1").order(0)
                .rooms(List.of(room)).build();
        tree("arc-1", "chap-1", List.of(s));

        assertTrue(ruleIds(service.assess(CAMP)).contains("SCENE-042-ROOM-ENEMY-BROKEN"));
    }

    @Test
    void roomWithoutEnemies_isLegitimate_noGap() {
        // Un lieu explorable purement descriptif (salle au trésor, énigme…) sans ennemi
        // est parfaitement valide : aucune règle ne doit s'en plaindre.
        Room room = Room.builder().id("room-1").name("Salle du trésor").enemyIds(List.of()).build();
        Scene s = Scene.builder().id("scene-1").name("Donjon").chapterId("chap-1").order(0)
                .rooms(List.of(room)).build();
        tree("arc-1", "chap-1", List.of(s));

        Set<String> rules = ruleIds(service.assess(CAMP));
        assertFalse(rules.contains("SCENE-042-ROOM-ENEMY-BROKEN"));
        assertFalse(rules.contains("SCENE-041-ROOMBRANCH-INVALID"));
    }

    // --- QUEST-001 / QUEST-010 / CAMP-010 ---

    @Test
    void questWithoutNodes_yieldsGap() {
        tree("arc-1", "chap-1", List.of(scene("scene-1", "chap-1")));
        Quest q = Quest.builder().id("q-1").name("Q").campaignId(CAMP).nodes(List.of()).build();
        when(questRepository.findByCampaignId(CAMP)).thenReturn(List.of(q));

        assertTrue(ruleIds(service.assess(CAMP)).contains("QUEST-001-NO-NODES"));
    }

    @Test
    void questNodeToGhostChapter_isBroken() {
        tree("arc-1", "chap-1", List.of(scene("scene-1", "chap-1")));
        Quest q = Quest.builder().id("q-1").name("Q").campaignId(CAMP)
                .nodes(List.of(new QuestNodeRef(NodeType.CHAPTER, "ghost-chap", 0))).build();
        when(questRepository.findByCampaignId(CAMP)).thenReturn(List.of(q));

        Set<String> rules = ruleIds(service.assess(CAMP));
        assertTrue(rules.contains("QUEST-010-NODE-REF-BROKEN"));
        assertFalse(rules.contains("QUEST-001-NO-NODES"));
    }

    @Test
    void questNodeToExistingChapter_isValid() {
        tree("arc-1", "chap-1", List.of(scene("scene-1", "chap-1")));
        Quest q = Quest.builder().id("q-1").name("Q").campaignId(CAMP)
                .nodes(List.of(new QuestNodeRef(NodeType.CHAPTER, "chap-1", 0))).build();
        when(questRepository.findByCampaignId(CAMP)).thenReturn(List.of(q));

        Set<String> rules = ruleIds(service.assess(CAMP));
        assertFalse(rules.contains("QUEST-010-NODE-REF-BROKEN"));
        assertEquals(ReadinessStatus.POLISHED, service.assess(CAMP).overallStatus());
    }

    @Test
    void danglingQuestPrerequisite_yieldsGap() {
        tree("arc-1", "chap-1", List.of(scene("scene-1", "chap-1")));
        Quest q = Quest.builder().id("q-1").name("Q").campaignId(CAMP)
                .nodes(List.of(new QuestNodeRef(NodeType.CHAPTER, "chap-1", 0)))
                .prerequisites(List.of(new Prerequisite.QuestCompleted("ghost-quest"))).build();
        when(questRepository.findByCampaignId(CAMP)).thenReturn(List.of(q));

        assertTrue(ruleIds(service.assess(CAMP)).contains("CAMP-010-DANGLING-QUEST-PREREQ"));
    }

    @Test
    void satisfiedQuestPrerequisite_noGap() {
        tree("arc-1", "chap-1", List.of(scene("scene-1", "chap-1")));
        Quest q1 = Quest.builder().id("q-1").name("Q1").campaignId(CAMP)
                .nodes(List.of(new QuestNodeRef(NodeType.CHAPTER, "chap-1", 0))).build();
        Quest q2 = Quest.builder().id("q-2").name("Q2").campaignId(CAMP)
                .nodes(List.of(new QuestNodeRef(NodeType.CHAPTER, "chap-1", 0)))
                .prerequisites(List.of(new Prerequisite.QuestCompleted("q-1"))).build();
        when(questRepository.findByCampaignId(CAMP)).thenReturn(List.of(q1, q2));

        assertFalse(ruleIds(service.assess(CAMP)).contains("CAMP-010-DANGLING-QUEST-PREREQ"));
    }

    // --- Mode plat : pas d'arc, quête porteuse de contenu ---

    @Test
    void flatMode_questCarriesContent_noEmptyCampaignGap() {
        // Aucun arc. Une quête avec des nœuds => la campagne n'est pas "vide".
        Quest q = Quest.builder().id("q-1").name("Q").campaignId(CAMP)
                .nodes(List.of(new QuestNodeRef(NodeType.SCENE, "scene-x", 0))).build();
        when(questRepository.findByCampaignId(CAMP)).thenReturn(List.of(q));

        assertFalse(ruleIds(service.assess(CAMP)).contains("CAMP-001-NO-CONTENT"));
    }

    // --- Agrégation : tri + statut ---

    @Test
    void aggregation_mixesSeverities_draftAndBlockingFirst() {
        // arc-1 vide (BLOQUANT) ; arc-2 -> chap-2 -> scène combat sans ennemi (RECOMMANDÉ).
        Scene combat = Scene.builder().id("scene-1").name("S1").chapterId("chap-2").order(0)
                .combatDifficulty("Difficile").build();
        when(arcRepository.findByCampaignId(CAMP)).thenReturn(List.of(arc("arc-1"), arc("arc-2")));
        when(chapterRepository.findByArcId("arc-1")).thenReturn(List.of());
        when(chapterRepository.findByArcId("arc-2")).thenReturn(List.of(chapter("chap-2", "arc-2")));
        when(sceneRepository.findByChapterId("chap-2")).thenReturn(List.of(combat));

        CampaignReadinessAssessment a = service.assess(CAMP);

        assertEquals(ReadinessStatus.DRAFT, a.overallStatus());
        assertTrue(a.counts().get("BLOCKING") >= 1);
        assertEquals(1, a.counts().get("RECOMMENDED"));
        // Tri : le premier gap est de sévérité BLOQUANTE.
        assertEquals(ReadinessSeverity.BLOCKING, a.gaps().get(0).severity());
    }

    // --- Contexte de navigation ---

    @Test
    void sceneGap_carriesArcAndChapterContext() {
        Scene s = Scene.builder().id("scene-1").name("  ").chapterId("chap-1").order(0).build();
        tree("arc-1", "chap-1", List.of(s));

        ReadinessGap gap = service.assess(CAMP).gaps().stream()
                .filter(g -> g.ruleId().equals("SCENE-001-NO-NAME")).findFirst().orElseThrow();

        assertEquals("arc-1", gap.arcId());
        assertEquals("chap-1", gap.chapterId());
        assertEquals("scene-1", gap.entityId());
    }
}
