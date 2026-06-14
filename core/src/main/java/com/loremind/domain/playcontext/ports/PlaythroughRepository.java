package com.loremind.domain.playcontext.ports;

import com.loremind.domain.playcontext.Playthrough;

import java.util.List;
import java.util.Optional;

/**
 * Port de sortie pour la persistance des Playthroughs (parties jouées).
 */
public interface PlaythroughRepository {

    Playthrough save(Playthrough playthrough);

    Optional<Playthrough> findById(String id);

    List<Playthrough> findByCampaignId(String campaignId);

    List<Playthrough> findAll();

    void deleteById(String id);

    boolean existsById(String id);
}
