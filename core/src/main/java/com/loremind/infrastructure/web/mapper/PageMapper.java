package com.loremind.infrastructure.web.mapper;

import com.loremind.domain.lorecontext.Page;
import com.loremind.domain.shared.CollectionUtils;
import com.loremind.infrastructure.web.dto.lorecontext.PageDTO;
import org.springframework.stereotype.Component;

/**
 * Mapper pour convertir entre Page (entité de domaine) et PageDTO.
 */
@Component
public class PageMapper {

    public PageDTO toDTO(Page page) {
        if (page == null) {
            return null;
        }
        PageDTO dto = new PageDTO();
        dto.setId(page.getId());
        dto.setLoreId(page.getLoreId());
        dto.setNodeId(page.getNodeId());
        dto.setTemplateId(page.getTemplateId());
        dto.setTitle(page.getTitle());
        dto.setValues(CollectionUtils.copyMap(page.getValues()));
        dto.setImageValues(CollectionUtils.copyMap(page.getImageValues()));
        dto.setNotes(page.getNotes());
        dto.setTags(CollectionUtils.copyList(page.getTags()));
        dto.setRelatedPageIds(CollectionUtils.copyList(page.getRelatedPageIds()));
        return dto;
    }

    public Page toDomain(PageDTO dto) {
        if (dto == null) {
            return null;
        }
        return Page.builder()
                .id(dto.getId())
                .loreId(dto.getLoreId())
                .nodeId(dto.getNodeId())
                .templateId(dto.getTemplateId())
                .title(dto.getTitle())
                .values(CollectionUtils.copyMap(dto.getValues()))
                .imageValues(CollectionUtils.copyMap(dto.getImageValues()))
                .notes(dto.getNotes())
                .tags(CollectionUtils.copyList(dto.getTags()))
                .relatedPageIds(CollectionUtils.copyList(dto.getRelatedPageIds()))
                .build();
    }
}
