package com.loremind.infrastructure.web.mapper;

import com.loremind.domain.campaigncontext.Chapter;
import com.loremind.infrastructure.web.dto.campaigncontext.ChapterDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Mapper Chapter (domaine) ↔ ChapterDTO (REST).
 *
 * <p>Ne touche plus à {@code progressionStatus} ni {@code effectiveStatus} :
 * ces champs sont propres à un Playthrough et injectés par {@code ChapterStatusEnricher}
 * quand le controller a un playthroughId.</p>
 */
@Component
public class ChapterMapper {

    private final PrerequisiteMapper prerequisiteMapper;

    public ChapterMapper(PrerequisiteMapper prerequisiteMapper) {
        this.prerequisiteMapper = prerequisiteMapper;
    }

    public ChapterDTO toDTO(Chapter chapter) {
        if (chapter == null) return null;

        ChapterDTO dto = new ChapterDTO();
        dto.setId(chapter.getId());
        dto.setName(chapter.getName());
        dto.setDescription(chapter.getDescription());
        dto.setArcId(chapter.getArcId());
        dto.setOrder(chapter.getOrder());
        dto.setPrerequisites(prerequisiteMapper.toDTOList(chapter.getPrerequisites()));
        // progressionStatus / effectiveStatus : laissés null. Peuplés par ChapterStatusEnricher.enrich(...)
        // si le client a fourni un playthroughId au controller.
        dto.setIcon(chapter.getIcon());
        dto.setGmNotes(chapter.getGmNotes());
        dto.setPlayerObjectives(chapter.getPlayerObjectives());
        dto.setNarrativeStakes(chapter.getNarrativeStakes());
        dto.setRelatedPageIds(copyList(chapter.getRelatedPageIds()));
        dto.setIllustrationImageIds(copyList(chapter.getIllustrationImageIds()));
        return dto;
    }

    public Chapter toDomain(ChapterDTO dto) {
        if (dto == null) return null;

        return Chapter.builder()
                .id(dto.getId())
                .name(dto.getName())
                .description(dto.getDescription())
                .arcId(dto.getArcId())
                .order(dto.getOrder())
                .prerequisites(prerequisiteMapper.toDomainList(dto.getPrerequisites()))
                .icon(dto.getIcon())
                .gmNotes(dto.getGmNotes())
                .playerObjectives(dto.getPlayerObjectives())
                .narrativeStakes(dto.getNarrativeStakes())
                .relatedPageIds(copyList(dto.getRelatedPageIds()))
                .illustrationImageIds(copyList(dto.getIllustrationImageIds()))
                .build();
    }

    private <T> List<T> copyList(List<T> source) {
        return source != null ? new ArrayList<>(source) : new ArrayList<>();
    }
}
