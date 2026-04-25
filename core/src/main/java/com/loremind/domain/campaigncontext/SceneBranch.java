package com.loremind.domain.campaigncontext;

/**
 * Value Object représentant une "sortie" narrative depuis une Scene.
 * Décrit un choix offert aux joueurs et la scène de destination associée.
 * <p>
 * Record Java : immuable par construction, sans aucune dépendance technique
 * (pas de Lombok, pas de Jackson). Jackson 2.12+ sait sérialiser/désérialiser
 * les records nativement via le constructeur canonique — c'est ce dont
 * dépend le SceneBranchListJsonConverter pour le stockage JSONB.
 * <p>
 * Règle métier : targetSceneId DOIT pointer vers une Scene du MÊME Chapter
 * (validation portée par SceneService).
 *
 * @param label          Libellé du choix (ex: "Si les joueurs attaquent le garde").
 * @param targetSceneId  Id de la Scene de destination, intra-chapitre uniquement.
 * @param condition      Notes MJ privées sur la condition de déclenchement (optionnel).
 */
public record SceneBranch(String label, String targetSceneId, String condition) {

    /** Raccourci pour construire une branche sans condition (cas le plus courant). */
    public static SceneBranch of(String label, String targetSceneId) {
        return new SceneBranch(label, targetSceneId, null);
    }
}
