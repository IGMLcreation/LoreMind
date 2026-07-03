package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.playcontext.Front;
import com.loremind.domain.playcontext.ports.FrontRepository;
import com.loremind.infrastructure.persistence.entity.FrontJpaEntity;
import com.loremind.infrastructure.persistence.jpa.FrontJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adaptateur d'infrastructure : implémente le port {@link FrontRepository} via JPA/Postgres.
 */
@Repository
public class PostgresFrontRepository implements FrontRepository {

    private final FrontJpaRepository jpaRepository;

    public PostgresFrontRepository(FrontJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Front save(Front front) {
        return toDomain(jpaRepository.save(toJpaEntity(front)));
    }

    @Override
    public Optional<Front> findById(String id) {
        return jpaRepository.findById(Long.parseLong(id)).map(this::toDomain);
    }

    @Override
    public List<Front> findByPlaythroughId(String playthroughId) {
        return jpaRepository.findByPlaythroughIdOrderByOrderAsc(Long.parseLong(playthroughId)).stream()
                .map(this::toDomain)
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

    private Front toDomain(FrontJpaEntity e) {
        return Front.builder()
                .id(e.getId().toString())
                .playthroughId(e.getPlaythroughId() != null ? e.getPlaythroughId().toString() : null)
                .name(e.getName())
                .description(e.getDescription())
                .order(e.getOrder())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private FrontJpaEntity toJpaEntity(Front f) {
        Long id = f.getId() != null ? Long.parseLong(f.getId()) : null;
        return FrontJpaEntity.builder()
                .id(id)
                .playthroughId(f.getPlaythroughId() != null ? Long.parseLong(f.getPlaythroughId()) : null)
                .name(f.getName())
                .description(f.getDescription())
                .order(f.getOrder())
                .createdAt(f.getCreatedAt())
                .updatedAt(f.getUpdatedAt())
                .build();
    }
}
