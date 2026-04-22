package com.loremind.infrastructure.web.dto.campaigncontext;

import lombok.Data;

/**
 * DTO pour l'entité Campaign.
 * Objet de transfert de données pour l'API REST.
 */
@Data
public class CampaignDTO {

    private String id;
    private String name;
    private String description;
    private int arcsCount;
    /** Nullable : campagne sans univers associé. */
    private String loreId;
    /** Nullable : campagne sans système de JDR associé (générique). */
    private String gameSystemId;
}
