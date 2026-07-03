package com.loremind.infrastructure.web.dto.campaigncontext;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO d'un lien Quête → nœud narratif (Chapitre ou Scène).
 * Miroir REST du VO {@code QuestNodeRef}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestNodeRefDTO {

    /** Type du nœud : CHAPTER | SCENE. */
    private String nodeType;

    /** ID du Chapitre ou de la Scène (weak ref). */
    private String nodeId;

    /** Ordre du nœud dans la quête. */
    private int order;
}
