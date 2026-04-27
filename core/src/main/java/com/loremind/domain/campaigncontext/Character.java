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
 * Scope strict PJ : les PNJ sont gérés par l'entité {@link Npc} dédiée
 * (entité distincte plutôt qu'enum PJ/PNJ — invariants métier divergents).
 * Évolution prévue : système de templating partagé PJ/PNJ piloté par
 * GameSystem pour adapter les blocs aux différents systèmes de JDR.
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
