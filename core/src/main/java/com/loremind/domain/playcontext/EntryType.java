package com.loremind.domain.playcontext;

/**
 * Type d'entrée du journal de session.
 * Permet à l'UI de catégoriser visuellement la timeline (icône, couleur).
 */
public enum EntryType {
    /** Note libre du MJ (défaut). */
    NOTE,
    /** Moment marquant du scénario (combat gagné, décision majeure...). */
    EVENT,
    /** Jet de dés / test de caractéristique. */
    DICE_ROLL,
    /** Action déclarée par un joueur. */
    PLAYER_ACTION
}
