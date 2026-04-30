package com.loremind.infrastructure.web.mapper;

import com.loremind.domain.campaigncontext.Npc;
import com.loremind.infrastructure.web.dto.campaigncontext.NpcDTO;
import org.springframework.stereotype.Component;

import java.util.HashMap;

@Component
public class NpcMapper {

    public NpcDTO toDTO(Npc n) {
        if (n == null) return null;
        NpcDTO dto = new NpcDTO();
        dto.setId(n.getId());
        dto.setName(n.getName());
        dto.setPortraitImageId(n.getPortraitImageId());
        dto.setHeaderImageId(n.getHeaderImageId());
        dto.setValues(n.getValues() != null ? new HashMap<>(n.getValues()) : new HashMap<>());
        dto.setImageValues(n.getImageValues() != null ? new HashMap<>(n.getImageValues()) : new HashMap<>());
        dto.setCampaignId(n.getCampaignId());
        dto.setOrder(n.getOrder());
        return dto;
    }

    public Npc toDomain(NpcDTO dto) {
        if (dto == null) return null;
        return Npc.builder()
                .id(dto.getId())
                .name(dto.getName())
                .portraitImageId(dto.getPortraitImageId())
                .headerImageId(dto.getHeaderImageId())
                .values(dto.getValues() != null ? new HashMap<>(dto.getValues()) : new HashMap<>())
                .imageValues(dto.getImageValues() != null ? new HashMap<>(dto.getImageValues()) : new HashMap<>())
                .campaignId(dto.getCampaignId())
                .order(dto.getOrder())
                .build();
    }
}
