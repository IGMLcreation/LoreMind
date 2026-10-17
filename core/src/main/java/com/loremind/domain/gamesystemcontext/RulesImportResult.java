package com.loremind.domain.gamesystemcontext;

import java.util.Map;

/**
 * Proposition de règles extraites d'un PDF, prête à être révisée par l'utilisateur.
 * <p>
 * {@code sections} associe un titre de section à son contenu markdown — aligné
 * sur le format {@link GameSystem#getRulesMarkdown()} (découpé par titres H2).
 * C'est une PROPOSITION : rien n'est persisté ; l'UI laisse l'utilisateur
 * réviser/éditer avant d'enregistrer le GameSystem.
 * <p>
 * {@code ocrPageCount} indique combien de pages ont nécessité l'OCR (scan) —
 * 0 = PDF born-digital (couche texte présente).
 */
public record RulesImportResult(
        Map<String, String> sections,
        int pageCount,
        int ocrPageCount,
        /** Règles en deux langues : {code langue → sections} (« fr », « en »). */
        Map<String, Map<String, String>> sectionsByLanguage,
        /** Langue d'origine du livre (extraction fidèle) ; l'autre version est traduite. */
        String sourceLanguage) {

    /** Résultat dans une seule langue (Brain antérieur, ou traduction indisponible). */
    public RulesImportResult(Map<String, String> sections, int pageCount, int ocrPageCount) {
        this(sections, pageCount, ocrPageCount, Map.of(), null);
    }
}
