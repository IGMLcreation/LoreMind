package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.gamesystemcontext.GameSystem;
import com.loremind.domain.gamesystemcontext.ports.GameSystemRepository;
import com.loremind.infrastructure.persistence.entity.GameSystemJpaEntity;
import com.loremind.infrastructure.persistence.jpa.GameSystemJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class PostgresGameSystemRepository implements GameSystemRepository {

    private final GameSystemJpaRepository jpaRepository;

    public PostgresGameSystemRepository(GameSystemJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public GameSystem save(GameSystem gameSystem) {
        GameSystemJpaEntity entity = toJpaEntity(gameSystem);
        GameSystemJpaEntity saved = jpaRepository.save(entity);
        return toDomainEntity(saved);
    }

    @Override
    public Optional<GameSystem> findById(String id) {
        return jpaRepository.findById(Long.parseLong(id)).map(this::toDomainEntity);
    }

    @Override
    public List<GameSystem> findAll() {
        return jpaRepository.findAll().stream()
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
    public List<GameSystem> searchByName(String query) {
        return jpaRepository.findByNameContainingIgnoreCase(query).stream()
                .map(this::toDomainEntity)
                .collect(Collectors.toList());
    }

    private GameSystem toDomainEntity(GameSystemJpaEntity e) {
        return GameSystem.builder()
                .id(e.getId().toString())
                .name(e.getName())
                .description(e.getDescription())
                .rulesMarkdown(e.getRulesMarkdown())
                .author(e.getAuthor())
                .isPublic(e.isPublic())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private GameSystemJpaEntity toJpaEntity(GameSystem g) {
        Long id = g.getId() != null ? Long.parseLong(g.getId()) : null;
        return GameSystemJpaEntity.builder()
                .id(id)
                .name(g.getName())
                .description(g.getDescription())
                .rulesMarkdown(g.getRulesMarkdown())
                .author(g.getAuthor())
                .isPublic(g.isPublic())
                .createdAt(g.getCreatedAt())
                .updatedAt(g.getUpdatedAt())
                .build();
    }
}
