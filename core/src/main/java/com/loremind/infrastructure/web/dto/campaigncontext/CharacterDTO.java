package com.loremind.infrastructure.web.dto.campaigncontext;

import lombok.Data;

/**
 * DTO pour les fiches de personnages (PJ) d'une campagne.
 */
@Data
public class CharacterDTO {

    private String id;
    private String name;
    private String markdownContent;
    private String campaignId;
    private int order;
}
