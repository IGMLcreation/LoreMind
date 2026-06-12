package com.loremind.domain.campaigncontext;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entité de domaine représentant une Scene.
 * Unité de jeu la plus fine, subdivision d'un Chapter (ex: "Scène 1: L'auberge").
 * Entité pure du domaine, sans dépendance technique.
 */
@Data
@Builder
public class Scene {

    private String id;
    private String name;
    private String description;           // = Description courte dans l'UI
    private String chapterId;              // Référence vers le Chapter parent
    private int order;                     // Ordre de la scène dans le chapitre

    /** Cle d'icone choisie par l'utilisateur (cf. CAMPAIGN_ICON_OPTIONS cote front). */
    private String icon;

    // === Contexte et ambiance ===
    private String location;               // Lieu de la scène (ex: Taverne du Dragon d'Or)
    private String timing;                 // Moment (ex: Soir, à la tombée de la nuit)
    private String atmosphere;             // Ambiance générale (sons, odeurs, émotions...)

    // === Narration pour les joueurs ===
    private String playerNarration;        // Texte lu directement aux joueurs

    // === Notes et secrets du MJ (privé) ===
    private String gmSecretNotes;          // Informations cachées, non visibles par les joueurs

    // === Choix et conséquences ===
    private String choicesConsequences;    // Options offertes aux joueurs et leurs conséquences

    // === Combat ou rencontre ===
    private String combatDifficulty;       // Difficulté estimée
    private String enemies;                // Liste des ennemis et créatures (texte libre)

    /**
     * IDs des fiches du bestiaire ({@link Enemy}) engagées dans cette rencontre
     * (weak cross-aggregate references). Complète le texte libre `enemies` :
     * l'utilisateur peut référencer ses fiches, ou tout écrire à la main, ou les deux.
     */
    @Builder.Default
    private List<String> enemyIds = new ArrayList<>();

    /**
     * IDs des pages du Lore associées à cette scène (weak cross-context references).
     * Très utile pour la préparation : épingler un lieu, un PNJ, une créature à une scène.
     */
    @Builder.Default
    private List<String> relatedPageIds = new ArrayList<>();

    /**
     * IDs des images (Shared Kernel) illustrant cette scene.
     * Vocation "ambiance" : portraits, decors, moodboard. Rendu facon editorial.
     */
    @Builder.Default
    private List<String> illustrationImageIds = new ArrayList<>();

    /**
     * IDs des images utilisees comme cartes / plans.
     * Vocation "outil de table" : plan de donjon, carte du lieu, schema tactique.
     * Rendu different des illustrations : vignettes plus grandes, ratio natif preserve.
     */
    @Builder.Default
    private List<String> mapImageIds = new ArrayList<>();

    /**
     * Sorties narratives possibles depuis cette scène (graphe intra-chapitre).
     * Chaque branche décrit un choix des joueurs et la scène de destination.
     * Liste vide = scène "feuille" (fin de chapitre ou scène linéaire).
     */
    @Builder.Default
    private List<SceneBranch> branches = new ArrayList<>();

    /**
     * Pièces du lieu explorable représenté par cette scène (donjon, crypte, manoir…).
     * Vide => scène classique « beat narratif » (comportement inchangé).
     * Non vide => la scène devient explorable, l'UI affiche un layout dédié pièce-par-pièce.
     * Sérialisé en JSONB.
     */
    @Builder.Default
    private List<Room> rooms = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
