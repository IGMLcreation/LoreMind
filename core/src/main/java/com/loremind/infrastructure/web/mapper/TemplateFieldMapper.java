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
        if ((field.getType() == FieldType.KEY_VALUE_LIST || field.getType() == FieldType.TABLE)
                && field.getLabels() != null) {
            labels = new ArrayList<>(field.getLabels());
        }
        return new TemplateFieldDTO(field.getName(), typeStr, layoutStr, labels, field.getFoundryPath());
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
        if ((type == FieldType.KEY_VALUE_LIST || type == FieldType.TABLE) && dto.getLabels() != null) {
            labels = new ArrayList<>(dto.getLabels());
        }
        return new TemplateField(dto.getName(), type, layout, labels, dto.getFoundryPath());
    }

    /** Mappe une liste de champs domaine → DTO ({@code null} → liste vide). */
    public List<TemplateFieldDTO> toDTOList(List<TemplateField> fields) {
        if (fields == null) return new ArrayList<>();
        List<TemplateFieldDTO> out = new ArrayList<>(fields.size());
        for (TemplateField f : fields) out.add(toDTO(f));
        return out;
    }

    /** Mappe une liste de champs DTO → domaine ({@code null} → liste vide). */
    public List<TemplateField> toDomainList(List<TemplateFieldDTO> dtos) {
        if (dtos == null) return new ArrayList<>();
        List<TemplateField> out = new ArrayList<>(dtos.size());
        for (TemplateFieldDTO d : dtos) out.add(toDomain(d));
        return out;
    }
}
