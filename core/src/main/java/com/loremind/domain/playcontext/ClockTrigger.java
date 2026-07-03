package com.loremind.domain.playcontext;

/**
 * Déclencheur d'avancement automatique d'une {@link Clock} (co-MJ) : l'horloge
 * avance d'un segment quand l'événement lié survient dans la Partie. Le MJ garde
 * la main (il peut reculer). {@code triggerRef} précise la cible selon le type.
 */
public enum ClockTrigger {
    /** Aucun : l'horloge n'avance qu'à la main. */
    NONE,
    /** Quand un Fait (flag) passe à vrai — {@code triggerRef} = nom du fait. */
    FLAG_SET,
    /** Quand une quête passe à COMPLETED — {@code triggerRef} = id de la quête. */
    QUEST_COMPLETED,
    /** À la clôture d'une séance de la Partie — pas de {@code triggerRef}. */
    SESSION_ENDED
}
