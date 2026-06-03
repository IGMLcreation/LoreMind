package com.loremind.infrastructure.web.dto.campaigncontext;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO d'une pièce d'un lieu explorable.
 */
@Data
public class RoomDTO {

    /** UUID stable généré côté front à la création. */
    private String id;
    private String name;
    private String description;
    private String enemies;
    private String loot;
    private String traps;
    private String gmNotes;
    /** Étage (0 = RdC, 1 = 1er…). Nullable = pas d'étage défini. */
    private Integer floor;
    private int order;
    private List<String> illustrationImageIds = new ArrayList<>();
    private String mapImageId;
    private List<RoomBranchDTO> branches = new ArrayList<>();
}
