package com.loremind.infrastructure.web.mapper;

import com.loremind.domain.lorecontext.FieldType;
import com.loremind.domain.lorecontext.TemplateField;
import com.loremind.infrastructure.web.dto.lorecontext.TemplateFieldDTO;
import org.springframework.stereotype.Component;

/**
 * Mapper pour convertir entre {@link TemplateField} (domaine) et
 * {@link TemplateFieldDTO} (wire).
 * <p>
 * Tolerance : un type inconnu recu du client est interprete comme TEXT
 * (plus safe que de rejeter la requete et d'interrompre la sauvegarde).
 */
@Component
public class TemplateFieldMapper {

    public TemplateFieldDTO toDTO(TemplateField field) {
        if (field == null) return null;
        String typeStr = field.getType() != null ? field.getType().name() : FieldType.TEXT.name();
        return new TemplateFieldDTO(field.getName(), typeStr);
    }

    public TemplateField toDomain(TemplateFieldDTO dto) {
        if (dto == null) return null;
        FieldType type;
        try {
            type = dto.getType() != null ? FieldType.valueOf(dto.getType()) : FieldType.TEXT;
        } catch (IllegalArgumentException ex) {
            type = FieldType.TEXT;
        }
        return new TemplateField(dto.getName(), type);
    }
}
