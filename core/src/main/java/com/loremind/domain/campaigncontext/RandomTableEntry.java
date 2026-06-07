package com.loremind.domain.campaigncontext;

import lombok.Builder;
import lombok.Data;

/**
 * Une entrée d'une {@link RandomTable} : une PLAGE de jet (minRoll..maxRoll, bornes
 * incluses) qui mappe vers un résultat. Les plages permettent les tables PONDÉRÉES
 * (un résultat couvrant 1–10 est plus probable qu'un couvrant 11–12).
 * <p>
 * Value object possédé par la table (pas d'identité propre côté domaine) : à chaque
 * mise à jour, les entrées sont remplacées en bloc.
 */
@Data
@Builder
public class RandomTableEntry {

    /** Borne basse du jet (incluse). */
    private int minRoll;

    /** Borne haute du jet (incluse). Pour une entrée unitaire, min == max. */
    private int maxRoll;

    /** Résultat court affiché (ex. "Embuscade de gobelins"). */
    private String label;

    /** Détail markdown : « ce que c'est » (effet, description). Nullable. */
    private String detail;
}
