package com.loremind.domain.campaigncontext.readiness;

/**
 * Gravité d'un manque de préparation détecté par le guidage (Pilier B).
 *
 * <p>Purement indicatif : aucun niveau ne BLOQUE une action. Le guidage conseille,
 * il n'interdit rien (un MJ qui improvise n'est jamais empêché).</p>
 */
public enum ReadinessSeverity {

    /** Empêche de jouer proprement : structure cassée, entité vide, référence morte. */
    BLOCKING,

    /** Fortement conseillé pour la cohérence / le confort de jeu (ex. combat sans ennemi). */
    RECOMMENDED,

    /** Finition (ambiance, illustrations…). Masqué par défaut dans l'UI. */
    OPTIONAL
}
