package com.loremind.infrastructure.web.dto.playcontext;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO pour l'entité Clock — objet de transfert de l'API REST.
 */
@Data
public class ClockDTO {

    private String id;
    private String playthroughId;
    private String name;
    private String description;
    private int segments;
    private int filled;
    private int order;
    /** Déclencheur auto (co-MJ) : "NONE" | "FLAG_SET" | "QUEST_COMPLETED" | "SESSION_ENDED". */
    private String triggerType;
    /** Cible du déclencheur : nom du fait (FLAG_SET) ou id de quête (QUEST_COMPLETED) ; sinon null. */
    private String triggerRef;
    /** Front (menace) auquel l'horloge appartient, ou null (horloge libre). */
    private String frontId;
    /** Read-only : {@code filled >= segments}. */
    private boolean complete;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
