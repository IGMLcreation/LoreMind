package com.loremind.infrastructure.web.dto.gamesystemcontext;

import lombok.Data;

/**
 * DTO pour l'entité GameSystem (système de JDR).
 */
@Data
public class GameSystemDTO {

    private String id;
    private String name;
    private String description;
    private String rulesMarkdown;
    private String author;
    private boolean isPublic;
}
