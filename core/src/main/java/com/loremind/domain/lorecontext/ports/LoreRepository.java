package com.loremind.domain.lorecontext.ports;

import com.loremind.domain.lorecontext.Lore;
import java.util.List;
import java.util.Optional;

/**
 * Port de sortie pour la persistance des Lores.
 * Interface définie dans le domaine, implémentée par l'infrastructure.
 * C'est un Port de sortie (Output Port) selon l'Architecture Hexagonale.
 */
public interface LoreRepository {

    Lore save(Lore lore);
    
    Optional<Lore> findById(String id);
    
    List<Lore> findAll();
    
    void deleteById(String id);

    boolean existsById(String id);

    List<Lore> searchByName(String query);
}
