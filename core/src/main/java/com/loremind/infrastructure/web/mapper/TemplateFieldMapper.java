package com.loremind.infrastructure.web.mapper;

import com.loremind.domain.lorecontext.FieldType;
import com.loremind.domain.lorecontext.ImageLayout;
import com.loremind.domain.lorecontext.TemplateField;
import com.loremind.infrastructure.web.dto.lorecontext.TemplateFieldDTO;
import org.springframework.stereotype.Component;

/**
 * Mapper pour convertir entre {@link TemplateField} (domaine) et
 * {@link TemplateFieldDTO} (wire).
 * <p>
 * Tolerance : un type inconnu recu du client est interprete comme TEXT
 * (plus safe que de rejeter la requete et d'interrompre la sauvegarde).
 * Un layout inconnu ou absent sur un champ IMAGE est interprete comme GALLERY.
 * Le layout est force a null pour les champs TEXT.
 */
@Component
public class TemplateFieldMapper {

    public TemplateFieldDTO toDTO(TemplateField field) {
        if (field == null) return null;
        String typeStr = field.getType() != null ? field.getType().name() : FieldType.TEXT.name();
        String layoutStr = null;
        if (field.getType() == FieldType.IMAGE) {
            ImageLayout layout = field.getLayout() != null ? field.getLayout() : ImageLayout.GALLERY;
            layoutStr = layout.name();
        }
        return new TemplateFieldDTO(field.getName(), typeStr, layoutStr);
    }

    public TemplateField toDomain(TemplateFieldDTO dto) {
        if (dto == null) return null;
        FieldType type;
        try {
            type = dto.getType() != null ? FieldType.valueOf(dto.getType()) : FieldType.TEXT;
        } catch (IllegalArgumentException ex) {
            type = FieldType.TEXT;
        }
        ImageLayout layout = null;
        if (type == FieldType.IMAGE) {
            try {
                layout = dto.getLayout() != null
                        ? ImageLayout.valueOf(dto.getLayout())
                        : ImageLayout.GALLERY;
            } catch (IllegalArgumentException ex) {
                layout = ImageLayout.GALLERY;
            }
        }
        return new TemplateField(dto.getName(), type, layout);
    }
}
