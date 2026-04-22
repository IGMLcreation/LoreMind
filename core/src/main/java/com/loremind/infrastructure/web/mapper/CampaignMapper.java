package com.loremind.infrastructure.web.mapper;

import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.infrastructure.web.dto.campaigncontext.CampaignDTO;
import org.springframework.stereotype.Component;

/**
 * Mapper pour convertir entre Campaign (entité de domaine) et CampaignDTO.
 */
@Component
public class CampaignMapper {

    public CampaignDTO toDTO(Campaign campaign) {
        if (campaign == null) {
            return null;
        }
        
        CampaignDTO dto = new CampaignDTO();
        dto.setId(campaign.getId());
        dto.setName(campaign.getName());
        dto.setDescription(campaign.getDescription());
        dto.setArcsCount(campaign.getArcsCount());
        dto.setLoreId(campaign.getLoreId());
        dto.setGameSystemId(campaign.getGameSystemId());
        return dto;
    }

    public Campaign toDomain(CampaignDTO dto) {
        if (dto == null) {
            return null;
        }
        
        return Campaign.builder()
                .id(dto.getId())
                .name(dto.getName())
                .description(dto.getDescription())
                .arcsCount(dto.getArcsCount())
                .loreId(dto.getLoreId())
                .gameSystemId(dto.getGameSystemId())
                .build();
    }
}
