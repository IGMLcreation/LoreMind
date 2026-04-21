package com.loremind.infrastructure.web.dto.campaigncontext;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO pour l'entité Scene.
 * Objet de transfert de données pour l'API REST.
 */
@Data
public class SceneDTO {

    private String id;
    private String name;
    private String description;
    private String chapterId;
    private int order;

    // Champs narratifs enrichis
    private String location;
    private String timing;
    private String atmosphere;
    private String playerNarration;
    private String gmSecretNotes;
    private String choicesConsequences;
    private String combatDifficulty;
    private String enemies;

    /** IDs des pages du Lore liées (weak cross-context references). */
    private List<String> relatedPageIds = new ArrayList<>();

    /** IDs des images (Shared Kernel) illustrant cette scene. */
    private List<String> illustrationImageIds = new ArrayList<>();

    /** Branches narratives : sorties possibles vers d'autres scènes du même chapitre. */
    private List<SceneBranchDTO> branches = new ArrayList<>();
}
