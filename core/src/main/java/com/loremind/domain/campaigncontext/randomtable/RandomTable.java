package com.loremind.domain.campaigncontext.randomtable;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Table aléatoire d'une campagne : on jette un dé ({@code diceFormula}) et la
 * valeur tombée désigne une {@link RandomTableEntry} (par sa plage) → un résultat.
 * <p>
 * Outil MJ classique (rencontres, butin, complications, noms…). Le JET lui-même
 * est effectué côté client (instantané, comme le panneau de dés) ; le domaine ne
 * fait que stocker la table et ses entrées. Scope campagne (cross-aggregate via ID).
 */
@Data
@Builder
public class RandomTable {

    private String id;
    private String name;

    /** Description libre (à quoi sert la table). Nullable. */
    private String description;

    /** Formule du dé à lancer : "1d20", "2d6", "d100"… */
    private String diceFormula;

    /** Clé d'icône (lucide) pour la sidebar/fiche. Nullable. */
    private String icon;

    /** Référence vers la Campaign parente (cross-aggregate via ID). */
    private String campaignId;

    /** Ordre d'affichage dans la liste des tables de la campagne. */
    private int order;

    /** Entrées ordonnées (par plage de jet). Jamais null après construction. */
    private List<RandomTableEntry> entries;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public List<RandomTableEntry> getEntries() {
        if (entries == null) entries = new ArrayList<>();
        return entries;
    }
}
