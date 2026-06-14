package com.loremind.infrastructure.web.dto.campaigncontext;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sortie d'une pièce vers une autre pièce d'un lieu explorable.
 * Pendant web du record domaine RoomBranch.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomBranchDTO {
    private String label;
    private String targetRoomId;
    private String condition;
}
