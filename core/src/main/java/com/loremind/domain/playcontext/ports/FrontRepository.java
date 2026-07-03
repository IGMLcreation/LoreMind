package com.loremind.domain.playcontext.ports;

import com.loremind.domain.playcontext.Front;

import java.util.List;
import java.util.Optional;

/**
 * Port de sortie pour la persistance des Fronts (menaces regroupant des horloges).
 */
public interface FrontRepository {

    Front save(Front front);

    Optional<Front> findById(String id);

    /** Fronts d'une Partie, triés par {@code order} croissant. */
    List<Front> findByPlaythroughId(String playthroughId);

    void deleteById(String id);

    boolean existsById(String id);
}
