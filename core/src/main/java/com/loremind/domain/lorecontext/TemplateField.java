package com.loremind.domain.lorecontext;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Value Object d'un champ de Template.
 * <p>
 * Un champ a un nom (affiche dans l'UI) et un type (TEXT ou IMAGE, extensible).
 * Le type pilote le rendu cote front (textarea vs galerie d'images) ET
 * la logique metier (seuls les champs TEXT sont envoyes a l'IA pour generation).
 * <p>
 * Pour les champs IMAGE, {@link #layout} precise la variante de rendu
 * (gallery/hero/masonry/carousel). Nullable : l'absence equivaut a GALLERY.
 * Ignore pour les champs TEXT.
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

    /** Constructeur de retrocompat : type seul, layout=null. */
    public TemplateField(String name, FieldType type) {
        this(name, type, null);
    }

    /** Raccourci : construit un champ de type TEXT (cas le plus courant). */
    public static TemplateField text(String name) {
        return new TemplateField(name, FieldType.TEXT, null);
    }

    /** Raccourci : construit un champ de type IMAGE avec layout GALLERY. */
    public static TemplateField image(String name) {
        return new TemplateField(name, FieldType.IMAGE, ImageLayout.GALLERY);
    }

    /** Raccourci : construit un champ IMAGE avec un layout specifique. */
    public static TemplateField image(String name, ImageLayout layout) {
        return new TemplateField(name, FieldType.IMAGE, layout);
    }
}
