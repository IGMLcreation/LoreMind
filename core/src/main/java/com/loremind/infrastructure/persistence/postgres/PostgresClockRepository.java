package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.playcontext.Clock;
import com.loremind.domain.playcontext.ClockTrigger;
import com.loremind.domain.playcontext.ports.ClockRepository;
import com.loremind.infrastructure.persistence.entity.ClockJpaEntity;
import com.loremind.infrastructure.persistence.jpa.ClockJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adaptateur d'infrastructure : implémente le port {@link ClockRepository} via JPA/Postgres.
 */
@Repository
public class PostgresClockRepository implements ClockRepository {

    private final ClockJpaRepository jpaRepository;

    public PostgresClockRepository(ClockJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Clock save(Clock clock) {
        return toDomain(jpaRepository.save(toJpaEntity(clock)));
    }

    @Override
    public Optional<Clock> findById(String id) {
        return jpaRepository.findById(Long.parseLong(id)).map(this::toDomain);
    }

    @Override
    public List<Clock> findByPlaythroughId(String playthroughId) {
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

    private Clock toDomain(ClockJpaEntity e) {
        return Clock.builder()
                .id(e.getId().toString())
                .playthroughId(e.getPlaythroughId() != null ? e.getPlaythroughId().toString() : null)
                .name(e.getName())
                .description(e.getDescription())
                .segments(e.getSegments())
                .filled(e.getFilled())
                .order(e.getOrder())
                .triggerType(e.getTriggerType() != null ? e.getTriggerType() : ClockTrigger.NONE)
                .triggerRef(e.getTriggerRef())
                .frontId(e.getFrontId() != null ? e.getFrontId().toString() : null)
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private ClockJpaEntity toJpaEntity(Clock c) {
        Long id = c.getId() != null ? Long.parseLong(c.getId()) : null;
        return ClockJpaEntity.builder()
                .id(id)
                .playthroughId(c.getPlaythroughId() != null ? Long.parseLong(c.getPlaythroughId()) : null)
                .name(c.getName())
                .description(c.getDescription())
                .segments(c.getSegments())
                .filled(c.getFilled())
                .order(c.getOrder())
                .triggerType(c.getTriggerType() != null ? c.getTriggerType() : ClockTrigger.NONE)
                .triggerRef(c.getTriggerRef())
                .frontId(c.getFrontId() != null ? Long.parseLong(c.getFrontId()) : null)
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
