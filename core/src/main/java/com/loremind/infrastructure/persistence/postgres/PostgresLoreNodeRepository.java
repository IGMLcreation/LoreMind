package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.lorecontext.LoreNode;
import com.loremind.domain.lorecontext.ports.LoreNodeRepository;
import com.loremind.infrastructure.persistence.entity.LoreNodeJpaEntity;
import com.loremind.infrastructure.persistence.jpa.LoreNodeJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adaptateur d'infrastructure qui implémente le Port LoreNodeRepository.
 */
@Repository
public class PostgresLoreNodeRepository implements LoreNodeRepository {

    private final LoreNodeJpaRepository jpaRepository;

    public PostgresLoreNodeRepository(LoreNodeJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public LoreNode save(LoreNode loreNode) {
        LoreNodeJpaEntity jpaEntity = toJpaEntity(loreNode);
        LoreNodeJpaEntity saved = jpaRepository.save(jpaEntity);
        return toDomainEntity(saved);
    }

    @Override
    public Optional<LoreNode> findById(String id) {
        Long longId = Long.parseLong(id);
        return jpaRepository.findById(longId)
                .map(this::toDomainEntity);
    }

    @Override
    public List<LoreNode> findByLoreId(String loreId) {
        Long longLoreId = Long.parseLong(loreId);
        return jpaRepository.findByLoreId(longLoreId).stream()
                .map(this::toDomainEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<LoreNode> findByParentId(String parentId) {
        if (parentId == null || parentId.isEmpty()) {
            return jpaRepository.findByParentId(null).stream()
                    .map(this::toDomainEntity)
                    .collect(Collectors.toList());
        }
        Long longParentId = Long.parseLong(parentId);
        return jpaRepository.findByParentId(longParentId).stream()
                .map(this::toDomainEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<LoreNode> findAll() {
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
    public long countByLoreId(String loreId) {
        return jpaRepository.countByLoreId(Long.parseLong(loreId));
    }

    @Override
    public List<LoreNode> searchByName(String query) {
        return jpaRepository.findByNameContainingIgnoreCase(query).stream()
                .map(this::toDomainEntity)
                .collect(Collectors.toList());
    }

    private LoreNode toDomainEntity(LoreNodeJpaEntity jpaEntity) {
        return LoreNode.builder()
                .id(jpaEntity.getId().toString())
                .name(jpaEntity.getName())
                .icon(jpaEntity.getIcon())
                .parentId(jpaEntity.getParentId() != null ? jpaEntity.getParentId().toString() : null)
                .loreId(jpaEntity.getLoreId().toString())
                .createdAt(jpaEntity.getCreatedAt())
                .updatedAt(jpaEntity.getUpdatedAt())
                .build();
    }

    private LoreNodeJpaEntity toJpaEntity(LoreNode loreNode) {
        Long id = loreNode.getId() != null ? Long.parseLong(loreNode.getId()) : null;
        Long parentId = loreNode.getParentId() != null && !loreNode.getParentId().isEmpty()
                ? Long.parseLong(loreNode.getParentId())
                : null;
        return LoreNodeJpaEntity.builder()
                .id(id)
                .name(loreNode.getName())
                .icon(loreNode.getIcon())
                .parentId(parentId)
                .loreId(Long.parseLong(loreNode.getLoreId()))
                .createdAt(loreNode.getCreatedAt())
                .updatedAt(loreNode.getUpdatedAt())
                .build();
    }
}
