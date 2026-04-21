package com.loremind.domain.lorecontext;

/**
 * Type d'un champ dynamique d'un Template.
 * <p>
 * - TEXT : valeur textuelle libre (stockee dans Page.values : Map<String, String>)
 * - IMAGE : galerie d'images, represente comme une liste d'IDs d'images
 *           (stockee dans Page.imageValues : Map<String, List<String>>)
 * <p>
 * Extension future possible : RICH_TEXT, NUMBER, DATE, BOOLEAN, LORE_LINK...
 */
public enum FieldType {
    TEXT,
    IMAGE
}
