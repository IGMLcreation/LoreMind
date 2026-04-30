package com.loremind.domain.shared.template;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Value Object d'un champ de Template (kernel partage).
 * <p>
 * Un champ a un nom (affiche dans l'UI) et un type. Le type pilote
 * le rendu cote front et la logique metier (seuls les champs TEXT sont
 * envoyes a l'IA pour generation).
 * <p>
 * Pour les champs IMAGE, {@link #layout} precise la variante de rendu
 * (gallery/hero/masonry/carousel). Nullable : l'absence equivaut a GALLERY.
 * Ignore pour les autres types.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateField {
    /** Nom du champ tel qu'affiche dans l'UI (ex: "Histoire", "Portrait"). */
    private String name;
    /** Type du champ, pilote le rendu et la generation IA. */
    private FieldType type;
    /** Variante de rendu pour les champs IMAGE. Null = GALLERY. */
    private ImageLayout layout;
    /**
     * Labels predefinis pour les champs KEY_VALUE_LIST (ordre significatif).
     * Ex: ["FOR","DEX","CON","INT","SAG","CHA"] pour un champ "Caracteristiques".
     * Null/vide pour les autres types.
     */
    private List<String> labels;

    /** Constructeur de retrocompat : type seul, layout/labels=null. */
    public TemplateField(String name, FieldType type) {
        this(name, type, null, null);
    }

    /** Constructeur de retrocompat : type + layout, labels=null. */
    public TemplateField(String name, FieldType type, ImageLayout layout) {
        this(name, type, layout, null);
    }

    /** Raccourci : construit un champ de type TEXT (cas le plus courant). */
    public static TemplateField text(String name) {
        return new TemplateField(name, FieldType.TEXT, null, null);
    }

    /** Raccourci : construit un champ de type IMAGE avec layout GALLERY. */
    public static TemplateField image(String name) {
        return new TemplateField(name, FieldType.IMAGE, ImageLayout.GALLERY, null);
    }

    /** Raccourci : construit un champ IMAGE avec un layout specifique. */
    public static TemplateField image(String name, ImageLayout layout) {
        return new TemplateField(name, FieldType.IMAGE, layout, null);
    }

    /** Raccourci : construit un champ de type NUMBER. */
    public static TemplateField number(String name) {
        return new TemplateField(name, FieldType.NUMBER, null, null);
    }

    /** Raccourci : construit un champ KEY_VALUE_LIST avec labels predefinis. */
    public static TemplateField keyValueList(String name, List<String> labels) {
        return new TemplateField(name, FieldType.KEY_VALUE_LIST, null, labels);
    }
}
