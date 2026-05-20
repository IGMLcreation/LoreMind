package com.loremind.infrastructure.web.dto.playcontext;

import com.loremind.domain.playcontext.EntryType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO d'une entrée de journal de session.
 */
@Data
public class SessionEntryDTO {

    private String id;
    private String sessionId;
    private EntryType type;
    private String content;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
