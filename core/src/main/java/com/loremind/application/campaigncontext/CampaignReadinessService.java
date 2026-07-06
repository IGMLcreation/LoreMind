package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.structure.Arc;
import com.loremind.domain.campaigncontext.structure.ArcType;
import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.structure.Chapter;
import com.loremind.domain.campaigncontext.bestiary.Enemy;
import com.loremind.domain.campaigncontext.quest.NodeType;
import com.loremind.domain.campaigncontext.quest.Prerequisite;
import com.loremind.domain.campaigncontext.quest.Quest;
import com.loremind.domain.campaigncontext.readiness.ReadinessEntityType;
import com.loremind.domain.campaigncontext.readiness.ReadinessSeverity;
import com.loremind.domain.campaigncontext.readiness.ReadinessStatus;
import com.loremind.domain.campaigncontext.structure.Room;
import com.loremind.domain.campaigncontext.structure.Scene;
import com.loremind.domain.campaigncontext.structure.SceneBranch;
import com.loremind.domain.campaigncontext.ports.ArcRepository;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
import com.loremind.domain.campaigncontext.ports.EnemyRepository;
import com.loremind.domain.campaigncontext.ports.QuestRepository;
import com.loremind.domain.campaigncontext.ports.SceneRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service applicatif du Pilier B (« guidage / readiness ») : calcule à la volée
 * l'état de préparation d'une campagne et la liste des manques (« gaps ») à combler,
 * chacun cliquable vers l'éditeur concerné côté front.
 *
 * <p><b>Déterministe, sans IA, sans persistance</b> (aucune colonne readiness — recalcul
 * à chaque appel, comme le statut de quête). <b>Orthogonalité stricte</b> : n'injecte
 * AUCUN repository du Play Context ; le readiness ne dépend jamais d'un Playthrough,
 * d'un flag ou d'une progression. Purement indicatif : aucune sévérité ne bloque une action.</p>
 *
 * <p>Chargement en une passe façon {@code CampaignStructuralContextBuilder} : arcs →
 * chapitres (par arc) → scènes (par chapitre), + quêtes et bestiaire de la campagne
 * chargés une seule fois, indexés par id pour résoudre les références faibles sans N+1.</p>
 *
 * <p>Périmètre MVP : le noyau BLOQUANT (vides, branches/portes cassées, quête sans nœud
 * ou à nœud mort, prérequis cassé) + le trio « combat » RECOMMANDÉ (combat annoncé sans
 * ennemi, réf d'ennemi cassée en scène et en pièce). Les règles narratives/ambiance et
 * les orphelins (état que l'UI empêche) sont hors MVP.</p>
 */
@Service
public class CampaignReadinessService {

    private static final String SCENE_FALLBACK_NAME = "Scène";

    private final CampaignRepository campaignRepository;
    private final ArcRepository arcRepository;
    private final ChapterRepository chapterRepository;
    private final SceneRepository sceneRepository;
    private final QuestRepository questRepository;
    private final EnemyRepository enemyRepository;

    public CampaignReadinessService(CampaignRepository campaignRepository,
                                    ArcRepository arcRepository,
                                    ChapterRepository chapterRepository,
                                    SceneRepository sceneRepository,
                                    QuestRepository questRepository,
                                    EnemyRepository enemyRepository) {
        this.campaignRepository = campaignRepository;
        this.arcRepository = arcRepository;
        this.chapterRepository = chapterRepository;
        this.sceneRepository = sceneRepository;
        this.questRepository = questRepository;
        this.enemyRepository = enemyRepository;
    }

    /** Évalue la préparation d'une campagne (arbre + quêtes) et agrège son statut. */
    public CampaignReadinessAssessment assess(String campaignId) {
        List<ReadinessGap> gaps = new ArrayList<>();

        String campaignName = campaignRepository.findById(campaignId)
                .map(Campaign::getName).orElse(null);

        // Bestiaire de la campagne, indexé pour résoudre les weak refs sans N+1.
        // On écarte les ids vides/blancs : ils ne doivent jamais « résoudre » une référence.
        Set<String> enemyIds = enemyRepository.findByCampaignId(campaignId).stream()
                .map(Enemy::getId).filter(id -> !isBlank(id)).collect(Collectors.toSet());

        List<Quest> quests = questRepository.findByCampaignId(campaignId);
        // Arcs HUB « portés » par ≥1 quête rattachée : ne comptent PAS comme vides
        // (un arc HUB contient des quêtes, un arc LINÉAIRE des chapitres).
        Set<String> arcsWithQuests = quests.stream()
                .map(Quest::getArcId).filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        List<Arc> arcs = new ArrayList<>(arcRepository.findByCampaignId(campaignId));
        arcs.sort(Comparator.comparingInt(Arc::getOrder));

        // Index global chapitres / scènes (cibles possibles des nœuds de quête).
        Set<String> allChapterIds = new HashSet<>();
        Set<String> allSceneIds = new HashSet<>();
        int totalScenes = 0;
        for (Arc arc : arcs) {
            ArcScan scan = checkArc(arc, arcsWithQuests, enemyIds, gaps);
            allChapterIds.addAll(scan.chapterIds());
            allSceneIds.addAll(scan.sceneIds());
            totalScenes += scan.sceneCount();
        }

        // Campagne vide : ni scène jouable, ni quête porteuse de contenu (couvre le mode plat).
        if (totalScenes == 0 && !anyQuestHasNodes(quests)) {
            gaps.add(new ReadinessGap(ReadinessEntityType.CAMPAIGN, campaignId, campaignName,
                    "CAMP-001-NO-CONTENT",
                    "Campagne vide : ajoutez un arc avec une scène, ou créez une quête, pour commencer à jouer.",
                    ReadinessSeverity.BLOCKING, null, null));
        }

        Set<String> questIds = quests.stream()
                .map(Quest::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        for (Quest quest : quests) {
            checkQuest(quest, allChapterIds, allSceneIds, questIds, gaps);
        }

        return aggregate(campaignId, gaps);
    }

    /** Résultat du scan d'un arc : chapitres/scènes indexés (cibles des nœuds de quête) + total scènes. */
    private record ArcScan(Set<String> chapterIds, Set<String> sceneIds, int sceneCount) {}

    private ArcScan checkArc(Arc arc, Set<String> arcsWithQuests, Set<String> enemyIds, List<ReadinessGap> gaps) {
        List<Chapter> chapters = chapterRepository.findByArcId(arc.getId());
        // Arc SYSTEM (conteneurs des quêtes libres) : jamais « vide » — c'est de la
        // plomberie invisible. Ses chapitres restent analysés (CHAP-001 des conteneurs).
        boolean hubCoveredByQuest = arc.getType() == ArcType.HUB && arcsWithQuests.contains(arc.getId());
        if (chapters.isEmpty() && !hubCoveredByQuest && arc.getType() != ArcType.SYSTEM) {
            gaps.add(emptyArcGap(arc));
        }

        Set<String> chapterIds = new HashSet<>();
        Set<String> sceneIds = new HashSet<>();
        int sceneCount = 0;
        for (Chapter chapter : chapters) {
            chapterIds.add(chapter.getId());
            List<Scene> scenes = sceneRepository.findByChapterId(chapter.getId());
            if (scenes.isEmpty()) {
                gaps.add(new ReadinessGap(ReadinessEntityType.CHAPTER, chapter.getId(),
                        labelOr(chapter.getName(), "Chapitre"), "CHAP-001-NO-SCENE",
                        "Chapitre vide : ajoutez au moins une scène pour pouvoir le jouer.",
                        ReadinessSeverity.BLOCKING, arc.getId(), chapter.getId()));
            }
            Set<String> chapterSceneIds = scenes.stream()
                    .map(Scene::getId).filter(Objects::nonNull).collect(Collectors.toSet());
            sceneCount += scenes.size();
            for (Scene scene : scenes) {
                sceneIds.add(scene.getId());
                checkScene(scene, arc.getId(), chapter.getId(), chapterSceneIds, enemyIds, gaps);
            }
        }
        return new ArcScan(chapterIds, sceneIds, sceneCount);
    }

    private ReadinessGap emptyArcGap(Arc arc) {
        String msg = arc.getType() == ArcType.HUB
                ? "Arc vide : ajoutez une quête (ou un chapitre), ou supprimez-le."
                : "Arc vide : ajoutez un chapitre, ou supprimez-le.";
        return new ReadinessGap(ReadinessEntityType.ARC, arc.getId(), labelOr(arc.getName(), "Arc"),
                "ARC-001-EMPTY", msg, ReadinessSeverity.BLOCKING, arc.getId(), null);
    }

    private static boolean anyQuestHasNodes(List<Quest> quests) {
        return quests.stream().anyMatch(q -> q.getNodes() != null && !q.getNodes().isEmpty());
    }

    private void checkScene(Scene scene, String arcId, String chapterId,
                            Set<String> chapterSceneIds, Set<String> enemyIds, List<ReadinessGap> gaps) {
        checkSceneName(scene, arcId, chapterId, gaps);
        checkSceneBranches(scene, arcId, chapterId, chapterSceneIds, gaps);
        checkSceneCombat(scene, arcId, chapterId, enemyIds, gaps);
        checkSceneEnemyRefs(scene, arcId, chapterId, enemyIds, gaps);
        checkSceneRooms(scene, arcId, chapterId, enemyIds, gaps);
    }

    /** SCENE-001 — scène sans titre. */
    private void checkSceneName(Scene scene, String arcId, String chapterId, List<ReadinessGap> gaps) {
        if (isBlank(scene.getName())) {
            gaps.add(sceneGap(scene, arcId, chapterId, "SCENE-001-NO-NAME",
                    "Scène sans titre : donnez-lui un nom pour l'identifier et la jouer.",
                    ReadinessSeverity.BLOCKING));
        }
    }

    /** SCENE-010 — branche de sortie cassée (vide / hors chapitre / auto-référence). */
    private void checkSceneBranches(Scene scene, String arcId, String chapterId,
                                     Set<String> chapterSceneIds, List<ReadinessGap> gaps) {
        List<SceneBranch> branches = scene.getBranches();
        if (branches == null) return;
        boolean invalid = branches.stream().anyMatch(b ->
                isBlank(b.targetSceneId())
                        || b.targetSceneId().equals(scene.getId())
                        || !chapterSceneIds.contains(b.targetSceneId()));
        if (invalid) {
            gaps.add(sceneGap(scene, arcId, chapterId, "SCENE-010-BRANCH-INVALID",
                    "Branche cassée : une sortie de « " + labelOr(scene.getName(), SCENE_FALLBACK_NAME)
                            + " » pointe dans le vide, hors du chapitre, ou sur elle-même.",
                    ReadinessSeverity.BLOCKING));
        }
    }

    /** SCENE-011 — combat annoncé sans adversaire (règle produit clé). */
    private void checkSceneCombat(Scene scene, String arcId, String chapterId,
                                   Set<String> enemyIds, List<ReadinessGap> gaps) {
        if (isBlank(scene.getCombatDifficulty())) return;
        boolean hasEnemyText = !isBlank(scene.getEnemies());
        boolean hasResolvedEnemy = scene.getEnemyIds() != null
                && scene.getEnemyIds().stream().anyMatch(id -> !isBlank(id) && enemyIds.contains(id));
        if (!hasEnemyText && !hasResolvedEnemy) {
            gaps.add(sceneGap(scene, arcId, chapterId, "SCENE-011-COMBAT-NO-ENEMY",
                    "Combat annoncé sans adversaire : ajoutez une fiche du bestiaire ou décrivez les ennemis.",
                    ReadinessSeverity.RECOMMENDED));
        }
    }

    /** SCENE-012 — référence d'ennemi cassée (fiche supprimée). */
    private void checkSceneEnemyRefs(Scene scene, String arcId, String chapterId,
                                      Set<String> enemyIds, List<ReadinessGap> gaps) {
        if (scene.getEnemyIds() == null) return;
        boolean broken = scene.getEnemyIds().stream().anyMatch(id -> !isBlank(id) && !enemyIds.contains(id));
        if (broken) {
            gaps.add(sceneGap(scene, arcId, chapterId, "SCENE-012-ENEMY-REF-BROKEN",
                    "Ennemi introuvable : « " + labelOr(scene.getName(), SCENE_FALLBACK_NAME)
                            + " » référence une fiche du bestiaire supprimée. Retirez la référence ou recréez la fiche.",
                    ReadinessSeverity.RECOMMENDED));
        }
    }

    /** SCENE-041 / SCENE-042 — pièces explorables : portes cassées + ennemis fantômes. */
    private void checkSceneRooms(Scene scene, String arcId, String chapterId,
                                  Set<String> enemyIds, List<ReadinessGap> gaps) {
        List<Room> rooms = scene.getRooms();
        if (rooms == null || rooms.isEmpty()) return;
        String name = labelOr(scene.getName(), SCENE_FALLBACK_NAME);
        Set<String> roomIds = rooms.stream()
                .map(Room::getId).filter(Objects::nonNull).collect(Collectors.toSet());

        if (rooms.stream().anyMatch(room -> hasInvalidBranch(room, roomIds))) {
            gaps.add(sceneGap(scene, arcId, chapterId, "SCENE-041-ROOMBRANCH-INVALID",
                    "Porte cassée : dans « " + name
                            + " », une sortie de pièce pointe hors de la scène ou dans le vide.",
                    ReadinessSeverity.BLOCKING));
        }
        if (rooms.stream().anyMatch(room -> hasBrokenEnemyRef(room, enemyIds))) {
            gaps.add(sceneGap(scene, arcId, chapterId, "SCENE-042-ROOM-ENEMY-BROKEN",
                    "Ennemi introuvable dans une pièce de « " + name
                            + " » : la référence pointe vers une fiche supprimée.",
                    ReadinessSeverity.RECOMMENDED));
        }
    }

    private static boolean hasInvalidBranch(Room room, Set<String> roomIds) {
        if (room.getBranches() == null) return false;
        return room.getBranches().stream().anyMatch(rb ->
                isBlank(rb.targetRoomId())
                        || rb.targetRoomId().equals(room.getId())
                        || !roomIds.contains(rb.targetRoomId()));
    }

    private static boolean hasBrokenEnemyRef(Room room, Set<String> enemyIds) {
        if (room.getEnemyIds() == null) return false;
        return room.getEnemyIds().stream().anyMatch(id -> !isBlank(id) && !enemyIds.contains(id));
    }

    private void checkQuest(Quest quest, Set<String> allChapterIds, Set<String> allSceneIds,
                            Set<String> questIds, List<ReadinessGap> gaps) {
        String name = labelOr(quest.getName(), "Quête");

        // QUEST-001 — quête sans nœud ; sinon QUEST-010 — nœud pointant dans le vide.
        if (quest.getNodes() == null || quest.getNodes().isEmpty()) {
            gaps.add(questGap(quest, "QUEST-001-NO-NODES",
                    "Quête sans contenu : ajoutez au moins un chapitre ou une scène à « " + name + " »."));
        } else if (quest.getNodes().stream().anyMatch(n ->
                n.nodeType() == null
                        || isBlank(n.nodeId())
                        || (n.nodeType() == NodeType.CHAPTER && !allChapterIds.contains(n.nodeId()))
                        || (n.nodeType() == NodeType.SCENE && !allSceneIds.contains(n.nodeId())))) {
            gaps.add(questGap(quest, "QUEST-010-NODE-REF-BROKEN",
                    "Nœud de quête cassé : dans « " + name
                            + " », un chapitre ou une scène référencé n'existe plus."));
        }

        // CAMP-010 — prérequis QuestCompleted pointant une quête disparue.
        if (quest.getPrerequisites() != null && quest.getPrerequisites().stream()
                .filter(p -> p instanceof Prerequisite.QuestCompleted)
                .map(p -> ((Prerequisite.QuestCompleted) p).questId())
                .anyMatch(qid -> isBlank(qid) || !questIds.contains(qid))) {
            gaps.add(questGap(quest, "CAMP-010-DANGLING-QUEST-PREREQ",
                    "Prérequis cassé : « " + name
                            + " » dépend d'une quête qui n'existe plus. Corrigez la condition de déblocage."));
        }
    }

    private CampaignReadinessAssessment aggregate(String campaignId, List<ReadinessGap> gaps) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(ReadinessSeverity.BLOCKING.name(), 0);
        counts.put(ReadinessSeverity.RECOMMENDED.name(), 0);
        counts.put(ReadinessSeverity.OPTIONAL.name(), 0);
        for (ReadinessGap g : gaps) {
            counts.merge(g.severity().name(), 1, Integer::sum);
        }

        ReadinessStatus status;
        if (counts.get(ReadinessSeverity.BLOCKING.name()) > 0) {
            status = ReadinessStatus.DRAFT;
        } else if (counts.get(ReadinessSeverity.RECOMMENDED.name()) > 0) {
            status = ReadinessStatus.PLAYABLE;
        } else {
            status = ReadinessStatus.POLISHED;
        }

        gaps.sort(Comparator.comparingInt(g -> severityRank(g.severity())));
        return new CampaignReadinessAssessment(campaignId, status, counts, gaps);
    }

    private ReadinessGap sceneGap(Scene scene, String arcId, String chapterId,
                                  String ruleId, String message, ReadinessSeverity severity) {
        return new ReadinessGap(ReadinessEntityType.SCENE, scene.getId(),
                labelOr(scene.getName(), SCENE_FALLBACK_NAME), ruleId, message, severity, arcId, chapterId);
    }

    private ReadinessGap questGap(Quest quest, String ruleId, String message) {
        return new ReadinessGap(ReadinessEntityType.QUEST, quest.getId(),
                labelOr(quest.getName(), "Quête"), ruleId, message, ReadinessSeverity.BLOCKING, null, null);
    }

    private static int severityRank(ReadinessSeverity severity) {
        return switch (severity) {
            case BLOCKING -> 0;
            case RECOMMENDED -> 1;
            case OPTIONAL -> 2;
        };
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String labelOr(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }
}
