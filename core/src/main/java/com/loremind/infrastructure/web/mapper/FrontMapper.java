package com.loremind.infrastructure.web.mapper;

import com.loremind.domain.playcontext.Front;
import com.loremind.infrastructure.web.dto.playcontext.FrontDTO;
import org.springframework.stereotype.Component;

/**
 * Mapper Front (domaine) ↔ FrontDTO (transport REST).
 */
@Component
public class FrontMapper {

    public FrontDTO toDTO(Front f) {
        if (f == null) return null;
        FrontDTO dto = new FrontDTO();
        dto.setId(f.getId());
        dto.setPlaythroughId(f.getPlaythroughId());
        dto.setName(f.getName());
        dto.setDescription(f.getDescription());
        dto.setOrder(f.getOrder());
        dto.setCreatedAt(f.getCreatedAt());
        dto.setUpdatedAt(f.getUpdatedAt());
        return dto;
    }
}
