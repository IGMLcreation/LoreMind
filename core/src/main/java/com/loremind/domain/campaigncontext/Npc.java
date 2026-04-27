package com.loremind.domain.campaigncontext;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Fiche de personnage non-joueur (PNJ) d'une campagne.
 * <p>
 * MVP : entité dédiée, distincte de {@link Character} (PJ). Choix DDD assumé —
 * un PNJ a vocation à porter à terme des invariants métier propres (faction,
 * statut vivant/mort/disparu, visibilité côté joueurs, relations inter-PNJ)
 * qui n'ont aucun sens sur un PJ. Mutualiser via un enum aurait pollué l'entité
 * PJ avec des champs inutiles ({@code if (type == NPC)} partout = anti-pattern).
 * <p>
 * Contenu markdown libre comme les PJ. Évolution prévue : templating partagé
 * PJ/PNJ piloté par GameSystem.
 * <p>
 * Scope campagne : les PNJ "univers" (worldboss, figures du Lore) restent
 * gérés via le système Page/Template du LoreContext.
 */
@Data
@Builder
public class Npc {

    private String id;
    private String name;

    /** Contenu libre markdown — description, motivation, stats, notes MJ. Nullable à la création. */
    private String markdownContent;

    /** Référence vers la Campaign parente (cross-aggregate via ID, jamais d'objet). */
    private String campaignId;

    /** Ordre d'affichage dans la liste des PNJ de la campagne. */
    private int order;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}