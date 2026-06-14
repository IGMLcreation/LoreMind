package com.loremind.infrastructure.web.dto.campaigncontext;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO d'une table aléatoire (avec ses entrées).
 */
@Data
public class RandomTableDTO {
    private String id;
    private String name;
    private String description;
    private String diceFormula;
    private String icon;
    private String campaignId;
    private int order;
    private List<RandomTableEntryDTO> entries = new ArrayList<>();
}
