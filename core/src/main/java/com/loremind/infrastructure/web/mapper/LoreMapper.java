package com.loremind.infrastructure.web.mapper;

import com.loremind.domain.lorecontext.Lore;
import com.loremind.infrastructure.web.dto.lorecontext.LoreDTO;
import org.springframework.stereotype.Component;

/**
 * Mapper pour convertir entre Lore (entité de domaine) et LoreDTO.
 * Fait partie de la couche Infrastructure de l'Architecture Hexagonale.
 */
@Component
public class LoreMapper {

    public LoreDTO toDTO(Lore lore) {
        if (lore == null) {
            return null;
        }
        
        LoreDTO dto = new LoreDTO();
        dto.setId(lore.getId());
        dto.setName(lore.getName());
        dto.setDescription(lore.getDescription());
        dto.setNodeCount(lore.getNodeCount());
        dto.setPageCount(lore.getPageCount());
        return dto;
    }

    public Lore toDomain(LoreDTO dto) {
        if (dto == null) {
            return null;
        }
        
        return Lore.builder()
                .id(dto.getId())
                .name(dto.getName())
                .description(dto.getDescription())
                .nodeCount(dto.getNodeCount())
                .pageCount(dto.getPageCount())
                .build();
    }
}
