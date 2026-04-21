package com.loremind.domain.campaigncontext.ports;

import com.loremind.domain.campaigncontext.Arc;
import java.util.List;
import java.util.Optional;

/**
 * Port de sortie pour la persistance des Arcs.
 * Interface définie dans le domaine, implémentée par l'infrastructure.
 */
public interface ArcRepository {

    Arc save(Arc arc);
    
    Optional<Arc> findById(String id);
    
    List<Arc> findByCampaignId(String campaignId);
    
    List<Arc> findAll();
    
    void deleteById(String id);
    
    boolean existsById(String id);
}
