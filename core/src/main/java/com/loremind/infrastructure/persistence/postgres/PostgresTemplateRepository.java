package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.lorecontext.Template;
import com.loremind.domain.lorecontext.ports.TemplateRepository;
import com.loremind.infrastructure.persistence.entity.TemplateJpaEntity;
import com.loremind.infrastructure.persistence.jpa.TemplateJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adaptateur d'infrastructure qui implémente le Port TemplateRepository.
 * Responsable du mapping bidirectionnel entre l'entité de domaine (Template)
 * et l'entité JPA (TemplateJpaEntity).
 */
@Repository
public class PostgresTemplateRepository implements TemplateRepository {

    private final TemplateJpaRepository jpaRepository;

    public PostgresTemplateRepository(TemplateJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Template save(Template template) {
        TemplateJpaEntity saved = jpaRepository.save(toJpaEntity(template));
        return toDomainEntity(saved);
    }

    @Override
    public Optional<Template> findById(String id) {
        return jpaRepository.findById(Long.parseLong(id)).map(this::toDomainEntity);
    }

    @Override
    public List<Template> findAll() {
        return jpaRepository.findAll().stream()
                .map(this::toDomainEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<Template> findByLoreId(String loreId) {
        return jpaRepository.findByLoreId(Long.parseLong(loreId)).stream()
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

    @Override
    public List<Template> searchByName(String query) {
        return jpaRepository.findByNameContainingIgnoreCase(query).stream()
                .map(this::toDomainEntity)
                .collect(Collectors.toList());
    }

    // --- Mapping ----------------------------------------------------------

    private Template toDomainEntity(TemplateJpaEntity e) {
        return Template.builder()
                .id(e.getId().toString())
                .loreId(e.getLoreId() != null ? e.getLoreId().toString() : null)
                .name(e.getName())
                .description(e.getDescription())
                .defaultNodeId(e.getDefaultNodeId() != null ? e.getDefaultNodeId().toString() : null)
                .fields(e.getFields() != null
                        ? new ArrayList<>(e.getFields())
                        : new ArrayList<>())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private TemplateJpaEntity toJpaEntity(Template t) {
        return TemplateJpaEntity.builder()
                .id(t.getId() != null ? Long.parseLong(t.getId()) : null)
                .loreId(t.getLoreId() != null ? Long.parseLong(t.getLoreId()) : null)
                .name(t.getName())
                .description(t.getDescription())
                .defaultNodeId(t.getDefaultNodeId() != null ? Long.parseLong(t.getDefaultNodeId()) : null)
                .fields(t.getFields() != null
                        ? new ArrayList<>(t.getFields())
                        : new ArrayList<>())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }
}
