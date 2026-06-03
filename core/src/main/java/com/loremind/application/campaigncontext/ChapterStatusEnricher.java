package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Chapter;
import com.loremind.domain.campaigncontext.PrerequisiteEvaluator;
import com.loremind.domain.campaigncontext.ProgressionStatus;
import com.loremind.domain.campaigncontext.QuestStatus;
import com.loremind.domain.playcontext.QuestProgression;
import com.loremind.domain.playcontext.ports.PlaythroughFlagRepository;
import com.loremind.domain.playcontext.ports.PlaythroughRepository;
import com.loremind.domain.playcontext.ports.QuestProgressionRepository;
import com.loremind.domain.playcontext.ports.SessionRepository;
import com.loremind.infrastructure.web.dto.campaigncontext.ChapterDTO;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service applicatif : enrichit des ChapterDTO avec leur {@link QuestStatus} effectif,
 * relatif à un Playthrough donné.
 *
 * <p>Depuis l'introduction de Playthrough : la progression et les flags vivent au niveau
 * de la Partie, plus de la Campagne. L'enrichissement nécessite donc un playthroughId.</p>
 */
@Service
public class ChapterStatusEnricher {

    private final PlaythroughRepository playthroughRepository;
    private final QuestProgressionRepository progressionRepository;
    private final PlaythroughFlagRepository flagRepository;
    private final SessionRepository sessionRepository;
    private final PrerequisiteEvaluator evaluator = new PrerequisiteEvaluator();

    public ChapterStatusEnricher(PlaythroughRepository playthroughRepository,
                                 QuestProgressionRepository progressionRepository,
                                 PlaythroughFlagRepository flagRepository,
                                 SessionRepository sessionRepository) {
        this.playthroughRepository = playthroughRepository;
        this.progressionRepository = progressionRepository;
        this.flagRepository = flagRepository;
        this.sessionRepository = sessionRepository;
    }

    /** Contexte d'évaluation + map chapterId -> ProgressionStatus pour ce Playthrough. */
    public record PlaythroughEvalSnapshot(
            PrerequisiteEvaluator.EvaluationContext ctx,
            Map<String, ProgressionStatus> progressionByChapterId
    ) {}

    /** Construit le snapshot d'évaluation pour un Playthrough. */
    public PlaythroughEvalSnapshot buildSnapshot(String playthroughId) {
        if (playthroughId == null || playthroughRepository.findById(playthroughId).isEmpty()) {
            return new PlaythroughEvalSnapshot(
                    new PrerequisiteEvaluator.EvaluationContext(Collections.emptySet(), 0, Collections.emptyMap()),
                    Collections.emptyMap()
            );
        }
        Map<String, Boolean> flags = flagRepository.findByPlaythroughId(playthroughId);
        Set<String> completedQuestIds = progressionRepository.findCompletedChapterIdsByPlaythroughId(playthroughId);
        int sessionCount = sessionRepository.findByPlaythroughId(playthroughId).size();

        Map<String, ProgressionStatus> progressionMap = new HashMap<>();
        for (QuestProgression qp : progressionRepository.findByPlaythroughId(playthroughId)) {
            progressionMap.put(qp.getChapterId(), qp.getStatus());
        }

        return new PlaythroughEvalSnapshot(
                new PrerequisiteEvaluator.EvaluationContext(completedQuestIds, sessionCount, flags),
                progressionMap
        );
    }

    /** Calcule le statut effectif d'un seul chapitre relatif à un Playthrough. */
    public QuestStatus computeFor(Chapter chapter, String playthroughId) {
        PlaythroughEvalSnapshot snap = buildSnapshot(playthroughId);
        ProgressionStatus progression = snap.progressionByChapterId()
                .getOrDefault(chapter.getId(), ProgressionStatus.NOT_STARTED);
        return evaluator.computeStatus(progression, chapter.getPrerequisites(), snap.ctx());
    }

    /**
     * Injecte le {@code effectiveStatus} et le {@code progressionStatus} dans une liste de DTOs.
     * Un seul build du snapshot pour toute la liste (optimal pour les vues qui listent un arc).
     */
    public void enrich(List<ChapterDTO> dtos, List<Chapter> domain, String playthroughId) {
        if (dtos == null || dtos.isEmpty()) return;
        PlaythroughEvalSnapshot snap = buildSnapshot(playthroughId);
        Map<String, Chapter> byId = domain.stream()
                .collect(Collectors.toMap(Chapter::getId, c -> c));
        for (ChapterDTO dto : dtos) {
            Chapter c = byId.get(dto.getId());
            if (c == null) continue;
            ProgressionStatus progression = snap.progressionByChapterId()
                    .getOrDefault(c.getId(), ProgressionStatus.NOT_STARTED);
            QuestStatus status = evaluator.computeStatus(progression, c.getPrerequisites(), snap.ctx());
            dto.setProgressionStatus(progression.name());
            dto.setEffectiveStatus(status.name());
        }
    }
}
