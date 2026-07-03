package com.loremind.infrastructure.web.mapper;

import com.loremind.domain.campaigncontext.Chapter;
import com.loremind.infrastructure.web.dto.campaigncontext.ChapterDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Mapper Chapter (domaine) ↔ ChapterDTO (REST).
 *
 * <p>Depuis le Niveau 1, le Chapitre est une donnée de SCÉNARIO pure : plus de
 * prérequis ni de statut de progression (le gating vit sur les Quêtes).</p>
 */
@Component
public class ChapterMapper {

    public ChapterDTO toDTO(Chapter chapter) {
        if (chapter == null) return null;

        ChapterDTO dto = new ChapterDTO();
        dto.setId(chapter.getId());
        dto.setName(chapter.getName());
        dto.setDescription(chapter.getDescription());
        dto.setArcId(chapter.getArcId());
        dto.setOrder(chapter.getOrder());
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
