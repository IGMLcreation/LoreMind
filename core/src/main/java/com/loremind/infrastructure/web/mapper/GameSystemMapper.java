package com.loremind.infrastructure.web.mapper;

import com.loremind.domain.gamesystemcontext.GameSystem;
import com.loremind.domain.shared.template.TemplateField;
import com.loremind.infrastructure.web.dto.gamesystemcontext.GameSystemDTO;
import com.loremind.infrastructure.web.dto.shared.TemplateFieldDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class GameSystemMapper {

    private final TemplateFieldMapper fieldMapper;

    public GameSystemMapper(TemplateFieldMapper fieldMapper) {
        this.fieldMapper = fieldMapper;
    }

    public GameSystemDTO toDTO(GameSystem g) {
        if (g == null) return null;
        GameSystemDTO dto = new GameSystemDTO();
        dto.setId(g.getId());
        dto.setName(g.getName());
        dto.setDescription(g.getDescription());
        dto.setRulesMarkdown(g.getRulesMarkdown());
        dto.setCharacterTemplate(toDTOList(g.getCharacterTemplate()));
        dto.setNpcTemplate(toDTOList(g.getNpcTemplate()));
        dto.setAuthor(g.getAuthor());
        dto.setPublic(g.isPublic());
        return dto;
    }

    public GameSystem toDomain(GameSystemDTO dto) {
        if (dto == null) return null;
        return GameSystem.builder()
                .id(dto.getId())
                .name(dto.getName())
                .description(dto.getDescription())
                .rulesMarkdown(dto.getRulesMarkdown())
                .characterTemplate(toDomainList(dto.getCharacterTemplate()))
                .npcTemplate(toDomainList(dto.getNpcTemplate()))
                .author(dto.getAuthor())
                .isPublic(dto.isPublic())
                .build();
    }

    private List<TemplateFieldDTO> toDTOList(List<TemplateField> fields) {
        if (fields == null) return new ArrayList<>();
        List<TemplateFieldDTO> out = new ArrayList<>(fields.size());
        for (TemplateField f : fields) out.add(fieldMapper.toDTO(f));
        return out;
    }

    private List<TemplateField> toDomainList(List<TemplateFieldDTO> dtos) {
        if (dtos == null) return new ArrayList<>();
        List<TemplateField> out = new ArrayList<>(dtos.size());
        for (TemplateFieldDTO d : dtos) out.add(fieldMapper.toDomain(d));
        return out;
    }
}
