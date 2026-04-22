package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service d'application pour le contexte Campaign.
 * Orchestre la logique métier en utilisant le Port CampaignRepository.
 * Fait partie de la couche Application de l'Architecture Hexagonale.
 */
@Service
public class CampaignService {

    private final CampaignRepository campaignRepository;

    public CampaignService(CampaignRepository campaignRepository) {
        this.campaignRepository = campaignRepository;
    }

    /**
     * Parameter Object pour la création / mise à jour d'une Campaign.
     * Évite une signature à rallonge et rend les évolutions futures (theme,
     * coverImageUrl, etc.) sans casser les appelants.
     *
     * <p>{@code loreId} est nullable : une campagne peut exister sans univers associé.</p>
     */
    public record CampaignData(String name, String description, String loreId, String gameSystemId) {}

    public Campaign createCampaign(CampaignData data) {
        Campaign campaign = Campaign.builder()
                .name(data.name())
                .description(data.description())
                .loreId(normalizeId(data.loreId()))
                .gameSystemId(normalizeId(data.gameSystemId()))
                .arcsCount(0)
                .build();
        return campaignRepository.save(campaign);
    }

    public Optional<Campaign> getCampaignById(String id) {
        return campaignRepository.findById(id);
    }

    public List<Campaign> getAllCampaigns() {
        return campaignRepository.findAll();
    }

    public Campaign updateCampaign(String id, CampaignData data) {
        Optional<Campaign> existingCampaign = campaignRepository.findById(id);
        if (existingCampaign.isEmpty()) {
            throw new IllegalArgumentException("Campaign non trouvé avec l'ID: " + id);
        }

        Campaign campaign = existingCampaign.get();
        campaign.setName(data.name());
        campaign.setDescription(data.description());
        campaign.setLoreId(normalizeId(data.loreId()));
        campaign.setGameSystemId(normalizeId(data.gameSystemId()));
        return campaignRepository.save(campaign);
    }

    /**
     * Normalise un ID entrant : une chaîne vide/blanche est traitée comme "pas de lien".
     * Utile car les payloads JSON peuvent envoyer "" au lieu de null.
     */
    private String normalizeId(String id) {
        return (id == null || id.isBlank()) ? null : id;
    }

    public void deleteCampaign(String id) {
        campaignRepository.deleteById(id);
    }

    public boolean campaignExists(String id) {
        return campaignRepository.existsById(id);
    }

    public List<Campaign> searchCampaigns(String query) {
        if (query == null || query.isBlank()) return List.of();
        return campaignRepository.searchByName(query.trim());
    }
}
