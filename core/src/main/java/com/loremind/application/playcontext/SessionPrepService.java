package com.loremind.application.playcontext;

import com.loremind.application.campaigncontext.CampaignReadinessAssessment;
import com.loremind.application.campaigncontext.CampaignReadinessService;
import com.loremind.application.campaigncontext.QuestStatusEnricher;
import com.loremind.application.campaigncontext.ReadinessGap;
import com.loremind.domain.campaigncontext.structure.Chapter;
import com.loremind.domain.campaigncontext.quest.Quest;
import com.loremind.domain.campaigncontext.quest.QuestNodeRef;
import com.loremind.domain.campaigncontext.quest.QuestStatus;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
import com.loremind.domain.campaigncontext.ports.QuestRepository;
import com.loremind.domain.campaigncontext.ports.SceneRepository;
import com.loremind.domain.playcontext.Front;
import com.loremind.domain.playcontext.Playthrough;
import com.loremind.domain.playcontext.Session;
import com.loremind.domain.playcontext.ports.ClockRepository;
import com.loremind.domain.playcontext.ports.FrontRepository;
import com.loremind.domain.playcontext.ports.PlaythroughRepository;
import com.loremind.domain.playcontext.ports.SessionRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Read-model « Préparer la prochaine séance » (Phase 3 co-MJ). Croise, pour UNE Partie :
 *
 * <ol>
 *   <li><b>Position des joueurs</b> — quêtes EN COURS / DISPONIBLES (statut effectif via
 *       {@link QuestStatusEnricher}, un seul snapshot) + dernière séance ;</li>
 *   <li><b>Contenu probable</b> — les chapitres/scènes traversés par ces quêtes actives ;</li>
 *   <li><b>Manques ciblés</b> — les gaps du guidage ({@link CampaignReadinessService})
 *       restreints à ce contenu probable : « quoi combler AVANT la prochaine séance » ;</li>
 *   <li><b>Menaces en mouvement</b> — horloges entamées, avec leur front.</li>
 * </ol>
 *
 * <p>Déterministe, sans IA, zéro persistance. Si la campagne n'utilise pas de quêtes, la
 * notion de « contenu probable » n'existe pas : on renvoie alors TOUS les manques (toute
 * la campagne est potentiellement la prochaine séance).</p>
 */
@Service
public class SessionPrepService {

    private final PlaythroughRepository playthroughRepository;
    private final SessionRepository sessionRepository;
    private final ClockRepository clockRepository;
    private final FrontRepository frontRepository;
    private final QuestRepository questRepository;
    private final ChapterRepository chapterRepository;
    private final SceneRepository sceneRepository;
    private final QuestStatusEnricher statusEnricher;
    private final CampaignReadinessService readinessService;

    public SessionPrepService(PlaythroughRepository playthroughRepository,
                              SessionRepository sessionRepository,
                              ClockRepository clockRepository,
                              FrontRepository frontRepository,
                              QuestRepository questRepository,
                              ChapterRepository chapterRepository,
                              SceneRepository sceneRepository,
                              QuestStatusEnricher statusEnricher,
                              CampaignReadinessService readinessService) {
        this.playthroughRepository = playthroughRepository;
        this.sessionRepository = sessionRepository;
        this.clockRepository = clockRepository;
        this.frontRepository = frontRepository;
        this.questRepository = questRepository;
        this.chapterRepository = chapterRepository;
        this.sceneRepository = sceneRepository;
        this.statusEnricher = statusEnricher;
        this.readinessService = readinessService;
    }

    public SessionPrepReport prepare(String playthroughId) {
        Playthrough playthrough = playthroughRepository.findById(playthroughId)
                .orElseThrow(() -> new IllegalArgumentException("Partie non trouvée: " + playthroughId));
        String campaignId = playthrough.getCampaignId();

        // 1) Position : quêtes actives + dernière séance.
        List<Quest> quests = questRepository.findByCampaignId(campaignId);
        Map<String, QuestStatus> statusById = statusEnricher.computeAll(quests, playthroughId);

        List<Quest> inProgress = byStatus(quests, statusById, QuestStatus.IN_PROGRESS);
        List<Quest> available = byStatus(quests, statusById, QuestStatus.AVAILABLE);
        List<Quest> completed = byStatus(quests, statusById, QuestStatus.COMPLETED);

        // 2) Contenu probable : nœuds des quêtes actives (en cours d'abord), dédupliqués.
        LinkedHashSet<String> hotspotChapterIds = new LinkedHashSet<>();
        LinkedHashSet<String> hotspotSceneIds = new LinkedHashSet<>();
        List<SessionPrepReport.NodeInfo> hotspots = new ArrayList<>();
        for (Quest quest : concat(inProgress, available)) {
            for (QuestNodeRef node : nullSafe(quest.getNodes())) {
                resolveNode(node, hotspotChapterIds, hotspotSceneIds, hotspots);
            }
        }

        // 3) Manques ciblés sur ce contenu probable. Sans quête, tout est « probable ».
        Set<String> hotspotQuestIds = concat(inProgress, available).stream()
                .map(Quest::getId).collect(Collectors.toSet());
        CampaignReadinessAssessment assessment = readinessService.assess(campaignId);
        List<ReadinessGap> focused;
        if (quests.isEmpty()) {
            focused = assessment.gaps();
        } else {
            focused = assessment.gaps().stream()
                    .filter(g -> isFocused(g, hotspotChapterIds, hotspotSceneIds, hotspotQuestIds))
                    .toList();
        }
        int otherGapCount = assessment.gaps().size() - focused.size();

        // 4) Menaces en mouvement : horloges entamées, avec le nom de leur front.
        Map<String, String> frontNames = frontRepository.findByPlaythroughId(playthroughId).stream()
                .collect(Collectors.toMap(Front::getId, Front::getName, (a, b) -> a));
        List<SessionPrepReport.ClockInfo> clocks = clockRepository.findByPlaythroughId(playthroughId).stream()
                .filter(c -> c.getFilled() > 0)
                .map(c -> new SessionPrepReport.ClockInfo(c.getId(), c.getName(), c.getSegments(),
                        c.getFilled(), c.getFrontId() != null ? frontNames.get(c.getFrontId()) : null))
                .toList();

        return new SessionPrepReport(
                playthroughId,
                lastSessionOf(playthroughId),
                toQuestInfos(inProgress),
                toQuestInfos(available),
                toQuestInfos(completed),
                hotspots,
                focused,
                otherGapCount,
                clocks);
    }

    /** Résout un nœud de quête vers son info navigable ; les refs mortes sont ignorées. */
    private void resolveNode(QuestNodeRef node,
                             Set<String> chapterIds, Set<String> sceneIds,
                             List<SessionPrepReport.NodeInfo> out) {
        if (node == null || node.nodeId() == null || node.nodeType() == null) return;
        switch (node.nodeType()) {
            case CHAPTER -> chapterRepository.findById(node.nodeId()).ifPresent(ch -> {
                if (chapterIds.add(ch.getId())) {
                    out.add(new SessionPrepReport.NodeInfo("CHAPTER", ch.getId(), ch.getName(), ch.getArcId(), ch.getId()));
                }
            });
            case SCENE -> sceneRepository.findById(node.nodeId()).ifPresent(sc -> {
                if (sceneIds.add(sc.getId())) {
                    String arcId = chapterRepository.findById(sc.getChapterId())
                            .map(Chapter::getArcId).orElse(null);
                    out.add(new SessionPrepReport.NodeInfo("SCENE", sc.getId(), sc.getName(), arcId, sc.getChapterId()));
                }
            });
        }
    }

    /** Un gap est « ciblé » s'il touche une entité probable (ou une scène d'un chapitre probable). */
    private boolean isFocused(ReadinessGap gap,
                              Set<String> chapterIds, Set<String> sceneIds, Set<String> questIds) {
        return switch (gap.entityType()) {
            case SCENE -> sceneIds.contains(gap.entityId())
                    || (gap.chapterId() != null && chapterIds.contains(gap.chapterId()));
            case CHAPTER -> chapterIds.contains(gap.entityId());
            case QUEST -> questIds.contains(gap.entityId());
            default -> false;
        };
    }

    private SessionPrepReport.LastSessionInfo lastSessionOf(String playthroughId) {
        List<Session> sessions = sessionRepository.findByPlaythroughId(playthroughId);
        return sessions.stream()
                .max(Comparator.comparing(Session::getStartedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(s -> new SessionPrepReport.LastSessionInfo(
                        s.getId(), s.getName(), s.getStartedAt(), s.getEndedAt(), s.isActive()))
                .orElse(null);
    }

    private static List<Quest> byStatus(List<Quest> quests, Map<String, QuestStatus> statusById, QuestStatus wanted) {
        return quests.stream()
                .filter(q -> statusById.get(q.getId()) == wanted)
                .sorted(Comparator.comparingInt(Quest::getOrder))
                .toList();
    }

    private static List<SessionPrepReport.QuestInfo> toQuestInfos(List<Quest> quests) {
        return quests.stream()
                .map(q -> new SessionPrepReport.QuestInfo(q.getId(), q.getName(), q.getIcon()))
                .toList();
    }

    private static List<Quest> concat(List<Quest> a, List<Quest> b) {
        List<Quest> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list != null ? list : List.of();
    }
}
