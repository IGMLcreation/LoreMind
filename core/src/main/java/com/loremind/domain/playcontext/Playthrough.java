package com.loremind.domain.playcontext;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Instance jouée d'une Campagne par une table donnée.
 *
 * <p>Sépare clairement le SCÉNARIO (Campaign : arcs, chapitres, prérequis) de
 * l'ÉTAT DE JEU d'une table précise (progression des quêtes, flags narratifs,
 * sessions tenues, PJ). Permet à plusieurs tables de jouer la même campagne
 * indépendamment.</p>
 *
 * <p>Fait partie du Play Context. Référence la Campagne par weak reference
 * (campaignId) pour respecter les Bounded Contexts.</p>
 */
@Data
@Builder
public class Playthrough {

    private String id;

    /** Weak reference vers la Campagne (le scénario joué). */
    private String campaignId;

    /** Nom donné par le MJ à cette partie (ex. : "Table du vendredi"). */
    private String name;

    /** Notes libres sur la partie / la table — facultatif. */
    private String description;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
