package com.loremind.infrastructure.web.dto.lorecontext;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * DTO pour l'entité Page.
 * Objet de transfert de données pour l'API REST.
 */
@Data
public class PageDTO {

    private String id;
    private String loreId;
    private String nodeId;
    private String templateId;
    private String title;
    private Map<String, String> values;
    /** Pour chaque champ IMAGE du template, la liste ordonnee des IDs d'images. */
    private Map<String, List<String>> imageValues;
    private String notes;
    private List<String> tags;
    private List<String> relatedPageIds;
}
