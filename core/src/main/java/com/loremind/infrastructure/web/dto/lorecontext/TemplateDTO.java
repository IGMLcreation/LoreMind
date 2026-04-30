package com.loremind.infrastructure.web.dto.lorecontext;

import com.loremind.infrastructure.web.dto.shared.TemplateFieldDTO;
import lombok.Data;

import java.util.List;

/**
 * DTO pour l'entité Template.
 * Objet de transfert de données pour l'API REST.
 * Expose un compteur fieldCount pour éviter aux clients de recalculer fields.size().
 */
@Data
public class TemplateDTO {

    private String id;
    private String loreId;
    private String name;
    private String description;
    private String defaultNodeId;
    private List<TemplateFieldDTO> fields;
    private int fieldCount;
}
