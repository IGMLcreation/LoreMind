package com.loremind.domain.playcontext.ports;

import java.util.Map;

/**
 * Port de sortie pour les flags narratifs d'un Playthrough.
 *
 * <p>Anciennement {@code CampaignFlagRepository} : les flags suivent maintenant
 * une partie (Playthrough), pas un scénario (Campaign).</p>
 */
public interface PlaythroughFlagRepository {

    Map<String, Boolean> findByPlaythroughId(String playthroughId);

    void setFlag(String playthroughId, String name, boolean value);

    void deleteFlag(String playthroughId, String name);

    void deleteAllByPlaythroughId(String playthroughId);
}
