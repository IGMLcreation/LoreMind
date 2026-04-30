package com.loremind.infrastructure.web.mapper;

import com.loremind.domain.campaigncontext.Character;
import com.loremind.infrastructure.web.dto.campaigncontext.CharacterDTO;
import org.springframework.stereotype.Component;

import java.util.HashMap;

@Component
public class CharacterMapper {

    public CharacterDTO toDTO(Character c) {
        if (c == null) return null;
        CharacterDTO dto = new CharacterDTO();
        dto.setId(c.getId());
        dto.setName(c.getName());
        dto.setPortraitImageId(c.getPortraitImageId());
        dto.setHeaderImageId(c.getHeaderImageId());
        dto.setValues(c.getValues() != null ? new HashMap<>(c.getValues()) : new HashMap<>());
        dto.setImageValues(c.getImageValues() != null ? new HashMap<>(c.getImageValues()) : new HashMap<>());
        dto.setCampaignId(c.getCampaignId());
        dto.setOrder(c.getOrder());
        return dto;
    }

    public Character toDomain(CharacterDTO dto) {
        if (dto == null) return null;
        return Character.builder()
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
