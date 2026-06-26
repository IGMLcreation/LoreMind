package com.loremind.infrastructure.web.dto.lorecontext;

import com.loremind.domain.lorecontext.ImageFraming;
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
    private int order;
    private Map<String, String> values;
    /** Pour chaque champ IMAGE du template, la liste ordonnee des IDs d'images. */
    private Map<String, List<String>> imageValues;
    /** Cadrage (pan/zoom) des images : fieldKey → imageId → {x, y, scale}. */
    private Map<String, Map<String, ImageFraming>> imageFraming;
    /** Pour chaque champ KEY_VALUE_LIST du template : label → valeur. */
    private Map<String, Map<String, String>> keyValueValues;
    /** Pour chaque champ TABLE du template : lignes (colonne → cellule). */
    private Map<String, List<Map<String, String>>> tableValues;
    private String notes;
    private List<String> tags;
    private List<String> relatedPageIds;
}
