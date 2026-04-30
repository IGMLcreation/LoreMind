package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.campaigncontext.Character;
import com.loremind.domain.campaigncontext.ports.CharacterRepository;
import com.loremind.infrastructure.persistence.entity.CharacterJpaEntity;
import com.loremind.infrastructure.persistence.jpa.CharacterJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class PostgresCharacterRepository implements CharacterRepository {

    private final CharacterJpaRepository jpaRepository;

    public PostgresCharacterRepository(CharacterJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Character save(Character character) {
        CharacterJpaEntity entity = toJpaEntity(character);
        CharacterJpaEntity saved = jpaRepository.save(entity);
        return toDomainEntity(saved);
    }

    @Override
    public Optional<Character> findById(String id) {
        return jpaRepository.findById(Long.parseLong(id)).map(this::toDomainEntity);
    }

    @Override
    public List<Character> findByCampaignId(String campaignId) {
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

    private Character toDomainEntity(CharacterJpaEntity e) {
        return Character.builder()
                .id(e.getId().toString())
                .name(e.getName())
                .portraitImageId(e.getPortraitImageId())
                .headerImageId(e.getHeaderImageId())
                .values(e.getValues() != null ? new HashMap<>(e.getValues()) : new HashMap<>())
                .imageValues(e.getImageValues() != null ? new HashMap<>(e.getImageValues()) : new HashMap<>())
                .campaignId(e.getCampaignId().toString())
                .order(e.getOrder())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private CharacterJpaEntity toJpaEntity(Character c) {
        Long id = c.getId() != null ? Long.parseLong(c.getId()) : null;
        return CharacterJpaEntity.builder()
                .id(id)
                .name(c.getName())
                .portraitImageId(c.getPortraitImageId())
                .headerImageId(c.getHeaderImageId())
                .values(c.getValues() != null ? new HashMap<>(c.getValues()) : new HashMap<>())
                .imageValues(c.getImageValues() != null ? new HashMap<>(c.getImageValues()) : new HashMap<>())
                .campaignId(Long.parseLong(c.getCampaignId()))
                .order(c.getOrder())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
