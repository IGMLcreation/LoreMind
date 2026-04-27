package com.loremind.infrastructure.web.mapper;

import com.loremind.domain.campaigncontext.Npc;
import com.loremind.infrastructure.web.dto.campaigncontext.NpcDTO;
import org.springframework.stereotype.Component;

@Component
public class NpcMapper {

    public NpcDTO toDTO(Npc n) {
        if (n == null) return null;
        NpcDTO dto = new NpcDTO();
        dto.setId(n.getId());
        dto.setName(n.getName());
        dto.setMarkdownContent(n.getMarkdownContent());
        dto.setCampaignId(n.getCampaignId());
        dto.setOrder(n.getOrder());
        return dto;
    }

    public Npc toDomain(NpcDTO dto) {
        if (dto == null) return null;
        return Npc.builder()
                .id(dto.getId())
                .name(dto.getName())
                .markdownContent(dto.getMarkdownContent())
                .campaignId(dto.getCampaignId())
                .order(dto.getOrder())
                .build();
    }
}
