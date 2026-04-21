package com.loremind.domain.lorecontext;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entité de domaine représentant un Template pour la génération de Pages.
 * <p>
 * Un Template :
 *  - appartient à un Lore (loreId)
 *  - définit le noeud par défaut où seront rangées les Pages créées (defaultNodeId)
 *  - porte une liste ordonnée de {@link TemplateField} (nom + type TEXT/IMAGE)
 *    qui seront instanciés sur chaque Page produite depuis ce gabarit.
 * <p>
 * Evolution : les `fields` etaient autrefois de simples `List<String>` (noms seuls).
 * Depuis l'ajout du support des images, chaque champ a un type discriminant pour
 * piloter le rendu UI et la logique IA.
 * <p>
 * Entité pure du domaine : aucune dépendance technique (Spring, JPA, etc.).
 */
@Data
@Builder
public class Template {

    private String id;
    private String loreId;                // Rattachement au Lore propriétaire
    private String name;
    private String description;
    private String defaultNodeId;          // Noeud cible des Pages générées
    private List<TemplateField> fields;    // Champs dynamiques ordonnes (nom + type)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // --- Méthodes métier ---------------------------------------------------

    /** Nombre de champs dynamiques définis (affiché dans le sidebar "X champs"). */
    public int fieldCount() {
        return fields == null ? 0 : fields.size();
    }

    /**
     * Retourne uniquement les noms des champs de type TEXT.
     * Utilise par l'IA : seul le texte peut etre genere, pas les images.
     */
    public List<String> textFieldNames() {
        if (fields == null) return new ArrayList<>();
        return fields.stream()
                .filter(f -> f.getType() == FieldType.TEXT)
                .map(TemplateField::getName)
                .toList();
    }
}
