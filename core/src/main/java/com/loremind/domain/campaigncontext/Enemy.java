package com.loremind.domain.campaigncontext;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fiche d'ennemi (monstre/créature) d'une campagne — le bestiaire du MJ.
 * <p>
 * Même principe de templating que {@link Npc} : champs universels hard-codés
 * (nom, niveau, dossier, portrait, bandeau) + champs pilotés par le template
 * ENNEMI du GameSystem ({@code GameSystem.enemyTemplate} : CA, PV, attaques…).
 * Classement libre par dossier (« Démons », « Humanoïdes »…).
 */
@Data
@Builder
public class Enemy {

    private String id;
    private String name;

    /** Niveau / FP / dangerosité — texte libre (« 5 », « FP 8 », « Boss »). Nullable. */
    private String level;

    /** Dossier de classement (texte libre). Null = non classé. */
    private String folder;

    /** ID de l'image portrait (champ universel hard-codé). Nullable. */
    private String portraitImageId;

    /** ID de l'image header/bannière (champ universel hard-codé). Nullable. */
    private String headerImageId;

    /** Valeurs TEXT/NUMBER du template ennemi. Jamais null après construction. */
    private Map<String, String> values;

    /** Valeurs IMAGE du template ennemi (listes d'IDs ordonnées par champ). Jamais null. */
    private Map<String, List<String>> imageValues;

    /** Valeurs KEY_VALUE_LIST : fieldName -> label -> value. Jamais null. */
    private Map<String, Map<String, String>> keyValueValues;

    /** Référence vers la Campaign parente (cross-aggregate via ID). */
    private String campaignId;

    /**
     * Référence vers l'acteur Foundry d'origine (UUID de compendium, ex.
     * {@code Compendium.nimble.monsters.Actor.abc123}). Renseigné quand l'ennemi
     * a été importé depuis un compendium Foundry ; permet, à l'export, de poser
     * un token du VRAI acteur (stats natives). Null pour un ennemi fait main.
     */
    private String foundryRef;

    /** Ordre d'affichage dans la liste. */
    private int order;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Map<String, String> getValues() {
        if (values == null) values = new HashMap<>();
        return values;
    }

    public Map<String, List<String>> getImageValues() {
        if (imageValues == null) imageValues = new HashMap<>();
        return imageValues;
    }

    public Map<String, Map<String, String>> getKeyValueValues() {
        if (keyValueValues == null) keyValueValues = new HashMap<>();
        return keyValueValues;
    }
}
