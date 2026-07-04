package com.loremind.infrastructure.transfer;

import com.loremind.domain.campaigncontext.NodeType;
import com.loremind.domain.campaigncontext.Prerequisite;
import com.loremind.domain.campaigncontext.QuestNodeRef;
import com.loremind.infrastructure.persistence.converter.PrerequisiteListJsonConverter;
import com.loremind.infrastructure.persistence.entity.QuestJpaEntity;
import com.loremind.infrastructure.persistence.jpa.QuestJpaRepository;
import com.loremind.infrastructure.transfer.dto.ContentExport;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Conversion legacy d'un import (cf. {@link ImportService}) : pour un bundle SANS champ
 * {@code quests}, recrée des Quests à partir des chapitres qui jouaient le rôle de quête
 * (arc HUB, OU porteurs de prérequis, OU référencés par une {@code quest_progression}).
 * Permet de ne pas perdre les quêtes d'un vieux backup.
 */
@Component
class LegacyQuestConverter {

    // (Dé)sérialise les prérequis dans le format "kind" du converter JPA (Prerequisite
    // est scellé, non sérialisable en polymorphe par l'ObjectMapper standard).
    private static final PrerequisiteListJsonConverter PREREQ_CONVERTER = new PrerequisiteListJsonConverter();

    private final QuestJpaRepository questRepo;

    LegacyQuestConverter(QuestJpaRepository questRepo) {
        this.questRepo = questRepo;
    }

    /**
     * Alimente {@code maps.questMap} (clé = ANCIEN chapter id du bundle -> nouvel id de
     * quête). Les prérequis / nœuds / relatedPageIds sont remappés en 2e passe, comme
     * pour les quêtes v2.
     */
    void convertLegacyChaptersToQuests(ContentExport export, ImportIdMaps maps) {
        Map<Long, ContentExport.ArcDto> arcById = new HashMap<>();
        for (ContentExport.ArcDto a : nullSafe(export.arcs())) arcById.put(a.id(), a);

        Set<Long> progressedChapterIds = new HashSet<>();
        for (ContentExport.QuestProgressionDto qp : nullSafe(export.questProgressions())) {
            if (qp.chapterId() != null) progressedChapterIds.add(qp.chapterId());
        }

        for (ContentExport.ChapterDto d : nullSafe(export.chapters())) {
            ContentExport.ArcDto arc = arcById.get(d.arcId());
            boolean isHub = arc != null && "HUB".equals(arc.type());
            List<Prerequisite> prereqs = PREREQ_CONVERTER.convertToEntityAttribute(d.prerequisitesJson());
            boolean hasPrereqs = prereqs != null && !prereqs.isEmpty();
            boolean inProgression = progressedChapterIds.contains(d.id());
            if (!isHub && !hasPrereqs && !inProgression) continue;

            QuestJpaEntity q = new QuestJpaEntity();
            q.setCampaignId(IdRemapper.remapId(maps.campaignMap, arc != null ? arc.campaignId() : null));
            // Quête legacy issue d'un arc HUB → rattachée à cet arc (préserve la structure HUB) ;
            // les quêtes issues de prérequis/progression seules restent transverses.
            q.setArcId(isHub ? IdRemapper.remapId(maps.arcMap, d.arcId()) : null);
            q.setName(d.name());
            q.setDescription(d.description());
            q.setIcon(d.icon());
            q.setOrder(d.order());
            q.setPrerequisites(prereqs);                                                  // remappé 2e passe (questMap)
            q.setNodes(new ArrayList<>(List.of(
                    new QuestNodeRef(NodeType.CHAPTER, String.valueOf(d.id()), 0))));      // remappé 2e passe (chapterMap)
            q.setGmNotes(d.gmNotes());
            q.setPlayerObjectives(d.playerObjectives());
            q.setNarrativeStakes(d.narrativeStakes());
            q.setRelatedPageIds(d.relatedPageIds());                                       // remappé 2e passe (pageMap)
            // illustrationImageIds : les chapitres legacy les conservent de leur côté.
            maps.questMap.put(d.id(), questRepo.save(q).getId());
        }
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list != null ? list : List.of();
    }
}
