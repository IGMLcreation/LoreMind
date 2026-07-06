package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.gamesystemcontext.ports.GameSystemRepository;
import org.springframework.stereotype.Component;

/**
 * Formateur de contexte campagne pour les prompts IA.
 * Centralise la construction du bloc "nom + description + système de jeu".
 */
@Component
public class CampaignContextFormatter {

    private final CampaignRepository campaignRepository;
    private final GameSystemRepository gameSystemRepository;

    public CampaignContextFormatter(CampaignRepository campaignRepository,
                                    GameSystemRepository gameSystemRepository) {
        this.campaignRepository = campaignRepository;
        this.gameSystemRepository = gameSystemRepository;
    }

    /** Contexte compact : nom de campagne + description + système de jeu. */
    public String format(String campaignId) {
        if (campaignId == null) return "";
        Campaign campaign = campaignRepository.findById(campaignId).orElse(null);
        if (campaign == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("Campagne : ").append(campaign.getName());
        if (campaign.getDescription() != null && !campaign.getDescription().isBlank()) {
            sb.append(" — ").append(campaign.getDescription().trim());
        }
        if (campaign.getGameSystemId() != null && !campaign.getGameSystemId().isBlank()) {
            gameSystemRepository.findById(campaign.getGameSystemId())
                    .ifPresent(gs -> sb.append("\nSystème de jeu : ").append(gs.getName()));
        }
        return sb.toString();
    }
}
