package com.loremind.infrastructure.web.dto.lorecontext;

import lombok.Data;

/**
 * DTO pour l'entité LoreNode.
 * Objet de transfert de données pour l'API REST.
 */
@Data
public class LoreNodeDTO {

    private String id;
    private String name;
    private String icon;
    private String parentId;
    private String loreId;
}
