package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.lorecontext.Page;
import com.loremind.domain.lorecontext.ports.PageRepository;
import com.loremind.infrastructure.persistence.entity.PageJpaEntity;
import com.loremind.infrastructure.persistence.jpa.PageJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adaptateur d'infrastructure qui implémente le Port PageRepository.
 */
@Repository
public class PostgresPageRepository implements PageRepository {

    private final PageJpaRepository jpaRepository;

    public PostgresPageRepository(PageJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Page save(Page page) {
        PageJpaEntity jpaEntity = toJpaEntity(page);
        PageJpaEntity saved = jpaRepository.save(jpaEntity);
        return toDomainEntity(saved);
    }

    @Override
    public Optional<Page> findById(String id) {
        Long longId = Long.parseLong(id);
        return jpaRepository.findById(longId)
                .map(this::toDomainEntity);
    }

    @Override
    public List<Page> findByLoreId(String loreId) {
        return jpaRepository.findByLoreId(Long.parseLong(loreId)).stream()
                .map(this::toDomainEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<Page> findByNodeId(String nodeId) {
        Long longNodeId = Long.parseLong(nodeId);
        return jpaRepository.findByNodeId(longNodeId).stream()
                .map(this::toDomainEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<Page> findAll() {
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
    public List<Page> searchByTitle(String query) {
        return jpaRepository.findByTitleContainingIgnoreCase(query).stream()
                .map(this::toDomainEntity)
                .collect(Collectors.toList());
    }

    private Page toDomainEntity(PageJpaEntity e) {
        return Page.builder()
                .id(e.getId().toString())
                .loreId(e.getLoreId() != null ? e.getLoreId().toString() : null)
                .nodeId(e.getNodeId() != null ? e.getNodeId().toString() : null)
                .templateId(e.getTemplateId() != null ? e.getTemplateId().toString() : null)
                .title(e.getTitle())
                .values(e.getValues() != null ? new HashMap<>(e.getValues()) : new HashMap<>())
                .imageValues(e.getImageValues() != null ? new HashMap<>(e.getImageValues()) : new HashMap<>())
                .notes(e.getNotes())
                .tags(e.getTags() != null ? new ArrayList<>(e.getTags()) : new ArrayList<>())
                .relatedPageIds(e.getRelatedPageIds() != null ? new ArrayList<>(e.getRelatedPageIds()) : new ArrayList<>())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private PageJpaEntity toJpaEntity(Page p) {
        return PageJpaEntity.builder()
                .id(p.getId() != null ? Long.parseLong(p.getId()) : null)
                .loreId(p.getLoreId() != null ? Long.parseLong(p.getLoreId()) : null)
                .nodeId(p.getNodeId() != null ? Long.parseLong(p.getNodeId()) : null)
                .templateId(p.getTemplateId() != null && !p.getTemplateId().isBlank()
                        ? Long.parseLong(p.getTemplateId()) : null)
                .title(p.getTitle())
                .values(p.getValues() != null ? new HashMap<>(p.getValues()) : new HashMap<>())
                .imageValues(p.getImageValues() != null ? new HashMap<>(p.getImageValues()) : new HashMap<>())
                .notes(p.getNotes())
                .tags(p.getTags() != null ? new ArrayList<>(p.getTags()) : new ArrayList<>())
                .relatedPageIds(p.getRelatedPageIds() != null ? new ArrayList<>(p.getRelatedPageIds()) : new ArrayList<>())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
