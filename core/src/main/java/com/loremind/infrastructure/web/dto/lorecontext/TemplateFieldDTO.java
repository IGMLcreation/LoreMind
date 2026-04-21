package com.loremind.infrastructure.web.dto.lorecontext;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour un champ de Template.
 * <p>
 * Miroir wire-friendly de {@link com.loremind.domain.lorecontext.TemplateField}.
 * Le type est serialise en string (TEXT/IMAGE) pour interop facile avec Angular.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TemplateFieldDTO {
    private String name;
    /** "TEXT" ou "IMAGE" (string pour serialisation JSON transparente). */
    private String type;
}
