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

    /** Retrocompat : constructeur sans labels. */
    public TemplateFieldDTO(String name, String type, String layout) {
        this(name, type, layout, null);
    }

    /** Retrocompat : constructeur sans layout ni labels. */
    public TemplateFieldDTO(String name, String type) {
        this(name, type, null, null);
    }
}
