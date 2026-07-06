package com.loremind.domain.campaigncontext.structure;

/**
 * Type narratif d'un nœud (Scène) — Niveau 2 (graphe de nœuds typés).
 *
 * <p>Métadonnée qui oriente le rendu et l'édition dans la vue graphe ; elle
 * n'altère pas le comportement existant des scènes. {@code GENERIC} = scène non
 * typée (valeur par défaut, et celle des scènes antérieures au Niveau 2).</p>
 */
public enum SceneType {
    /** Scène non typée (défaut). */
    GENERIC,
    /** Un lieu à explorer. */
    LOCATION,
    /** Une rencontre / un combat. */
    ENCOUNTER,
    /** Une situation centrée sur un PNJ. */
    NPC,
    /** Un événement scénarisé. */
    EVENT,
    /** Une révélation / un indice majeur. */
    REVELATION
}
