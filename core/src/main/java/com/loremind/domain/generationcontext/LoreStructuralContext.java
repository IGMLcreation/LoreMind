package com.loremind.domain.generationcontext;

import java.util.List;
import java.util.Map;

/**
 * Carte structurelle enrichie d'un Lore pour nourrir l'IA.
 * <p>
 * Équivalent Java du LoreStructuralContext Python. Depuis l'étape b9,
 * chaque page expose ses valeurs de champs, ses tags et ses pages liées
 * (résolues en titres) — plus uniquement son nom et son template.
 * <p>
 * La map `folders` est indexée par nom de dossier et mappe vers la liste
 * des pages qu'il contient (liste vide autorisée pour les dossiers vides).
 * <p>
 * Record Java : pur domaine, aucune dépendance technique.
 */
public record LoreStructuralContext(
        String loreName,
        String loreDescription,
        Map<String, List<PageSummary>> folders,
        List<String> tags) {

    /**
     * Résumé projeté d'une page pour l'IA.
     * <p>
     * Contient le contenu utile au raisonnement LLM :
     *  - title + templateName : identification
     *  - values : contenu des champs dynamiques (tronqué côté builder)
     *  - tags : étiquettes métier
     *  - relatedPageTitles : pages liées DÉJÀ résolues en titres lisibles
     *    (les IDs techniques n'ont aucune utilité dans un prompt LLM).
     * <p>
     * Les notes privées du MJ ne figurent PAS ici (choix b9 : exposer
     * uniquement ce qui est partageable en narration — les secrets MJ
     * restent confinés à leur page d'édition).
     */
    public record PageSummary(
            String title,
            String templateName,
            Map<String, String> values,
            List<String> tags,
            List<String> relatedPageTitles) {
    }
}
