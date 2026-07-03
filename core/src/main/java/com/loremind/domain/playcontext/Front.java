package com.loremind.domain.playcontext;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Front (menace) d'une Partie : regroupement nommé d'horloges de progression
 * ({@link Clock}) sous une même menace (ex. « La montée du Culte »). État de
 * Partie, comme les horloges — orthogonal à l'arbre Arc/Chapitre/Scène.
 */
@Data
@Builder
public class Front {

    private String id;

    /** Weak reference vers le Playthrough parent. */
    private String playthroughId;

    private String name;

    private String description;

    /** Ordre d'affichage parmi les fronts de la Partie. */
    private int order;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
