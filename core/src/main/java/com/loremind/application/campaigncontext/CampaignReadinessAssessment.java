package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.ReadinessStatus;

import java.util.List;
import java.util.Map;

/**
 * Bilan de préparation d'une campagne (read-model, Pilier B « guidage »).
 *
 * @param campaignId    campagne évaluée
 * @param overallStatus statut agrégé (DRAFT si ≥1 gap bloquant, PLAYABLE si ≥1
 *                      recommandé sans bloquant, POLISHED sinon)
 * @param counts        nombre de gaps par sévérité ({@code "BLOCKING"|"RECOMMENDED"|"OPTIONAL"})
 * @param gaps          liste des manques, triés par gravité décroissante
 */
public record CampaignReadinessAssessment(
        String campaignId,
        ReadinessStatus overallStatus,
        Map<String, Integer> counts,
        List<ReadinessGap> gaps
) {
}
