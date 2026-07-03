package com.loremind.domain.campaigncontext.ports;

import com.loremind.domain.campaigncontext.Quest;
import java.util.List;
import java.util.Optional;

/**
 * Port de sortie pour la persistance des Quests.
 * Interface définie dans le domaine, implémentée par l'infrastructure.
 */
public interface QuestRepository {

    Quest save(Quest quest);

    Optional<Quest> findById(String id);

    List<Quest> findByCampaignId(String campaignId);

    /** Quêtes rattachées à un arc (HUB). Vide si aucune. */
    List<Quest> findByArcId(String arcId);

    List<Quest> findAll();

    void deleteById(String id);

    boolean existsById(String id);
}
