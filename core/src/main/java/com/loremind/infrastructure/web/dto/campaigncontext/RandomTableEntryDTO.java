package com.loremind.infrastructure.web.dto.campaigncontext;

import lombok.Data;

/**
 * DTO d'une entrée de table aléatoire (plage de jet → résultat).
 */
@Data
public class RandomTableEntryDTO {
    private int minRoll;
    private int maxRoll;
    private String label;
    private String detail;
}
