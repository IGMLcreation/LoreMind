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
    private String enemies;                // Liste des ennemis et créatures

    /**
     * IDs des pages du Lore associées à cette scène (weak cross-context references).
     * Très utile pour la préparation : épingler un lieu, un PNJ, une créature à une scène.
     */
    @Builder.Default
    private List<String> relatedPageIds = new ArrayList<>();

    /**
     * IDs des images (Shared Kernel) illustrant cette scene.
     * Utile pour carte du lieu, portraits des PNJ principaux, ambiance.
     */
    @Builder.Default
    private List<String> illustrationImageIds = new ArrayList<>();

    /**
     * Sorties narratives possibles depuis cette scène (graphe intra-chapitre).
     * Chaque branche décrit un choix des joueurs et la scène de destination.
     * Liste vide = scène "feuille" (fin de chapitre ou scène linéaire).
     */
    @Builder.Default
    private List<SceneBranch> branches = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
