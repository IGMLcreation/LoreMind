package com.loremind.infrastructure.web.dto.shared;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO pour un champ de Template.
 * <p>
 * Miroir wire-friendly de {@link com.loremind.domain.shared.template.TemplateField}.
 * Le type est serialise en string (TEXT/IMAGE) pour interop facile avec Angular.
 * Le layout (null pour TEXT, ou GALLERY/HERO/MASONRY/CAROUSEL pour IMAGE) pilote
 * le rendu visuel des champs image cote front.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TemplateFieldDTO {
    private String name;
    /** "TEXT" | "IMAGE" | "NUMBER" | "KEY_VALUE_LIST". */
    private String type;
    /** "GALLERY" | "HERO" | "MASONRY" | "CAROUSEL", uniquement pour IMAGE. */
    private String layout;
    /** Labels predefinis pour KEY_VALUE_LIST (ordre significatif). */
    private List<String> labels;

    /** Chemin Foundry du champ (mapping pour l'export d'acteur typé). Nullable. */
    private String foundryPath;

    /**
     * Identifiant STABLE du bloc (cle d'ancrage des valeurs de Page). Retro-rempli
     * avec le nom cote backend pour les templates anterieurs. Appended en fin de
     * classe pour preserver la compat des constructeurs historiques.
     */
    private String id;

    /** Placement du bloc dans la grille 12 colonnes. Null = auto-flow empile. */
    private BlockPositionDTO pos;

    /** Retrocompat : constructeur sans id ni pos. */
    public TemplateFieldDTO(String name, String type, String layout, List<String> labels, String foundryPath) {
        this(name, type, layout, labels, foundryPath, null, null);
    }

    /** Retrocompat : constructeur sans foundryPath. */
    public TemplateFieldDTO(String name, String type, String layout, List<String> labels) {
        this(name, type, layout, labels, null);
    }

    /** Retrocompat : constructeur sans labels. */
    public TemplateFieldDTO(String name, String type, String layout) {
        this(name, type, layout, null, null);
    }

    /** Retrocompat : constructeur sans layout ni labels. */
    public TemplateFieldDTO(String name, String type) {
        this(name, type, null, null, null);
    }
}
