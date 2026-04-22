package com.loremind.domain.gamesystemcontext.ports;

import com.loremind.domain.gamesystemcontext.GameSystem;

import java.util.List;
import java.util.Optional;

/**
 * Port de sortie pour la persistance des GameSystems.
 */
public interface GameSystemRepository {

    GameSystem save(GameSystem gameSystem);

    Optional<GameSystem> findById(String id);

    List<GameSystem> findAll();

    void deleteById(String id);

    boolean existsById(String id);

    List<GameSystem> searchByName(String query);
}
