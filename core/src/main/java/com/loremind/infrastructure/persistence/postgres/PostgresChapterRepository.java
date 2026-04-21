package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.campaigncontext.Chapter;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
import com.loremind.infrastructure.persistence.entity.ChapterJpaEntity;
import com.loremind.infrastructure.persistence.jpa.ChapterJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adaptateur d'infrastructure qui implémente le Port ChapterRepository.
 */
@Repository
public class PostgresChapterRepository implements ChapterRepository {

    private final ChapterJpaRepository jpaRepository;

    public PostgresChapterRepository(ChapterJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Chapter save(Chapter chapter) {
        ChapterJpaEntity jpaEntity = toJpaEntity(chapter);
        ChapterJpaEntity saved = jpaRepository.save(jpaEntity);
        return toDomainEntity(saved);
    }

    @Override
    public Optional<Chapter> findById(String id) {
        Long longId = Long.parseLong(id);
        return jpaRepository.findById(longId)
                .map(this::toDomainEntity);
    }

    @Override
    public List<Chapter> findByArcId(String arcId) {
        Long longArcId = Long.parseLong(arcId);
        return jpaRepository.findByArcId(longArcId).stream()
                .map(this::toDomainEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<Chapter> findAll() {
        return jpaRepository.findAll().stream()
                .map(this::toDomainEntity)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(String id) {
        Long longId = Long.parseLong(id);
        jpaRepository.deleteById(longId);
    }

    @Override
    public boolean existsById(String id) {
        Long longId = Long.parseLong(id);
        return jpaRepository.existsById(longId);
    }

    private Chapter toDomainEntity(ChapterJpaEntity jpaEntity) {
        return Chapter.builder()
                .id(jpaEntity.getId().toString())
                .name(jpaEntity.getName())
                .description(jpaEntity.getDescription())
                .arcId(jpaEntity.getArcId().toString())
                .order(jpaEntity.getOrder())
                .gmNotes(jpaEntity.getGmNotes())
                .playerObjectives(jpaEntity.getPlayerObjectives())
                .narrativeStakes(jpaEntity.getNarrativeStakes())
                .relatedPageIds(jpaEntity.getRelatedPageIds() != null
                        ? new ArrayList<>(jpaEntity.getRelatedPageIds())
                        : new ArrayList<>())
                .illustrationImageIds(jpaEntity.getIllustrationImageIds() != null
                        ? new ArrayList<>(jpaEntity.getIllustrationImageIds())
                        : new ArrayList<>())
                .createdAt(jpaEntity.getCreatedAt())
                .updatedAt(jpaEntity.getUpdatedAt())
                .build();
    }

    private ChapterJpaEntity toJpaEntity(Chapter chapter) {
        Long id = chapter.getId() != null ? Long.parseLong(chapter.getId()) : null;
        return ChapterJpaEntity.builder()
                .id(id)
                .name(chapter.getName())
                .description(chapter.getDescription())
                .arcId(Long.parseLong(chapter.getArcId()))
                .order(chapter.getOrder())
                .gmNotes(chapter.getGmNotes())
                .playerObjectives(chapter.getPlayerObjectives())
                .narrativeStakes(chapter.getNarrativeStakes())
                .relatedPageIds(chapter.getRelatedPageIds() != null
                        ? new ArrayList<>(chapter.getRelatedPageIds())
                        : new ArrayList<>())
                .illustrationImageIds(chapter.getIllustrationImageIds() != null
                        ? new ArrayList<>(chapter.getIllustrationImageIds())
                        : new ArrayList<>())
                .createdAt(chapter.getCreatedAt())
                .updatedAt(chapter.getUpdatedAt())
                .build();
    }
}
