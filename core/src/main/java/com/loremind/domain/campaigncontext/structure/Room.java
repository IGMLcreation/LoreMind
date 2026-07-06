package com.loremind.domain.campaigncontext.structure;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Pièce d'un lieu explorable (donjon, crypte…) attaché à une Scene.
 *
 * <p>Une Scene devient « explorable » dès qu'elle a au moins une Room. Tant
 * qu'elle n'en a pas, elle se comporte comme un beat narratif classique.</p>
 *
 * <p>Pas un record Java parce que la liste {@code branches} est mutable côté
 * builder ; on garde la classe Lombok pour la cohérence avec le reste du
 * domaine (Arc, Chapter, Scene). L'ID est généré côté front (UUID) au moment
 * de la création — pas d'auto-increment DB puisque c'est un Value Object
 * sérialisé en JSONB sur Scene.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Room {

    /** ID stable (UUID généré côté client). Sert de cible aux {@link RoomBranch}. */
    private String id;

    /** Nom de la pièce (« Antichambre », « Salle du trône »). */
    private String name;

    /** Narration / description lue ou résumée aux joueurs en entrant. */
    private String description;

    /** Énemis, créatures, boss éventuels (markdown libre). */
    private String enemies;

    /**
     * IDs des fiches du bestiaire ({@link Enemy}) présentes dans la pièce
     * (weak refs). Complète le texte libre {@code enemies}, comme sur Scene.
     */
    @Builder.Default
    private List<String> enemyIds = new ArrayList<>();

    /** Loot / récompenses présentes dans la pièce. */
    private String loot;

    /** Pièges / dangers environnementaux. */
    private String traps;

    /** Notes privées du MJ (cachées des joueurs). */
    private String gmNotes;

    /** Étage / niveau de la pièce. 0 = rez-de-chaussée. Nullable = pas d'étage défini. */
    private Integer floor;

    /** Ordre d'affichage dans la liste (au sein d'un même étage le cas échéant). */
    private int order;

    /** IDs d'images d'illustration / ambiance. */
    @Builder.Default
    private List<String> illustrationImageIds = new ArrayList<>();

    /** ID de l'image « plan » de la pièce (1 image dédiée, schéma tactique). */
    private String mapImageId;

    /**
     * Sorties vers d'autres pièces. {@link RoomBranch#targetRoomId()} doit pointer
     * vers une Room de la même Scene.
     */
    @Builder.Default
    private List<RoomBranch> branches = new ArrayList<>();
}
