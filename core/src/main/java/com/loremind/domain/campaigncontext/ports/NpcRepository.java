package com.loremind.domain.campaigncontext.ports;

import com.loremind.domain.campaigncontext.bestiary.Npc;

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

    /** Recherche par nom (insensible à la casse) — alimente la recherche globale. */
    List<Npc> searchByName(String query);
}
