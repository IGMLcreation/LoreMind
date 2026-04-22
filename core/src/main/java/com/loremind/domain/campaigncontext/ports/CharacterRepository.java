package com.loremind.domain.campaigncontext.ports;

import com.loremind.domain.campaigncontext.Character;

import java.util.List;
import java.util.Optional;

/**
 * Port de sortie pour la persistance des fiches de personnages (PJ).
 */
public interface CharacterRepository {

    Character save(Character character);

    Optional<Character> findById(String id);

    List<Character> findByCampaignId(String campaignId);

    void deleteById(String id);

    boolean existsById(String id);
}
