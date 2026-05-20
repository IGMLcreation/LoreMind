package com.loremind.domain.playcontext.ports;

import com.loremind.domain.playcontext.Session;

import java.util.List;
import java.util.Optional;

/**
 * Port de sortie pour la persistance des Sessions.
 * Interface définie dans le domaine, implémentée par l'infrastructure.
 */
public interface SessionRepository {

    Session save(Session session);

    Optional<Session> findById(String id);

    List<Session> findAll();

    List<Session> findByCampaignId(String campaignId);

    /** Retourne la session en cours (endedAt null) s'il y en a une. */
    Optional<Session> findActive();

    void deleteById(String id);

    boolean existsById(String id);
}
