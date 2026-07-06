package com.loremind.domain.campaigncontext.ports;

import com.loremind.domain.campaigncontext.bestiary.Enemy;

import java.util.List;
import java.util.Optional;

/**
 * Port de sortie pour la persistance des fiches d'ennemis (bestiaire de campagne).
 */
public interface EnemyRepository {

    Enemy save(Enemy enemy);

    Optional<Enemy> findById(String id);

    List<Enemy> findByCampaignId(String campaignId);

    void deleteById(String id);

    /** Recherche par nom (insensible à la casse) — alimente la recherche globale. */
    List<Enemy> searchByName(String query);
}
