package com.loremind.domain.campaigncontext;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Fiche de personnage joueur (PJ) d'une campagne.
 * <p>
 * MVP : contenu markdown libre, l'utilisateur met ce qu'il veut (stats,
 * backstory, équipement). Évolution prévue vers un système templaté par
 * GameSystem (la fiche Nimble n'a pas les mêmes champs qu'une fiche D&D).
 * <p>
 * Scope strict PJ : les PNJ restent dans le Lore (pages templatées) ou
 * dans les scènes elles-mêmes. Si le besoin de PNJ spécifiques à une
 * campagne remonte, on étendra l'entité (ex: type enum PJ/PNJ).
 */
@Data
@Builder
public class Character {

    private String id;
    private String name;

    /**
     * Contenu libre en markdown — stats + backstory + notes. Nullable à la création,
     * renseigné progressivement par le MJ.
     */
    private String markdownContent;

    /** Référence vers la Campaign parente. */
    private String campaignId;

    /** Ordre d'affichage dans la liste des PJ de la campagne. */
    private int order;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
