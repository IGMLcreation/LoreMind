package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.campaigncontext.Arc;
import com.loremind.domain.campaigncontext.ports.ArcRepository;
import com.loremind.infrastructure.persistence.entity.ArcJpaEntity;
import com.loremind.infrastructure.persistence.jpa.ArcJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adaptateur d'infrastructure qui implémente le Port ArcRepository.
 */
@Repository
public class PostgresArcRepository implements ArcRepository {

    private final ArcJpaRepository jpaRepository;

    public PostgresArcRepository(ArcJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Arc save(Arc arc) {
        ArcJpaEntity jpaEntity = toJpaEntity(arc);
        ArcJpaEntity saved = jpaRepository.save(jpaEntity);
        return toDomainEntity(saved);
    }

    @Override
    public Optional<Arc> findById(String id) {
        Long longId = Long.parseLong(id);
        return jpaRepository.findById(longId)
                .map(this::toDomainEntity);
    }

    @Override
    public List<Arc> findByCampaignId(String campaignId) {
        Long longCampaignId = Long.parseLong(campaignId);
        return jpaRepository.findByCampaignId(longCampaignId).stream()
                .map(this::toDomainEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<Arc> findAll() {
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

    private Arc toDomainEntity(ArcJpaEntity jpaEntity) {
        return Arc.builder()
                .id(jpaEntity.getId().toString())
                .name(jpaEntity.getName())
                .description(jpaEntity.getDescription())
                .campaignId(jpaEntity.getCampaignId().toString())
                .order(jpaEntity.getOrder())
                .themes(jpaEntity.getThemes())
                .stakes(jpaEntity.getStakes())
                .gmNotes(jpaEntity.getGmNotes())
                .rewards(jpaEntity.getRewards())
                .resolution(jpaEntity.getResolution())
                // Defensive copy : jamais de null en domaine pour simplifier les appelants.
                .relatedPageIds(jpaEntity.getRelatedPageIds() != null
                        ? new ArrayList<>(jpaEntity.getRelatedPageIds())
                        : new ArrayList<>())
                .illustrationImageIds(jpaEntity.getIllustrationImageIds() != null
                        ? new ArrayList<>(jpaEntity.getIllustrationImageIds())
                        : new ArrayList<>())
                .mapImageIds(jpaEntity.getMapImageIds() != null
                        ? new ArrayList<>(jpaEntity.getMapImageIds())
                        : new ArrayList<>())
                .createdAt(jpaEntity.getCreatedAt())
                .updatedAt(jpaEntity.getUpdatedAt())
                .build();
    }

    private ArcJpaEntity toJpaEntity(Arc arc) {
        Long id = arc.getId() != null ? Long.parseLong(arc.getId()) : null;
        return ArcJpaEntity.builder()
                .id(id)
                .name(arc.getName())
                .description(arc.getDescription())
                .campaignId(Long.parseLong(arc.getCampaignId()))
                .order(arc.getOrder())
                .themes(arc.getThemes())
                .stakes(arc.getStakes())
                .gmNotes(arc.getGmNotes())
                .rewards(arc.getRewards())
                .resolution(arc.getResolution())
                .relatedPageIds(arc.getRelatedPageIds() != null
                        ? new ArrayList<>(arc.getRelatedPageIds())
                        : new ArrayList<>())
                .illustrationImageIds(arc.getIllustrationImageIds() != null
                        ? new ArrayList<>(arc.getIllustrationImageIds())
                        : new ArrayList<>())
                .mapImageIds(arc.getMapImageIds() != null
                        ? new ArrayList<>(arc.getMapImageIds())
                        : new ArrayList<>())
                .createdAt(arc.getCreatedAt())
                .updatedAt(arc.getUpdatedAt())
                .build();
    }
}
