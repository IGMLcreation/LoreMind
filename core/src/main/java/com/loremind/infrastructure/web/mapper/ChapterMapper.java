package com.loremind.infrastructure.web.mapper;

import com.loremind.domain.campaigncontext.Chapter;
import com.loremind.infrastructure.web.dto.campaigncontext.ChapterDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Mapper pour convertir entre Chapter (entité de domaine) et ChapterDTO.
 */
@Component
public class ChapterMapper {

    public ChapterDTO toDTO(Chapter chapter) {
        if (chapter == null) {
            return null;
        }

        ChapterDTO dto = new ChapterDTO();
        dto.setId(chapter.getId());
        dto.setName(chapter.getName());
        dto.setDescription(chapter.getDescription());
        dto.setArcId(chapter.getArcId());
        dto.setOrder(chapter.getOrder());
        dto.setGmNotes(chapter.getGmNotes());
        dto.setPlayerObjectives(chapter.getPlayerObjectives());
        dto.setNarrativeStakes(chapter.getNarrativeStakes());
        dto.setRelatedPageIds(copyList(chapter.getRelatedPageIds()));
        dto.setIllustrationImageIds(copyList(chapter.getIllustrationImageIds()));
        return dto;
    }

    public Chapter toDomain(ChapterDTO dto) {
        if (dto == null) {
            return null;
        }

        return Chapter.builder()
                .id(dto.getId())
                .name(dto.getName())
                .description(dto.getDescription())
                .arcId(dto.getArcId())
                .order(dto.getOrder())
                .gmNotes(dto.getGmNotes())
                .playerObjectives(dto.getPlayerObjectives())
                .narrativeStakes(dto.getNarrativeStakes())
                .relatedPageIds(copyList(dto.getRelatedPageIds()))
                .illustrationImageIds(copyList(dto.getIllustrationImageIds()))
                .build();
    }

    /**
     * Copie sécurisée d'une liste (gère le cas null).
     * Ceci est une méthode utilitaire privée pour éviter la duplication de code.
     */
    private <T> List<T> copyList(List<T> source) {
        return source != null ? new ArrayList<>(source) : new ArrayList<>();
    }
}
