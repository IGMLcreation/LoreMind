package com.loremind.infrastructure.web.dto.campaigncontext;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO d'un catalogue d'objets (avec ses objets).
 */
@Data
public class ItemCatalogDTO {
    private String id;
    private String name;
    private String description;
    private String icon;
    private String campaignId;
    private int order;
    private List<CatalogItemDTO> items = new ArrayList<>();
}
