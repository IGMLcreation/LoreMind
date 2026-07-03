package com.loremind.domain.playcontext.ports;

import com.loremind.domain.playcontext.Clock;

import java.util.List;
import java.util.Optional;

/**
 * Port de sortie pour la persistance des Horloges de progression (Clocks).
 */
public interface ClockRepository {

    Clock save(Clock clock);

    Optional<Clock> findById(String id);

    /** Horloges d'une Partie, triées par {@code order} croissant. */
    List<Clock> findByPlaythroughId(String playthroughId);

    void deleteById(String id);

    boolean existsById(String id);
}
