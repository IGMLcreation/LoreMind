package com.loremind.domain.campaigncontext.quest;

/**
 * Condition de déblocage d'une quête (Chapter dans un Arc HUB).
 * Sealed : la liste des types est CLOSE et connue à la compilation. Pour ajouter
 * un nouveau type (ex : NpcMet), il faudra l'ajouter ici ET dans
 * {@link PrerequisiteEvaluator}.
 * Sémantique MVP : une quête a une LISTE de prérequis, tous combinés en ET logique
 * (pas de OR pour le moment).
 */
public sealed interface Prerequisite
        permits Prerequisite.QuestCompleted,
                Prerequisite.SessionReached,
                Prerequisite.FlagSet {

    /** La quête référencée par {@code questId} doit être en COMPLETED. */
    record QuestCompleted(String questId) implements Prerequisite {}

    /** Le compteur de sessions de la campagne doit avoir atteint {@code minSessionNumber}. */
    record SessionReached(int minSessionNumber) implements Prerequisite {}

    /** Le flag campagne nommé {@code flagName} doit être à true. */
    record FlagSet(String flagName) implements Prerequisite {}
}
