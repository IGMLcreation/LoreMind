package com.loremind.infrastructure.web.mapper;

import com.loremind.domain.gamesystemcontext.GameSystem;
import com.loremind.infrastructure.web.dto.gamesystemcontext.GameSystemDTO;
import org.springframework.stereotype.Component;

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
        dto.setCharacterTemplate(fieldMapper.toDTOList(g.getCharacterTemplate()));
        dto.setNpcTemplate(fieldMapper.toDTOList(g.getNpcTemplate()));
        dto.setEnemyTemplate(fieldMapper.toDTOList(g.getEnemyTemplate()));
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
                .characterTemplate(fieldMapper.toDomainList(dto.getCharacterTemplate()))
                .npcTemplate(fieldMapper.toDomainList(dto.getNpcTemplate()))
                .enemyTemplate(fieldMapper.toDomainList(dto.getEnemyTemplate()))
                .author(dto.getAuthor())
                .isPublic(dto.isPublic())
                .build();
    }
}
