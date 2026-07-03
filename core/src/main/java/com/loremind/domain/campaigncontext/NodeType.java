package com.loremind.domain.campaigncontext;

/**
 * Type de nœud narratif référencé par une {@link Quest} via {@link QuestNodeRef}.
 *
 * <p>Une quête est ORTHOGONALE à l'arbre Arc→Chapitre→Scène : elle peut pointer
 * un chapitre entier (CHAPTER) ou une scène précise (SCENE), et traverser
 * plusieurs nœuds. Le schéma supporte les deux dès le départ (décision D3) ;
 * l'UI démarre sur les chapitres.</p>
 */
public enum NodeType {
    CHAPTER,
    SCENE
}
