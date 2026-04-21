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
 * Evolution de `List<String> fields` vers `List<TemplateField> fields` :
 * refactor propre (DDD Value Object polymorphism) permettant d'ajouter
 * facilement d'autres types de champs (DATE, NUMBER, RICH_TEXT...) sans
 * casser le contrat.
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

    /** Raccourci : construit un champ de type TEXT (cas le plus courant). */
    public static TemplateField text(String name) {
        return new TemplateField(name, FieldType.TEXT);
    }

    /** Raccourci : construit un champ de type IMAGE. */
    public static TemplateField image(String name) {
        return new TemplateField(name, FieldType.IMAGE);
    }
}
