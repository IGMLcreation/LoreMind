package com.loremind.domain.gamesystemcontext;

import com.loremind.domain.shared.template.TemplateField;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Entité de domaine représentant un GameSystem (système de JDR).
 * <p>
 * Porte les règles d'un système (D&D, Nimble, Pathfinder, homebrew...) sous forme
 * d'un markdown monolithique structuré par titres H2. Les sections sont extraites
 * à la volée lors de l'injection dans les prompts IA (cf. GameSystemContextSelector).
 * <p>
 * Porte aussi deux templates piloтant la structure des fiches PJ et PNJ d'une
 * campagne adossée à ce système. Les fiches markdown libres ont laissé place à
 * un système de champs typés (TEXT/IMAGE/NUMBER) défini ici.
 * <p>
 * {@code author} et {@code isPublic} sont des champs pensés pour un futur marketplace
 * de rulesets partagés — non exploités au MVP mais persistés dès maintenant pour
 * éviter une migration ultérieure.
 */
@Data
@Builder
public class GameSystem {

    private String id;
    private String name;
    private String description;

    /** Markdown monolithique. Sections découpées par titres H2 (## Combat, ## Classes, etc.). */
    private String rulesMarkdown;

    /**
     * Template de fiche PJ : champs typés affichés pour chaque personnage joueur.
     * Hors champs universels hard-codés (nom, portrait, header). Jamais null après
     * persistance — un template vide est représenté par une liste vide.
     */
    private List<TemplateField> characterTemplate;

    /**
     * Template de fiche PNJ. Mêmes règles que {@link #characterTemplate}.
     * Distinct du template PJ car les invariants métier divergent (un PNJ peut
     * n'avoir qu'un nom + une motivation, un PJ porte généralement une feuille
     * de stats complète).
     */
    private List<TemplateField> npcTemplate;

    /** Auteur déclaré — futur marketplace. Nullable. */
    private String author;

    /** Flag de partage — futur marketplace. False par défaut. */
    private boolean isPublic;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // --- Méthodes métier : templates PJ/PNJ --------------------------------

    /**
     * Ajoute un champ au template PJ. Refuse les doublons de nom (insensible à la casse)
     * pour éviter les collisions de clés dans {@code Character.values}.
     */
    public void addCharacterField(TemplateField field) {
        characterTemplate = appendField(characterTemplate, field);
    }

    /** Pendant PNJ de {@link #addCharacterField}. */
    public void addNpcField(TemplateField field) {
        npcTemplate = appendField(npcTemplate, field);
    }

    /**
     * Retire un champ du template PJ par nom (insensible à la casse).
     * No-op silencieux si le champ n'existe pas — appelant n'a pas à pré-vérifier.
     */
    public void removeCharacterField(String fieldName) {
        characterTemplate = removeFieldByName(characterTemplate, fieldName);
    }

    public void removeNpcField(String fieldName) {
        npcTemplate = removeFieldByName(npcTemplate, fieldName);
    }

    /**
     * Remplace intégralement le template PJ. Utilisé pour le réordonnancement
     * et l'édition en bloc côté UI. Valide l'unicité des noms.
     */
    public void replaceCharacterTemplate(List<TemplateField> fields) {
        characterTemplate = validateAndCopy(fields);
    }

    public void replaceNpcTemplate(List<TemplateField> fields) {
        npcTemplate = validateAndCopy(fields);
    }

    // --- Helpers privés ----------------------------------------------------

    private static List<TemplateField> appendField(List<TemplateField> current, TemplateField field) {
        if (field == null || field.getName() == null || field.getName().isBlank()) {
            throw new IllegalArgumentException("Field name is required");
        }
        List<TemplateField> next = current == null ? new ArrayList<>() : new ArrayList<>(current);
        if (containsName(next, field.getName())) {
            throw new IllegalArgumentException("Duplicate field name: " + field.getName());
        }
        next.add(field);
        return next;
    }

    private static List<TemplateField> removeFieldByName(List<TemplateField> current, String fieldName) {
        if (current == null || fieldName == null) return current;
        List<TemplateField> next = new ArrayList<>(current);
        next.removeIf(f -> equalsIgnoreCase(f.getName(), fieldName));
        return next;
    }

    private static List<TemplateField> validateAndCopy(List<TemplateField> fields) {
        if (fields == null) return new ArrayList<>();
        List<TemplateField> copy = new ArrayList<>(fields.size());
        for (TemplateField f : fields) {
            if (f == null || f.getName() == null || f.getName().isBlank()) {
                throw new IllegalArgumentException("Field name is required");
            }
            if (containsName(copy, f.getName())) {
                throw new IllegalArgumentException("Duplicate field name: " + f.getName());
            }
            copy.add(f);
        }
        return copy;
    }

    private static boolean containsName(List<TemplateField> fields, String name) {
        return fields.stream().anyMatch(f -> equalsIgnoreCase(f.getName(), name));
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        if (a == null || b == null) return a == b;
        return a.toLowerCase(Locale.ROOT).equals(b.toLowerCase(Locale.ROOT));
    }
}
