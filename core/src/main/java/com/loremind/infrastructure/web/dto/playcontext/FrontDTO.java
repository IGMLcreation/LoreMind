package com.loremind.infrastructure.web.dto.playcontext;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO pour l'entité Front — objet de transfert de l'API REST.
 */
@Data
public class FrontDTO {

    private String id;
    private String playthroughId;
    private String name;
    private String description;
    private int order;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
