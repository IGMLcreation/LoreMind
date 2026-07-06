package com.loremind.domain.campaigncontext.quest;

/**
 * Statut effectif d'une quête tel qu'affiché dans la vue Hub.
 * DÉRIVÉ — jamais persisté. Calculé par {@link PrerequisiteEvaluator} à partir
 * de la {@link ProgressionStatus} persistée et de l'évaluation des prérequis.
 * Table de vérité :
 *   NOT_STARTED + prérequis non remplis -> LOCKED
 *   NOT_STARTED + prérequis remplis     -> AVAILABLE
 *   IN_PROGRESS                          -> IN_PROGRESS
 *   COMPLETED                            -> COMPLETED
 */
public enum QuestStatus {
    LOCKED,
    AVAILABLE,
    IN_PROGRESS,
    COMPLETED
}
