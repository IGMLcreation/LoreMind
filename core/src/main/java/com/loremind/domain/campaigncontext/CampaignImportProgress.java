package com.loremind.domain.campaigncontext;

/**
 * Évènement d'avancement émis pendant l'import streamé d'un PDF de campagne.
 * <p>
 * {@code total} = nombre de morceaux à traiter (0 pendant l'extraction).
 * {@code current} = morceaux traités. Les compteurs arc/chapitre/scène donnent
 * un aperçu de l'arbre trouvé jusqu'ici (affichage « au fil de l'eau »).
 */
public record CampaignImportProgress(
        int current,
        int total,
        int pageCount,
        int ocrPageCount,
        int arcCount,
        int chapterCount,
        int sceneCount) {
}
