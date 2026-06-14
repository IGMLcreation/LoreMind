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
 * - TABLE           : tableau a colonnes figees au template (TemplateField.labels =
 *                     noms de colonnes) et lignes LIBRES ajoutees au remplissage
 *                     (Map<String, List<Map<String, String>>> : fieldName -> lignes,
 *                     chaque ligne = colonne -> cellule).
 *                     Usage : inventaire de boutique, tables d'objets, listes de prix.
 * <p>
 * Extension future possible : RICH_TEXT, DATE, BOOLEAN, REFERENCE...
 */
public enum FieldType {
    TEXT,
    IMAGE,
    NUMBER,
    KEY_VALUE_LIST,
    TABLE
}
