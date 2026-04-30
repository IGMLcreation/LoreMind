package com.loremind.domain.shared.template;

/**
 * Type d'un champ dynamique de template (kernel partage).
 * <p>
 * - TEXT            : valeur textuelle libre (Map<String, String>)
 * - IMAGE           : galerie d'images, liste d'IDs (Map<String, List<String>>)
 * - NUMBER          : valeur numerique stockee en texte (parsee a l'usage)
 * - KEY_VALUE_LIST  : liste de paires {label, value} avec labels figes au template
 *                     (Map<String, Map<String, String>> : fieldName -> label -> value).
 *                     Usage : stat blocks, listes de competences, traits.
 * <p>
 * Extension future possible : RICH_TEXT, DATE, BOOLEAN, REFERENCE...
 */
public enum FieldType {
    TEXT,
    IMAGE,
    NUMBER,
    KEY_VALUE_LIST
}
