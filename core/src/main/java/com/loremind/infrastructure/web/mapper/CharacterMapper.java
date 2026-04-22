package com.loremind.infrastructure.web.mapper;

import com.loremind.domain.campaigncontext.Character;
import com.loremind.infrastructure.web.dto.campaigncontext.CharacterDTO;
import org.springframework.stereotype.Component;

@Component
public class CharacterMapper {

    public CharacterDTO toDTO(Character c) {
        if (c == null) return null;
        CharacterDTO dto = new CharacterDTO();
        dto.setId(c.getId());
        dto.setName(c.getName());
        dto.setMarkdownContent(c.getMarkdownContent());
        dto.setCampaignId(c.getCampaignId());
        dto.setOrder(c.getOrder());
        return dto;
    }

    public Character toDomain(CharacterDTO dto) {
        if (dto == null) return null;
        return Character.builder()
                .id(dto.getId())
                .name(dto.getName())
                .markdownContent(dto.getMarkdownContent())
                .campaignId(dto.getCampaignId())
                .order(dto.getOrder())
                .build();
    }
}
