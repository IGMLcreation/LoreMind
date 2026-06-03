package com.loremind.infrastructure.web.mapper;

import com.loremind.domain.playcontext.Session;
import com.loremind.infrastructure.web.dto.playcontext.SessionDTO;
import org.springframework.stereotype.Component;

/**
 * Mapper Session (domaine) ↔ SessionDTO (transport REST).
 */
@Component
public class SessionMapper {

    public SessionDTO toDTO(Session session) {
        if (session == null) return null;
        SessionDTO dto = new SessionDTO();
        dto.setId(session.getId());
        dto.setName(session.getName());
        dto.setPlaythroughId(session.getPlaythroughId());
        dto.setStartedAt(session.getStartedAt());
        dto.setEndedAt(session.getEndedAt());
        dto.setCreatedAt(session.getCreatedAt());
        dto.setUpdatedAt(session.getUpdatedAt());
        dto.setActive(session.isActive());
        return dto;
    }
}
