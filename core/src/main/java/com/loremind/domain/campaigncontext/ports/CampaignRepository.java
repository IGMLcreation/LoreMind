package com.loremind.domain.campaigncontext.ports;

import com.loremind.domain.campaigncontext.Campaign;
import java.util.List;
import java.util.Optional;

/**
 * Port de sortie pour la persistance des Campaigns.
 * Interface définie dans le domaine, implémentée par l'infrastructure.
 */
public interface CampaignRepository {

    Campaign save(Campaign campaign);
    
    Optional<Campaign> findById(String id);
    
    List<Campaign> findAll();
    
    void deleteById(String id);
    
    boolean existsById(String id);

    List<Campaign> searchByName(String query);
}
