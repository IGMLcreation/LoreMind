package com.loremind.infrastructure.web.mapper;

import com.loremind.domain.shared.template.FieldType;
import com.loremind.domain.shared.template.ImageLayout;
import com.loremind.domain.shared.template.TemplateField;
import com.loremind.infrastructure.web.dto.shared.TemplateFieldDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Mapper pour convertir entre {@link TemplateField} (domaine) et
 * {@link TemplateFieldDTO} (wire).
 * <p>
 * Tolerance : un type inconnu recu du client est interprete comme TEXT.
 * Un layout inconnu ou absent sur un champ IMAGE est interprete comme GALLERY.
 * Layout/labels forces a null pour les types qui ne les utilisent pas.
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
        List<String> labels = null;
        if (field.getType() == FieldType.KEY_VALUE_LIST && field.getLabels() != null) {
            labels = new ArrayList<>(field.getLabels());
        }
        return new TemplateFieldDTO(field.getName(), typeStr, layoutStr, labels);
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
        List<String> labels = null;
        if (type == FieldType.KEY_VALUE_LIST && dto.getLabels() != null) {
            labels = new ArrayList<>(dto.getLabels());
        }
        return new TemplateField(dto.getName(), type, layout, labels);
    }
}
