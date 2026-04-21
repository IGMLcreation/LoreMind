package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.lorecontext.Lore;
import com.loremind.domain.lorecontext.ports.LoreRepository;
import com.loremind.infrastructure.persistence.entity.LoreJpaEntity;
import com.loremind.infrastructure.persistence.jpa.LoreJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adaptateur d'infrastructure qui implémente le Port LoreRepository.
 * Utilise LoreJpaRepository pour interagir avec PostgreSQL.
 * Fait la conversion entre Lore (domaine) et LoreJpaEntity (JPA).
 */
@Repository
public class PostgresLoreRepository implements LoreRepository {

    private final LoreJpaRepository jpaRepository;

    public PostgresLoreRepository(LoreJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Lore save(Lore lore) {
        LoreJpaEntity jpaEntity = toJpaEntity(lore);
        LoreJpaEntity saved = jpaRepository.save(jpaEntity);
        return toDomainEntity(saved);
    }

    @Override
    public Optional<Lore> findById(String id) {
        Long longId = Long.parseLong(id);
        return jpaRepository.findById(longId)
                .map(this::toDomainEntity);
    }

    @Override
    public List<Lore> findAll() {
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
    public List<Lore> searchByName(String query) {
        return jpaRepository.findByNameContainingIgnoreCase(query).stream()
                .map(this::toDomainEntity)
                .collect(Collectors.toList());
    }

    // Méthodes de conversion
    private Lore toDomainEntity(LoreJpaEntity jpaEntity) {
        return Lore.builder()
                .id(jpaEntity.getId().toString())
                .name(jpaEntity.getName())
                .description(jpaEntity.getDescription())
                .createdAt(jpaEntity.getCreatedAt())
                .updatedAt(jpaEntity.getUpdatedAt())
                .nodeCount(jpaEntity.getNodeCount())
                .pageCount(jpaEntity.getPageCount())
                .build();
    }

    private LoreJpaEntity toJpaEntity(Lore lore) {
        Long id = lore.getId() != null ? Long.parseLong(lore.getId()) : null;
        return LoreJpaEntity.builder()
                .id(id)
                .name(lore.getName())
                .description(lore.getDescription())
                .createdAt(lore.getCreatedAt())
                .updatedAt(lore.getUpdatedAt())
                .nodeCount(lore.getNodeCount())
                .pageCount(lore.getPageCount())
                .build();
    }
}
