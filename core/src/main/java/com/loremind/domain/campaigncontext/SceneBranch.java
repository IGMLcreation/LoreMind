package com.loremind.domain.campaigncontext;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Value Object représentant une "sortie" narrative depuis une Scene.
 * Décrit un choix offert aux joueurs et la scène de destination associée.
 * <p>
 * Immuable (@Value) : pour "modifier" une branche on la remplace.
 * @Jacksonized : permet à Jackson (sérialisation JSON via le converter JPA)
 * de reconstruire l'objet en passant par le builder malgré l'absence de setters.
 * <p>
 * Règle métier : targetSceneId DOIT pointer vers une Scene du MÊME Chapter
 * (validation portée par SceneService).
 */
@Value
@Builder
@Jacksonized
public class SceneBranch {

    /** Libellé du choix (ex: "Si les joueurs attaquent le garde"). */
    String label;

    /** Id de la Scene de destination, intra-chapitre uniquement. */
    String targetSceneId;

    /** Notes MJ privées sur la condition de déclenchement (optionnel). */
    String condition;
}
