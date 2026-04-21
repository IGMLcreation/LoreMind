package com.loremind.domain.campaigncontext.ports;

import com.loremind.domain.campaigncontext.Chapter;
import java.util.List;
import java.util.Optional;

/**
 * Port de sortie pour la persistance des Chapters.
 * Interface définie dans le domaine, implémentée par l'infrastructure.
 */
public interface ChapterRepository {

    Chapter save(Chapter chapter);
    
    Optional<Chapter> findById(String id);
    
    List<Chapter> findByArcId(String arcId);
    
    List<Chapter> findAll();
    
    void deleteById(String id);
    
    boolean existsById(String id);
}
