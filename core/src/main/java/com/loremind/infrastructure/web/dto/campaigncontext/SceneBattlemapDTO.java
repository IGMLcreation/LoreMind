package com.loremind.infrastructure.web.dto.campaigncontext;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO pour une battlemap d'une Scene (variante Jour/Nuit, étage…).
 * Pendant web du Value Object domaine SceneBattlemap.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SceneBattlemapDTO {

    /** Libellé libre de la variante (ex : "Jour", "Nuit"). Peut être vide. */
    private String label;

    /** ID du fichier media (image/video). Null = carte sans fond. */
    private String mediaFileId;

    /** ID du fichier sidecar Universal VTT (json/dd2vtt). Null si absent. */
    private String dataFileId;
}
