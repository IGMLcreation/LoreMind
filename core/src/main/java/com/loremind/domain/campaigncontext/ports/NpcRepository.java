package com.loremind.domain.campaigncontext.ports;

import com.loremind.domain.campaigncontext.Npc;

import java.util.List;
import java.util.Optional;

/**
 * Port de sortie pour la persistance des fiches de PNJ (campagne).
 */
public interface NpcRepository {

    Npc save(Npc npc);

    Optional<Npc> findById(String id);

    List<Npc> findByCampaignId(String campaignId);

    void deleteById(String id);

    boolean existsById(String id);
}
