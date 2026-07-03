package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.PrerequisiteEvaluator;
import com.loremind.domain.campaigncontext.ProgressionStatus;
import com.loremind.domain.campaigncontext.Quest;
import com.loremind.domain.campaigncontext.QuestStatus;
import com.loremind.domain.playcontext.QuestProgression;
import com.loremind.domain.playcontext.ports.PlaythroughFlagRepository;
import com.loremind.domain.playcontext.ports.PlaythroughRepository;
import com.loremind.domain.playcontext.ports.QuestProgressionRepository;
import com.loremind.domain.playcontext.ports.SessionRepository;
import com.loremind.infrastructure.web.dto.campaigncontext.QuestDTO;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service applicatif : enrichit des {@link QuestDTO} avec leur {@link QuestStatus}
 * effectif, relatif à un Playthrough donné.
 *
 * <p>Depuis l'introduction de Playthrough, la progression et les flags vivent au niveau
 * de la Partie. L'enrichissement nécessite donc un playthroughId ; sans lui (ou s'il est
 * inconnu), le snapshot est vide et tout est NOT_STARTED / AVAILABLE.</p>
 */
@Service
public class QuestStatusEnricher {

    private final PlaythroughRepository playthroughRepository;
    private final QuestProgressionRepository progressionRepository;
    private final PlaythroughFlagRepository flagRepository;
    private final SessionRepository sessionRepository;
    private final PrerequisiteEvaluator evaluator = new PrerequisiteEvaluator();

    public QuestStatusEnricher(PlaythroughRepository playthroughRepository,
                               QuestProgressionRepository progressionRepository,
                               PlaythroughFlagRepository flagRepository,
                               SessionRepository sessionRepository) {
        this.playthroughRepository = playthroughRepository;
        this.progressionRepository = progressionRepository;
        this.flagRepository = flagRepository;
        this.sessionRepository = sessionRepository;
    }

    /** Contexte d'évaluation + map questId -> ProgressionStatus pour ce Playthrough. */
    public record PlaythroughEvalSnapshot(
            PrerequisiteEvaluator.EvaluationContext ctx,
            Map<String, ProgressionStatus> progressionByQuestId
    ) {}

    /** Construit le snapshot d'évaluation pour un Playthrough (court-circuit si null / inconnu). */
    public PlaythroughEvalSnapshot buildSnapshot(String playthroughId) {
        if (playthroughId == null || playthroughRepository.findById(playthroughId).isEmpty()) {
            return new PlaythroughEvalSnapshot(
                    new PrerequisiteEvaluator.EvaluationContext(Collections.emptySet(), 0, Collections.emptyMap()),
                    Collections.emptyMap()
            );
        }
        Map<String, Boolean> flags = flagRepository.findByPlaythroughId(playthroughId);
        Set<String> completedQuestIds = progressionRepository.findCompletedQuestIdsByPlaythroughId(playthroughId);
        int sessionCount = sessionRepository.findByPlaythroughId(playthroughId).size();

        Map<String, ProgressionStatus> progressionMap = new HashMap<>();
        for (QuestProgression qp : progressionRepository.findByPlaythroughId(playthroughId)) {
            progressionMap.put(qp.getQuestId(), qp.getStatus());
        }

        return new PlaythroughEvalSnapshot(
                new PrerequisiteEvaluator.EvaluationContext(completedQuestIds, sessionCount, flags),
                progressionMap
        );
    }

    /** Calcule le statut effectif d'une seule quête relatif à un Playthrough. */
    public QuestStatus computeFor(Quest quest, String playthroughId) {
        PlaythroughEvalSnapshot snap = buildSnapshot(playthroughId);
        ProgressionStatus progression = snap.progressionByQuestId()
                .getOrDefault(quest.getId(), ProgressionStatus.NOT_STARTED);
        return evaluator.computeStatus(progression, quest.getPrerequisites(), snap.ctx());
    }

    /**
     * Statut effectif de PLUSIEURS quêtes avec un seul build du snapshot (contrairement
     * à {@link #computeFor} qui reconstruit le snapshot à chaque appel). Utilisé par les
     * read-models qui balaient toutes les quêtes d'une campagne (préparation de séance).
     */
    public Map<String, QuestStatus> computeAll(List<Quest> quests, String playthroughId) {
        PlaythroughEvalSnapshot snap = buildSnapshot(playthroughId);
        Map<String, QuestStatus> out = new HashMap<>();
        for (Quest q : quests) {
            if (q == null || q.getId() == null) continue;
            ProgressionStatus progression = snap.progressionByQuestId()
                    .getOrDefault(q.getId(), ProgressionStatus.NOT_STARTED);
            out.put(q.getId(), evaluator.computeStatus(progression, q.getPrerequisites(), snap.ctx()));
        }
        return out;
    }

    /**
     * Injecte {@code progressionStatus} + {@code effectiveStatus} dans une liste de DTOs.
     * Un seul build du snapshot pour toute la liste.
     */
    public void enrich(List<QuestDTO> dtos, List<Quest> domain, String playthroughId) {
        if (dtos == null || dtos.isEmpty()) return;
        PlaythroughEvalSnapshot snap = buildSnapshot(playthroughId);
        Map<String, Quest> byId = domain.stream()
                .collect(Collectors.toMap(Quest::getId, q -> q));
        for (QuestDTO dto : dtos) {
            Quest q = byId.get(dto.getId());
            if (q == null) continue;
            ProgressionStatus progression = snap.progressionByQuestId()
                    .getOrDefault(q.getId(), ProgressionStatus.NOT_STARTED);
            QuestStatus status = evaluator.computeStatus(progression, q.getPrerequisites(), snap.ctx());
            dto.setProgressionStatus(progression.name());
            dto.setEffectiveStatus(status.name());
        }
    }
}
