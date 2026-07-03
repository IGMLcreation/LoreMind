package com.loremind.domain.playcontext.ports;

import com.loremind.domain.campaigncontext.ProgressionStatus;
import com.loremind.domain.playcontext.QuestProgression;

import java.util.List;
import java.util.Set;

/**
 * Port de sortie pour la persistance des progressions de quêtes d'un Playthrough.
 *
 * <p>Modèle "absence = NOT_STARTED" : on ne stocke que les transitions
 * explicites IN_PROGRESS / COMPLETED.</p>
 */
public interface QuestProgressionRepository {

    /** Liste toutes les progressions explicites d'un Playthrough. */
    List<QuestProgression> findByPlaythroughId(String playthroughId);

    /** Set des IDs de quêtes en COMPLETED pour un Playthrough donné (fast path éval). */
    Set<String> findCompletedQuestIdsByPlaythroughId(String playthroughId);

    /**
     * Crée ou met à jour le statut d'une quête pour un Playthrough.
     * Si {@code status == NOT_STARTED}, la ligne est supprimée (sémantique "absence").
     */
    void setStatus(String playthroughId, String questId, ProgressionStatus status);

    /** Supprime toutes les progressions d'un Playthrough (cascade applicative). */
    void deleteAllByPlaythroughId(String playthroughId);

    /** Supprime toutes les progressions d'une quête (cascade à la suppression d'une Quest). */
    void deleteByQuestId(String questId);
}
