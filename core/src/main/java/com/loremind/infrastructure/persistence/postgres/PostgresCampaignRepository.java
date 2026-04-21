package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.infrastructure.persistence.entity.CampaignJpaEntity;
import com.loremind.infrastructure.persistence.jpa.CampaignJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adaptateur d'infrastructure qui implémente le Port CampaignRepository.
 */
@Repository
public class PostgresCampaignRepository implements CampaignRepository {

    private final CampaignJpaRepository jpaRepository;

    public PostgresCampaignRepository(CampaignJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Campaign save(Campaign campaign) {
        CampaignJpaEntity jpaEntity = toJpaEntity(campaign);
        CampaignJpaEntity saved = jpaRepository.save(jpaEntity);
        return toDomainEntity(saved);
    }

    @Override
    public Optional<Campaign> findById(String id) {
        Long longId = Long.parseLong(id);
        return jpaRepository.findById(longId)
                .map(this::toDomainEntity);
    }

    @Override
    public List<Campaign> findAll() {
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

    @Override
    public List<Campaign> searchByName(String query) {
        return jpaRepository.findByNameContainingIgnoreCase(query).stream()
                .map(this::toDomainEntity)
                .collect(Collectors.toList());
    }

    private Campaign toDomainEntity(CampaignJpaEntity jpaEntity) {
        return Campaign.builder()
                .id(jpaEntity.getId().toString())
                .name(jpaEntity.getName())
                .description(jpaEntity.getDescription())
                .createdAt(jpaEntity.getCreatedAt())
                .updatedAt(jpaEntity.getUpdatedAt())
                .arcsCount(jpaEntity.getArcsCount())
                .loreId(jpaEntity.getLoreId())
                .build();
    }

    private CampaignJpaEntity toJpaEntity(Campaign campaign) {
        Long id = campaign.getId() != null ? Long.parseLong(campaign.getId()) : null;
        return CampaignJpaEntity.builder()
                .id(id)
                .name(campaign.getName())
                .description(campaign.getDescription())
                .createdAt(campaign.getCreatedAt())
                .updatedAt(campaign.getUpdatedAt())
                .arcsCount(campaign.getArcsCount())
                .loreId(campaign.getLoreId())
                .build();
    }
}
