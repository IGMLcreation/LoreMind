package com.loremind.infrastructure.web.dto.playcontext;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO pour l'entité Session — objet de transfert de l'API REST.
 */
@Data
public class SessionDTO {

    private String id;
    private String name;
    private String playthroughId;
    private LocalDateTime startedAt;
    /** Null = session en cours. */
    private LocalDateTime endedAt;
    /** Scène courante épinglée (nullable, mode cockpit). */
    private String currentSceneId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean active;
}
