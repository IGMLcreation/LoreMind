package com.loremind.domain.campaigncontext.writingreview;

/**
 * Famille d'une remarque de relecture (Mentor d'écriture, phase M2). Sert à
 * regrouper les conseils côté front ; n'implique aucune gravité : une remarque de
 * relecture est un conseil d'auteur, jamais un blocage.
 */
public enum WritingRemarkCategory {
    /** Pouvoir des joueurs : vrais choix, chemins multiples, issues alternatives. */
    AGENCY,
    /** Rythme : variété des scènes, lisibilité de la structure. */
    PACING,
    /** Enjeux : ce que les joueurs risquent, pourquoi l'adversité agit. */
    STAKES,
    /** Narration : textes lus aux joueurs. */
    NARRATION
}
