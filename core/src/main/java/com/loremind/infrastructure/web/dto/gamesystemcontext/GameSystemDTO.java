package com.loremind.infrastructure.web.dto.gamesystemcontext;

import com.loremind.infrastructure.web.dto.shared.TemplateFieldDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO pour l'entité GameSystem (système de JDR).
 * Expose les templates PJ/PNJ comme listes de TemplateFieldDTO pour le wire.
 */
@Data
public class GameSystemDTO {

    private String id;
    private String name;
    private String description;
    private String rulesMarkdown;
    private List<TemplateFieldDTO> characterTemplate = new ArrayList<>();
    private List<TemplateFieldDTO> npcTemplate = new ArrayList<>();
    private String author;
    private boolean isPublic;
}
