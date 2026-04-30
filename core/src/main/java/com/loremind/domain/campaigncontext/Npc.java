package com.loremind.domain.campaigncontext;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fiche de personnage non-joueur (PNJ) d'une campagne.
 * <p>
 * Entité dédiée distincte de {@link Character} (DDD assumé : invariants divergents
 * à terme — faction, statut vivant/mort, visibilité côté joueurs, etc.).
 * <p>
 * Mêmes champs universels hard-codés et meme structure de templating que Character,
 * pilotée par le template PNJ du GameSystem
 * ({@link com.loremind.domain.gamesystemcontext.GameSystem#getNpcTemplate}).
 * <p>
 * Scope campagne : les PNJ "univers" (worldboss, figures du Lore) restent gérés
 * via le système Page/Template du LoreContext.
 */
@Data
@Builder
public class Npc {

    private String id;
    private String name;

    /** ID de l'image portrait (champ universel hard-code). Nullable. */
    private String portraitImageId;

    /** ID de l'image header/banniere (champ universel hard-code). Nullable. */
    private String headerImageId;

    /** Valeurs TEXT/NUMBER du template PNJ. Jamais null apres construction. */
    private Map<String, String> values;

    /** Valeurs IMAGE du template PNJ (listes d'IDs ordonnees par champ). Jamais null. */
    private Map<String, List<String>> imageValues;

    /** Référence vers la Campaign parente (cross-aggregate via ID). */
    private String campaignId;

    /** Ordre d'affichage dans la liste des PNJ de la campagne. */
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
}
