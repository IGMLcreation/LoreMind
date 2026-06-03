package com.loremind.infrastructure.web.mapper;

import com.loremind.domain.playcontext.Playthrough;
import com.loremind.infrastructure.web.dto.playcontext.PlaythroughDTO;
import org.springframework.stereotype.Component;

@Component
public class PlaythroughMapper {

    public PlaythroughDTO toDTO(Playthrough p) {
        if (p == null) return null;
        PlaythroughDTO dto = new PlaythroughDTO();
        dto.setId(p.getId());
        dto.setCampaignId(p.getCampaignId());
        dto.setName(p.getName());
        dto.setDescription(p.getDescription());
        dto.setCreatedAt(p.getCreatedAt());
        dto.setUpdatedAt(p.getUpdatedAt());
        return dto;
    }

    public Playthrough toDomain(PlaythroughDTO dto) {
        if (dto == null) return null;
        return Playthrough.builder()
                .id(dto.getId())
                .campaignId(dto.getCampaignId())
                .name(dto.getName())
                .description(dto.getDescription())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }
}
