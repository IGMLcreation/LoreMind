package com.loremind.domain.campaigncontext.quest;

/**
 * Value Object : lien faible d'une {@link Quest} vers un nœud narratif
 * (Chapitre ou Scène) qu'elle traverse.
 *
 * <p>Référence faible (pas de FK dure cross-aggregate, cohérent avec
 * {@code relatedPageIds}) : une quête peut couvrir plusieurs nœuds et un nœud
 * peut servir plusieurs quêtes (N‑N). Immuable (record), comme
 * {@link SceneBranch} / {@link RoomBranch}.</p>
 *
 * @param nodeType type du nœud cible (CHAPTER|SCENE)
 * @param nodeId   id du Chapter ou de la Scene (weak ref)
 * @param order    ordre d'affichage du nœud dans la quête
 */
public record QuestNodeRef(NodeType nodeType, String nodeId, int order) {
}
