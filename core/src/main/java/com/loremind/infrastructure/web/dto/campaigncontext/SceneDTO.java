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

    /** Cle d'icone (cf. CAMPAIGN_ICON_OPTIONS cote front). */
    private String icon;

    // Champs narratifs enrichis
    private String location;
    private String timing;
    private String atmosphere;
    private String playerNarration;
    private String gmSecretNotes;
    private String choicesConsequences;
    private String combatDifficulty;
    private String enemies;

    /** IDs des fiches du bestiaire engagées dans la rencontre (weak refs). */
    private List<String> enemyIds = new ArrayList<>();

    /** IDs des pages du Lore liées (weak cross-context references). */
    private List<String> relatedPageIds = new ArrayList<>();

    /** IDs des images (Shared Kernel) illustrant cette scene (ambiance). */
    private List<String> illustrationImageIds = new ArrayList<>();

    /** Battlemap Foundry : ID du fichier media (image/video). Null = pas de carte. */
    private String battlemapMediaFileId;

    /** Battlemap Foundry : ID du fichier sidecar Universal VTT (json). Null si absent. */
    private String battlemapDataFileId;

    /** Branches narratives : sorties possibles vers d'autres scènes du même chapitre. */
    private List<SceneBranchDTO> branches = new ArrayList<>();

    /** Pièces du lieu explorable (donjon, crypte…). Vide = scène classique. */
    private List<RoomDTO> rooms = new ArrayList<>();
}
