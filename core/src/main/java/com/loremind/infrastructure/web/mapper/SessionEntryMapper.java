package com.loremind.infrastructure.web.mapper;

import com.loremind.domain.playcontext.SessionEntry;
import com.loremind.infrastructure.web.dto.playcontext.SessionEntryDTO;
import org.springframework.stereotype.Component;

@Component
public class SessionEntryMapper {

    public SessionEntryDTO toDTO(SessionEntry entry) {
        if (entry == null) return null;
        SessionEntryDTO dto = new SessionEntryDTO();
        dto.setId(entry.getId());
        dto.setSessionId(entry.getSessionId());
        dto.setType(entry.getType());
        dto.setContent(entry.getContent());
        dto.setOccurredAt(entry.getOccurredAt());
        dto.setCreatedAt(entry.getCreatedAt());
        dto.setUpdatedAt(entry.getUpdatedAt());
        return dto;
    }
}
