package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.campaigncontext.Npc;
import com.loremind.domain.campaigncontext.ports.NpcRepository;
import com.loremind.infrastructure.persistence.entity.NpcJpaEntity;
import com.loremind.infrastructure.persistence.jpa.NpcJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class PostgresNpcRepository implements NpcRepository {

    private final NpcJpaRepository jpaRepository;

    public PostgresNpcRepository(NpcJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Npc save(Npc npc) {
        NpcJpaEntity entity = toJpaEntity(npc);
        NpcJpaEntity saved = jpaRepository.save(entity);
        return toDomainEntity(saved);
    }

    @Override
    public Optional<Npc> findById(String id) {
        return jpaRepository.findById(Long.parseLong(id)).map(this::toDomainEntity);
    }

    @Override
    public List<Npc> findByCampaignId(String campaignId) {
        return jpaRepository.findByCampaignIdOrderByOrderAsc(Long.parseLong(campaignId)).stream()
                .map(this::toDomainEntity)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(String id) {
        jpaRepository.deleteById(Long.parseLong(id));
    }

    @Override
    public boolean existsById(String id) {
        return jpaRepository.existsById(Long.parseLong(id));
    }

    private Npc toDomainEntity(NpcJpaEntity e) {
        return Npc.builder()
                .id(e.getId().toString())
                .name(e.getName())
                .portraitImageId(e.getPortraitImageId())
                .headerImageId(e.getHeaderImageId())
                .values(e.getValues() != null ? new HashMap<>(e.getValues()) : new HashMap<>())
                .imageValues(e.getImageValues() != null ? new HashMap<>(e.getImageValues()) : new HashMap<>())
                .keyValueValues(e.getKeyValueValues() != null ? new HashMap<>(e.getKeyValueValues()) : new HashMap<>())
                .campaignId(e.getCampaignId().toString())
                .order(e.getOrder())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private NpcJpaEntity toJpaEntity(Npc n) {
        Long id = n.getId() != null ? Long.parseLong(n.getId()) : null;
        return NpcJpaEntity.builder()
                .id(id)
                .name(n.getName())
                .portraitImageId(n.getPortraitImageId())
                .headerImageId(n.getHeaderImageId())
                .values(n.getValues() != null ? new HashMap<>(n.getValues()) : new HashMap<>())
                .imageValues(n.getImageValues() != null ? new HashMap<>(n.getImageValues()) : new HashMap<>())
                .keyValueValues(n.getKeyValueValues() != null ? new HashMap<>(n.getKeyValueValues()) : new HashMap<>())
                .campaignId(Long.parseLong(n.getCampaignId()))
                .order(n.getOrder())
                .createdAt(n.getCreatedAt())
                .updatedAt(n.getUpdatedAt())
                .build();
    }
}
