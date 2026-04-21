package com.loremind.infrastructure.web.mapper;

import com.loremind.domain.lorecontext.LoreNode;
import com.loremind.infrastructure.web.dto.lorecontext.LoreNodeDTO;
import org.springframework.stereotype.Component;

/**
 * Mapper pour convertir entre LoreNode (entité de domaine) et LoreNodeDTO.
 */
@Component
public class LoreNodeMapper {

    public LoreNodeDTO toDTO(LoreNode loreNode) {
        if (loreNode == null) {
            return null;
        }
        
        LoreNodeDTO dto = new LoreNodeDTO();
        dto.setId(loreNode.getId());
        dto.setName(loreNode.getName());
        dto.setIcon(loreNode.getIcon());
        dto.setParentId(loreNode.getParentId());
        dto.setLoreId(loreNode.getLoreId());
        return dto;
    }

    public LoreNode toDomain(LoreNodeDTO dto) {
        if (dto == null) {
            return null;
        }
        
        return LoreNode.builder()
                .id(dto.getId())
                .name(dto.getName())
                .icon(dto.getIcon())
                .parentId(dto.getParentId())
                .loreId(dto.getLoreId())
                .build();
    }
}
