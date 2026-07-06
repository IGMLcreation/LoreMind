package com.loremind.domain.campaigncontext.ports;

import com.loremind.domain.campaigncontext.randomtable.RandomTable;

import java.util.List;
import java.util.Optional;

/**
 * Port de sortie pour la persistance des {@link RandomTable}.
 */
public interface RandomTableRepository {

    RandomTable save(RandomTable table);

    Optional<RandomTable> findById(String id);

    List<RandomTable> findByCampaignId(String campaignId);

    void deleteById(String id);

    boolean existsById(String id);

    /** Recherche par nom (insensible à la casse) — alimente la recherche globale. */
    List<RandomTable> searchByName(String query);
}
