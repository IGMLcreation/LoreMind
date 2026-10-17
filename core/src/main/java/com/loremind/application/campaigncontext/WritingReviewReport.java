package com.loremind.application.campaigncontext;

import java.util.List;

/**
 * Relecture d'éditeur d'une campagne (Mentor d'écriture, phase M2).
 *
 * @param campaignId campagne relue
 * @param remarks    remarques dans l'ordre de l'arbre (campagne, puis arc par arc)
 */
public record WritingReviewReport(String campaignId, List<WritingRemark> remarks) {
}
