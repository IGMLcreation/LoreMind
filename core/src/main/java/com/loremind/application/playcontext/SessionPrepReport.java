package com.loremind.application.playcontext;

import com.loremind.application.campaigncontext.ReadinessGap;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Bilan de PRÉPARATION DE SÉANCE d'une Partie (Phase 3 co-MJ — « PlanDeSéance »).
 * Read-model pur, calculé à la volée : croise la position des joueurs (quêtes en cours /
 * disponibles, dernière séance), le contenu probable (nœuds des quêtes actives), les
 * manques de guidage CIBLÉS sur ce contenu, et les horloges en mouvement.
 *
 * @param playthroughId    la Partie évaluée
 * @param lastSession      dernière séance (ou {@code null} si aucune)
 * @param questsInProgress quêtes EN COURS (le « où en sont les joueurs »)
 * @param questsAvailable  quêtes DISPONIBLES (les prochaines pistes probables)
 * @param questsCompleted  quêtes TERMINÉES (rappel discret + possibilité de rouvrir)
 * @param hotspots         chapitres / scènes probables (nœuds des quêtes actives, dédupliqués)
 * @param gaps             manques de guidage restreints au contenu probable (à combler avant de jouer) ;
 *                         si la campagne n'utilise pas de quêtes, tous les manques de la campagne
 * @param otherGapCount    manques ailleurs dans la campagne (information, non bloquant pour la séance)
 * @param clocks           horloges entamées ({@code filled > 0}), avec le nom de leur menace
 */
public record SessionPrepReport(
        String playthroughId,
        LastSessionInfo lastSession,
        List<QuestInfo> questsInProgress,
        List<QuestInfo> questsAvailable,
        List<QuestInfo> questsCompleted,
        List<NodeInfo> hotspots,
        List<ReadinessGap> gaps,
        int otherGapCount,
        List<ClockInfo> clocks
) {

    /** Dernière séance tenue (ou en cours) de la Partie. */
    public record LastSessionInfo(String id, String name, LocalDateTime startedAt,
                                  LocalDateTime endedAt, boolean active) {}

    /** Quête résumée (statut implicite par la liste qui la porte). */
    public record QuestInfo(String id, String name, String icon) {}

    /**
     * Nœud narratif probable. {@code nodeType} = "CHAPTER"|"SCENE" ; {@code arcId}/
     * {@code chapterId} = contexte de navigation pour le lien profond côté front.
     */
    public record NodeInfo(String nodeType, String id, String name, String arcId, String chapterId) {}

    /** Horloge en mouvement (le front affiche « presque pleine » quand il reste ≤ 1 segment). */
    public record ClockInfo(String id, String name, int segments, int filled, String frontName) {}
}
