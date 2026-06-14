package com.loremind.infrastructure.web.dto.playcontext;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO d'un Playthrough (Partie) — instance jouée d'une Campagne.
 */
@Data
public class PlaythroughDTO {

    private String id;
    private String campaignId;
    private String name;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
