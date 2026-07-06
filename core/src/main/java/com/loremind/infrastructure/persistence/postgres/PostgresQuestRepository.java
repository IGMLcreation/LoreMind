package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.campaigncontext.quest.Quest;
import com.loremind.domain.campaigncontext.ports.QuestRepository;
import com.loremind.infrastructure.persistence.entity.QuestJpaEntity;
import com.loremind.infrastructure.persistence.jpa.QuestJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Adaptateur d'infrastructure qui implémente le Port QuestRepository.
 * Calqué sur {@code PostgresChapterRepository}.
 */
@Repository
public class PostgresQuestRepository implements QuestRepository {

    private final QuestJpaRepository jpaRepository;

    public PostgresQuestRepository(QuestJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Quest save(Quest quest) {
        QuestJpaEntity saved = jpaRepository.save(toJpaEntity(quest));
        return toDomainEntity(saved);
    }

    @Override
    public Optional<Quest> findById(String id) {
        return jpaRepository.findById(Long.parseLong(id))
                .map(this::toDomainEntity);
    }

    @Override
    public List<Quest> findByCampaignId(String campaignId) {
        return jpaRepository.findByCampaignId(Long.parseLong(campaignId)).stream()
                .map(this::toDomainEntity)
                .toList();
    }

    @Override
    public List<Quest> findByArcId(String arcId) {
        if (arcId == null || arcId.isBlank()) return List.of(); // pas d'arc → aucune quête rattachée
        return jpaRepository.findByArcId(Long.parseLong(arcId)).stream()
                .map(this::toDomainEntity)
                .toList();
    }

    @Override
    public List<Quest> findAll() {
        return jpaRepository.findAll().stream()
                .map(this::toDomainEntity)
                .toList();
    }

    @Override
    public void deleteById(String id) {
        jpaRepository.deleteById(Long.parseLong(id));
    }

    @Override
    public boolean existsById(String id) {
        return jpaRepository.existsById(Long.parseLong(id));
    }

    private Quest toDomainEntity(QuestJpaEntity e) {
        return Quest.builder()
                .id(e.getId().toString())
                .campaignId(e.getCampaignId().toString())
                .arcId(e.getArcId() != null ? e.getArcId().toString() : null)
                .name(e.getName())
                .description(e.getDescription())
                .icon(e.getIcon())
                .order(e.getOrder())
                .prerequisites(e.getPrerequisites() != null
                        ? new ArrayList<>(e.getPrerequisites())
                        : new ArrayList<>())
                .nodes(e.getNodes() != null
                        ? new ArrayList<>(e.getNodes())
                        : new ArrayList<>())
                .gmNotes(e.getGmNotes())
                .playerObjectives(e.getPlayerObjectives())
                .narrativeStakes(e.getNarrativeStakes())
                .relatedPageIds(e.getRelatedPageIds() != null
                        ? new ArrayList<>(e.getRelatedPageIds())
                        : new ArrayList<>())
                .illustrationImageIds(e.getIllustrationImageIds() != null
                        ? new ArrayList<>(e.getIllustrationImageIds())
                        : new ArrayList<>())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private QuestJpaEntity toJpaEntity(Quest q) {
        Long id = q.getId() != null ? Long.parseLong(q.getId()) : null;
        return QuestJpaEntity.builder()
                .id(id)
                .campaignId(Long.parseLong(q.getCampaignId()))
                .arcId(q.getArcId() != null && !q.getArcId().isBlank() ? Long.parseLong(q.getArcId()) : null)
                .name(q.getName())
                .description(q.getDescription())
                .icon(q.getIcon())
                .order(q.getOrder())
                .prerequisites(q.getPrerequisites() != null
                        ? new ArrayList<>(q.getPrerequisites())
                        : new ArrayList<>())
                .nodes(q.getNodes() != null
                        ? new ArrayList<>(q.getNodes())
                        : new ArrayList<>())
                .gmNotes(q.getGmNotes())
                .playerObjectives(q.getPlayerObjectives())
                .narrativeStakes(q.getNarrativeStakes())
                .relatedPageIds(q.getRelatedPageIds() != null
                        ? new ArrayList<>(q.getRelatedPageIds())
                        : new ArrayList<>())
                .illustrationImageIds(q.getIllustrationImageIds() != null
                        ? new ArrayList<>(q.getIllustrationImageIds())
                        : new ArrayList<>())
                .createdAt(q.getCreatedAt())
                .updatedAt(q.getUpdatedAt())
                .build();
    }
}
