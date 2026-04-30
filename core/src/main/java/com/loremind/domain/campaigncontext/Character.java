package com.loremind.domain.campaigncontext;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fiche de personnage joueur (PJ) d'une campagne.
 * <p>
 * Champs universels hard-codes : {@code name}, {@code portraitImageId},
 * {@code headerImageId}. Tout le reste est piloté par le template PJ du
 * GameSystem associé à la campagne (cf. {@link com.loremind.domain.gamesystemcontext.GameSystem#getCharacterTemplate}).
 * <p>
 * Les valeurs des champs templates sont stockées dans deux maps :
 *  - {@code values} : champs TEXT et NUMBER (numérique sérialisé en string,
 *    parsé à l'usage cote presentation)
 *  - {@code imageValues} : champs IMAGE (liste ordonnée d'IDs d'images par champ)
 * <p>
 * Le champ historique {@code markdownContent} a été supprimé (refonte 2026-04-30).
 * Le contenu pre-existant est migré dans {@code values["Notes"]} par défaut.
 * <p>
 * Scope strict PJ : les PNJ sont gérés par l'entité {@link Npc} (invariants divergents).
 */
@Data
@Builder
public class Character {

    private String id;
    private String name;

    /** ID de l'image portrait (champ universel hard-codé). Nullable. */
    private String portraitImageId;

    /** ID de l'image header/banniere (champ universel hard-codé). Nullable. */
    private String headerImageId;

    /**
     * Valeurs des champs TEXT et NUMBER du template PJ. Cle = nom du champ
     * (sensible a la casse cote stockage mais comparaison case-insensitive
     * dans le domaine GameSystem). Jamais null apres construction.
     */
    private Map<String, String> values;

    /**
     * Valeurs des champs IMAGE du template PJ. Cle = nom du champ, valeur =
     * liste ordonnee d'IDs d'images. Jamais null apres construction.
     */
    private Map<String, List<String>> imageValues;

    /** Référence vers la Campaign parente. */
    private String campaignId;

    /** Ordre d'affichage dans la liste des PJ de la campagne. */
    private int order;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Garantit que les maps ne sont jamais null cote consommateur. */
    public Map<String, String> getValues() {
        if (values == null) values = new HashMap<>();
        return values;
    }

    public Map<String, List<String>> getImageValues() {
        if (imageValues == null) imageValues = new HashMap<>();
        return imageValues;
    }
}
