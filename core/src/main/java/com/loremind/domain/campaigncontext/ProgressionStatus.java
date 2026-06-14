package com.loremind.domain.campaigncontext;

/**
 * Statut de progression d'une quête (= Chapter dans un Arc HUB), piloté manuellement par le MJ.
 *
 * NOT_STARTED : pas encore commencée. Peut être visible (AVAILABLE) ou cachée (LOCKED)
 *               selon les prérequis — voir {@link QuestStatus}.
 * IN_PROGRESS : démarrée par le MJ via le bouton "Démarrer cette quête".
 * COMPLETED   : marquée terminée par le MJ.
 *
 * NB : un Chapter d'Arc LINEAR conserve NOT_STARTED par défaut sans impact visible.
 */
public enum ProgressionStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED
}
