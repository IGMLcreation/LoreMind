package com.loremind.infrastructure.web.dto.campaigncontext;

import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DTO pour les fiches de PNJ d'une campagne. Meme structure que CharacterDTO.
 */
@Data
public class NpcDTO {

    private String id;
    private String name;
    private String portraitImageId;
    private String headerImageId;
    private Map<String, String> values = new HashMap<>();
    private Map<String, List<String>> imageValues = new HashMap<>();
    private String campaignId;
    private int order;
}
