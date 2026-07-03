package com.loremind.infrastructure.web.mapper;

import com.loremind.domain.playcontext.Clock;
import com.loremind.domain.playcontext.ClockTrigger;
import com.loremind.infrastructure.web.dto.playcontext.ClockDTO;
import org.springframework.stereotype.Component;

/**
 * Mapper Clock (domaine) ↔ ClockDTO (transport REST).
 */
@Component
public class ClockMapper {

    public ClockDTO toDTO(Clock c) {
        if (c == null) return null;
        ClockDTO dto = new ClockDTO();
        dto.setId(c.getId());
        dto.setPlaythroughId(c.getPlaythroughId());
        dto.setName(c.getName());
        dto.setDescription(c.getDescription());
        dto.setSegments(c.getSegments());
        dto.setFilled(c.getFilled());
        dto.setOrder(c.getOrder());
        dto.setTriggerType((c.getTriggerType() != null ? c.getTriggerType() : ClockTrigger.NONE).name());
        dto.setTriggerRef(c.getTriggerRef());
        dto.setFrontId(c.getFrontId());
        dto.setComplete(c.isComplete());
        dto.setCreatedAt(c.getCreatedAt());
        dto.setUpdatedAt(c.getUpdatedAt());
        return dto;
    }
}
