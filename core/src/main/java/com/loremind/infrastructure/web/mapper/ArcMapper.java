package com.loremind.infrastructure.web.mapper;

import com.loremind.domain.campaigncontext.Arc;
import com.loremind.infrastructure.web.dto.campaigncontext.ArcDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Mapper pour convertir entre Arc (entité de domaine) et ArcDTO.
 */
@Component
public class ArcMapper {

    public ArcDTO toDTO(Arc arc) {
        if (arc == null) {
            return null;
        }

        ArcDTO dto = new ArcDTO();
        dto.setId(arc.getId());
        dto.setName(arc.getName());
        dto.setDescription(arc.getDescription());
        dto.setCampaignId(arc.getCampaignId());
        dto.setOrder(arc.getOrder());
        dto.setThemes(arc.getThemes());
        dto.setStakes(arc.getStakes());
        dto.setGmNotes(arc.getGmNotes());
        dto.setRewards(arc.getRewards());
        dto.setResolution(arc.getResolution());
        dto.setRelatedPageIds(copyList(arc.getRelatedPageIds()));
        dto.setIllustrationImageIds(copyList(arc.getIllustrationImageIds()));
        dto.setMapImageIds(copyList(arc.getMapImageIds()));
        return dto;
    }

    public Arc toDomain(ArcDTO dto) {
        if (dto == null) {
            return null;
        }

        return Arc.builder()
                .id(dto.getId())
                .name(dto.getName())
                .description(dto.getDescription())
                .campaignId(dto.getCampaignId())
                .order(dto.getOrder())
                .themes(dto.getThemes())
                .stakes(dto.getStakes())
                .gmNotes(dto.getGmNotes())
                .rewards(dto.getRewards())
                .resolution(dto.getResolution())
                .relatedPageIds(copyList(dto.getRelatedPageIds()))
                .illustrationImageIds(copyList(dto.getIllustrationImageIds()))
                .mapImageIds(copyList(dto.getMapImageIds()))
                .build();
    }

    /**
     * Copie sécurisée d'une liste (gère le cas null).
     * Ceci est une méthode utilitaire privée pour éviter la duplication de code.
     */
    private <T> ArrayList<T> copyList(List<T> source) {
        return source != null ? new ArrayList<>(source) : new ArrayList<>();
    }
}
