package com.loremind.domain.playcontext.ports;

import com.loremind.domain.playcontext.Session;

import java.util.List;
import java.util.Optional;

/**
 * Port de sortie pour la persistance des Sessions.
 */
public interface SessionRepository {

    Session save(Session session);

    Optional<Session> findById(String id);

    List<Session> findAll();

    List<Session> findByPlaythroughId(String playthroughId);

    /** Retourne UNE session active dans l'app (sémantique « legacy » — multi-actives possibles). */
    Optional<Session> findActive();

    /** Retourne la session active du Playthrough donné, s'il y en a une. */
    Optional<Session> findActiveByPlaythroughId(String playthroughId);

    void deleteById(String id);

    boolean existsById(String id);
}
