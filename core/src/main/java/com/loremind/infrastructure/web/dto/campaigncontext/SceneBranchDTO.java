package com.loremind.infrastructure.web.dto.campaigncontext;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO pour une branche narrative (sortie) depuis une Scene.
 * Pendant web du Value Object domaine SceneBranch.
 *
 * @Data (mutable) est approprié pour les DTO de wire : Jackson désérialise
 * via setters côté requête entrante.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SceneBranchDTO {

    private String label;
    private String targetSceneId;
    private String condition;
}
