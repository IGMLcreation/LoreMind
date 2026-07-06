package com.loremind.domain.campaigncontext.structure;

/**
 * Type d'un lien narratif entre nœuds ({@link SceneBranch}) — Niveau 2.
 *
 * <p>{@code EXIT} = sortie / choix narratif : c'est la sémantique historique des
 * branches, et la valeur par défaut (branches existantes / bundles antérieurs).
 * {@code CLUE} et {@code LEAD} enrichissent le graphe façon « Three Clue Rule ».</p>
 */
public enum LinkType {
    /** Sortie / choix narratif (défaut, comportement historique). */
    EXIT,
    /** Indice menant à une information. */
    CLUE,
    /** Piste vers un autre nœud. */
    LEAD
}
