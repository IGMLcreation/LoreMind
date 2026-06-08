package com.loremind.infrastructure.web.dto.campaigncontext;

import lombok.Data;

/**
 * DTO d'un objet de catalogue.
 */
@Data
public class CatalogItemDTO {
    private String name;
    private String price;
    private String category;
    private String description;
}
