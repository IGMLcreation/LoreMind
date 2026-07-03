package com.loremind.domain.campaigncontext;

/**
 * Statut de PRÉPARATION d'une entité de scénario (Pilier B « guidage / readiness »).
 *
 * <p>À NE PAS confondre avec la progression EN PARTIE ({@link ProgressionStatus} /
 * {@link QuestStatus}, Play Context). Le readiness décrit « ce scénario est-il prêt
 * à jouer ? » et se calcule uniquement depuis les champs du Campaign Context —
 * jamais depuis un Playthrough. Ordre implicite DRAFT &lt; PLAYABLE &lt; POLISHED,
 * agrégé vers le haut en MIN() (le maillon faible tire l'ensemble vers le bas).</p>
 */
public enum ReadinessStatus {

    /** Il reste au moins un manque BLOQUANT (structure cassée, vide, référence morte). */
    DRAFT,

    /** Plus aucun manque bloquant : jouable, mais il reste des manques recommandés. */
    PLAYABLE,

    /** Plus aucun manque bloquant ni recommandé : prêt « clé en main ». */
    POLISHED
}
