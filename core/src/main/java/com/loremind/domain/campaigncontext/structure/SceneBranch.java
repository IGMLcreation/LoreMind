package com.loremind.domain.campaigncontext.structure;

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
 * @param kind           Type de lien (Niveau 2). {@code null} normalisé en {@link LinkType#EXIT}.
 */
public record SceneBranch(String label, String targetSceneId, String condition, LinkType kind) {

    /** Normalise un {@code kind} absent (branches / bundles antérieurs au Niveau 2) vers EXIT. */
    public SceneBranch {
        if (kind == null) kind = LinkType.EXIT;
    }

    /** Constructeur 3-args rétro-compatible : {@code kind} par défaut {@link LinkType#EXIT}. */
    public SceneBranch(String label, String targetSceneId, String condition) {
        this(label, targetSceneId, condition, LinkType.EXIT);
    }

    /** Raccourci pour construire une branche sans condition (cas le plus courant). */
    public static SceneBranch of(String label, String targetSceneId) {
        return new SceneBranch(label, targetSceneId, null);
    }
}
