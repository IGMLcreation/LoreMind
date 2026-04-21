package com.loremind.domain.campaigncontext.ports;

import com.loremind.domain.campaigncontext.Scene;
import java.util.List;
import java.util.Optional;

/**
 * Port de sortie pour la persistance des Scenes.
 * Interface définie dans le domaine, implémentée par l'infrastructure.
 */
public interface SceneRepository {

    Scene save(Scene scene);
    
    Optional<Scene> findById(String id);
    
    List<Scene> findByChapterId(String chapterId);
    
    List<Scene> findAll();
    
    void deleteById(String id);
    
    boolean existsById(String id);
}
