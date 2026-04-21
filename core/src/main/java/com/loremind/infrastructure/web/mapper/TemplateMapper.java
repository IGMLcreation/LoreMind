package com.loremind.infrastructure.web.mapper;

import com.loremind.domain.lorecontext.Template;
import com.loremind.domain.lorecontext.TemplateField;
import com.loremind.infrastructure.web.dto.lorecontext.TemplateDTO;
import com.loremind.infrastructure.web.dto.lorecontext.TemplateFieldDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Mapper pour convertir entre Template (entité de domaine) et TemplateDTO.
 * Delegue la conversion de chaque champ a {@link TemplateFieldMapper}.
 */
@Component
public class TemplateMapper {

    private final TemplateFieldMapper fieldMapper;

    public TemplateMapper(TemplateFieldMapper fieldMapper) {
        this.fieldMapper = fieldMapper;
    }

    public TemplateDTO toDTO(Template template) {
        if (template == null) {
            return null;
        }
        TemplateDTO dto = new TemplateDTO();
        dto.setId(template.getId());
        dto.setLoreId(template.getLoreId());
        dto.setName(template.getName());
        dto.setDescription(template.getDescription());
        dto.setDefaultNodeId(template.getDefaultNodeId());
        dto.setFields(mapFieldsToDto(template.getFields()));
        dto.setFieldCount(template.fieldCount());
        return dto;
    }

    public Template toDomain(TemplateDTO dto) {
        if (dto == null) {
            return null;
        }
        return Template.builder()
                .id(dto.getId())
                .loreId(dto.getLoreId())
                .name(dto.getName())
                .description(dto.getDescription())
                .defaultNodeId(dto.getDefaultNodeId())
                .fields(mapFieldsToDomain(dto.getFields()))
                .build();
    }

    private List<TemplateFieldDTO> mapFieldsToDto(List<TemplateField> fields) {
        if (fields == null) return new ArrayList<>();
        List<TemplateFieldDTO> result = new ArrayList<>(fields.size());
        for (TemplateField f : fields) result.add(fieldMapper.toDTO(f));
        return result;
    }

    private List<TemplateField> mapFieldsToDomain(List<TemplateFieldDTO> dtos) {
        if (dtos == null) return new ArrayList<>();
        List<TemplateField> result = new ArrayList<>(dtos.size());
        for (TemplateFieldDTO d : dtos) result.add(fieldMapper.toDomain(d));
        return result;
    }
}
