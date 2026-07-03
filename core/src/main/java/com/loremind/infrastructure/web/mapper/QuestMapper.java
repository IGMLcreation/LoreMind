package com.loremind.infrastructure.web.mapper;

import com.loremind.domain.campaigncontext.NodeType;
import com.loremind.domain.campaigncontext.Quest;
import com.loremind.domain.campaigncontext.QuestNodeRef;
import com.loremind.infrastructure.web.dto.campaigncontext.QuestDTO;
import com.loremind.infrastructure.web.dto.campaigncontext.QuestNodeRefDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Mapper Quest (domaine) ↔ QuestDTO (REST). Calqué sur {@code ChapterMapper} :
 * délègue les prérequis à {@code PrerequisiteMapper} (RÉUTILISÉ) et mappe les
 * nœuds. Laisse {@code progressionStatus} / {@code effectiveStatus} à null
 * (peuplés par {@code QuestStatusEnricher} si un playthroughId est fourni).
 */
@Component
public class QuestMapper {

    private final PrerequisiteMapper prerequisiteMapper;

    public QuestMapper(PrerequisiteMapper prerequisiteMapper) {
        this.prerequisiteMapper = prerequisiteMapper;
    }

    public QuestDTO toDTO(Quest quest) {
        if (quest == null) return null;

        QuestDTO dto = new QuestDTO();
        dto.setId(quest.getId());
        dto.setCampaignId(quest.getCampaignId());
        dto.setArcId(quest.getArcId());
        dto.setName(quest.getName());
        dto.setDescription(quest.getDescription());
        dto.setIcon(quest.getIcon());
        dto.setOrder(quest.getOrder());
        dto.setPrerequisites(prerequisiteMapper.toDTOList(quest.getPrerequisites()));
        dto.setNodes(toNodeDTOList(quest.getNodes()));
        dto.setGmNotes(quest.getGmNotes());
        dto.setPlayerObjectives(quest.getPlayerObjectives());
        dto.setNarrativeStakes(quest.getNarrativeStakes());
        dto.setRelatedPageIds(copyList(quest.getRelatedPageIds()));
        dto.setIllustrationImageIds(copyList(quest.getIllustrationImageIds()));
        return dto;
    }

    public Quest toDomain(QuestDTO dto) {
        if (dto == null) return null;

        return Quest.builder()
                .id(dto.getId())
                .campaignId(dto.getCampaignId())
                .arcId(dto.getArcId())
                .name(dto.getName())
                .description(dto.getDescription())
                .icon(dto.getIcon())
                .order(dto.getOrder())
                .prerequisites(prerequisiteMapper.toDomainList(dto.getPrerequisites()))
                .nodes(toNodeDomainList(dto.getNodes()))
                .gmNotes(dto.getGmNotes())
                .playerObjectives(dto.getPlayerObjectives())
                .narrativeStakes(dto.getNarrativeStakes())
                .relatedPageIds(copyList(dto.getRelatedPageIds()))
                .illustrationImageIds(copyList(dto.getIllustrationImageIds()))
                .build();
    }

    private List<QuestNodeRefDTO> toNodeDTOList(List<QuestNodeRef> list) {
        if (list == null) return new ArrayList<>();
        List<QuestNodeRefDTO> out = new ArrayList<>(list.size());
        for (QuestNodeRef n : list) {
            out.add(new QuestNodeRefDTO(n.nodeType().name(), n.nodeId(), n.order()));
        }
        return out;
    }

    private List<QuestNodeRef> toNodeDomainList(List<QuestNodeRefDTO> list) {
        if (list == null) return new ArrayList<>();
        List<QuestNodeRef> out = new ArrayList<>(list.size());
        for (QuestNodeRefDTO d : list) {
            if (d == null || d.getNodeType() == null) continue;
            out.add(new QuestNodeRef(NodeType.valueOf(d.getNodeType()), d.getNodeId(), d.getOrder()));
        }
        return out;
    }

    private <T> List<T> copyList(List<T> source) {
        return source != null ? new ArrayList<>(source) : new ArrayList<>();
    }
}
