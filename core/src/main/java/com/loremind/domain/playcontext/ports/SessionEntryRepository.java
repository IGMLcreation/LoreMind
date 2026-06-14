package com.loremind.domain.playcontext.ports;

import com.loremind.domain.playcontext.SessionEntry;

import java.util.List;
import java.util.Optional;

/**
 * Port de sortie pour la persistance des entrées de journal de session.
 */
public interface SessionEntryRepository {

    SessionEntry save(SessionEntry entry);

    Optional<SessionEntry> findById(String id);

    /** Renvoie les entrées d'une session, triées par occurredAt croissant (chronologique). */
    List<SessionEntry> findBySessionId(String sessionId);

    void deleteById(String id);

    /** Supprime toutes les entrées d'une session — utilisé pour la cascade à la suppression. */
    void deleteBySessionId(String sessionId);

    boolean existsById(String id);
}
