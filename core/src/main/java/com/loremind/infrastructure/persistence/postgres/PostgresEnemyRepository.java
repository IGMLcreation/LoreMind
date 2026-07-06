package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.campaigncontext.bestiary.Enemy;
import com.loremind.domain.campaigncontext.ports.EnemyRepository;
import com.loremind.infrastructure.persistence.entity.EnemyJpaEntity;
import com.loremind.infrastructure.persistence.jpa.EnemyJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

@Repository
public class PostgresEnemyRepository implements EnemyRepository {

    private final EnemyJpaRepository jpaRepository;

    public PostgresEnemyRepository(EnemyJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Enemy save(Enemy enemy) {
        return toDomainEntity(jpaRepository.save(toJpaEntity(enemy)));
    }

    @Override
    public Optional<Enemy> findById(String id) {
        return jpaRepository.findById(Long.parseLong(id)).map(this::toDomainEntity);
    }

    @Override
    public List<Enemy> findByCampaignId(String campaignId) {
        return jpaRepository.findByCampaignIdOrderByOrderAsc(Long.parseLong(campaignId)).stream()
                .map(this::toDomainEntity)
                .toList();
    }

    @Override
    public void deleteById(String id) {
        jpaRepository.deleteById(Long.parseLong(id));
    }

    @Override
    public List<Enemy> searchByName(String query) {
        return jpaRepository.findTop20ByNameContainingIgnoreCaseOrderByNameAsc(query).stream()
                .map(this::toDomainEntity)
                .toList();
    }

    private Enemy toDomainEntity(EnemyJpaEntity e) {
        return Enemy.builder()
                .id(e.getId().toString())
                .name(e.getName())
                .level(e.getLevel())
                .folder(e.getFolder())
                .portraitImageId(e.getPortraitImageId())
                .headerImageId(e.getHeaderImageId())
                .values(e.getValues() != null ? new HashMap<>(e.getValues()) : new HashMap<>())
                .imageValues(e.getImageValues() != null ? new HashMap<>(e.getImageValues()) : new HashMap<>())
                .keyValueValues(e.getKeyValueValues() != null ? new HashMap<>(e.getKeyValueValues()) : new HashMap<>())
                .campaignId(e.getCampaignId().toString())
                .foundryRef(e.getFoundryRef())
                .foundryStats(e.getFoundryStats() != null ? new HashMap<>(e.getFoundryStats()) : new HashMap<>())
                .order(e.getOrder())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private EnemyJpaEntity toJpaEntity(Enemy n) {
        return EnemyJpaEntity.builder()
                .id(n.getId() != null ? Long.parseLong(n.getId()) : null)
                .name(n.getName())
                .level(n.getLevel())
                .folder(n.getFolder())
                .portraitImageId(n.getPortraitImageId())
                .headerImageId(n.getHeaderImageId())
                .values(n.getValues() != null ? new HashMap<>(n.getValues()) : new HashMap<>())
                .imageValues(n.getImageValues() != null ? new HashMap<>(n.getImageValues()) : new HashMap<>())
                .keyValueValues(n.getKeyValueValues() != null ? new HashMap<>(n.getKeyValueValues()) : new HashMap<>())
                .campaignId(Long.parseLong(n.getCampaignId()))
                .foundryRef(n.getFoundryRef())
                .foundryStats(n.getFoundryStats() != null ? new HashMap<>(n.getFoundryStats()) : new HashMap<>())
                .order(n.getOrder())
                .createdAt(n.getCreatedAt())
                .updatedAt(n.getUpdatedAt())
                .build();
    }
}
