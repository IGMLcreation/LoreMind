package com.loremind.infrastructure.web.dto.campaigncontext;

import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DTO pour les fiches de personnages (PJ) d'une campagne.
 * Reflete la refonte template-based : champs universels hard-codes (name,
 * portrait, header) + maps {@code values}/{@code imageValues} pour les
 * champs templates pilotes par le GameSystem.
 */
@Data
public class CharacterDTO {

    private String id;
    private String name;
    private String portraitImageId;
    private String headerImageId;
    private Map<String, String> values = new HashMap<>();
    private Map<String, List<String>> imageValues = new HashMap<>();
    private String campaignId;
    private int order;
}
