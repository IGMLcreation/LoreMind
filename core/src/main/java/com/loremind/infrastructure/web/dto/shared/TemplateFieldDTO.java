package com.loremind.infrastructure.web.dto.shared;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    /** "TEXT" ou "IMAGE" (string pour serialisation JSON transparente). */
    private String type;
    /** "GALLERY" | "HERO" | "MASONRY" | "CAROUSEL", null si type=TEXT. */
    private String layout;

    /** Retrocompat : constructeur sans layout. */
    public TemplateFieldDTO(String name, String type) {
        this(name, type, null);
    }
}
