package com.loremind.domain.shared.template;

/**
 * Type d'un champ dynamique de template (kernel partage).
 * <p>
 * - TEXT   : valeur textuelle libre (Map<String, String>)
 * - IMAGE  : galerie d'images, liste d'IDs (Map<String, List<String>>)
 * - NUMBER : valeur numerique stockee en texte (parsee a l'usage)
 * <p>
 * Extension future possible : RICH_TEXT, DATE, BOOLEAN, KEY_VALUE_LIST, REFERENCE...
 */
public enum FieldType {
    TEXT,
    IMAGE,
    NUMBER
}
