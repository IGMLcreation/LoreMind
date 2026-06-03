package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.playcontext.Session;
import com.loremind.domain.playcontext.ports.SessionRepository;
import com.loremind.infrastructure.persistence.entity.SessionJpaEntity;
import com.loremind.infrastructure.persistence.jpa.SessionJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adaptateur d'infrastructure : implémente le port SessionRepository en utilisant
 * playthrough_id comme parent (depuis la refonte Playthrough).
 */
@Repository
public class PostgresSessionRepository implements SessionRepository {

    private final SessionJpaRepository jpaRepository;

    public PostgresSessionRepository(SessionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Session save(Session session) {
        SessionJpaEntity saved = jpaRepository.save(toJpaEntity(session));
        return toDomain(saved);
    }

    @Override
    public Optional<Session> findById(String id) {
        return jpaRepository.findById(Long.parseLong(id)).map(this::toDomain);
    }

    @Override
    public List<Session> findAll() {
        return jpaRepository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Session> findByPlaythroughId(String playthroughId) {
        return jpaRepository.findByPlaythroughIdOrderByStartedAtDesc(Long.parseLong(playthroughId)).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Session> findActive() {
        return jpaRepository.findFirstByEndedAtIsNull().map(this::toDomain);
    }

    @Override
    public Optional<Session> findActiveByPlaythroughId(String playthroughId) {
        return jpaRepository.findFirstByPlaythroughIdAndEndedAtIsNull(Long.parseLong(playthroughId))
                .map(this::toDomain);
    }

    @Override
    public void deleteById(String id) {
        jpaRepository.deleteById(Long.parseLong(id));
    }

    @Override
    public boolean existsById(String id) {
        return jpaRepository.existsById(Long.parseLong(id));
    }

    private Session toDomain(SessionJpaEntity jpa) {
        return Session.builder()
                .id(jpa.getId().toString())
                .name(jpa.getName())
                .playthroughId(jpa.getPlaythroughId() != null ? jpa.getPlaythroughId().toString() : null)
                .startedAt(jpa.getStartedAt())
                .endedAt(jpa.getEndedAt())
                .createdAt(jpa.getCreatedAt())
                .updatedAt(jpa.getUpdatedAt())
                .build();
    }

    private SessionJpaEntity toJpaEntity(Session session) {
        Long id = session.getId() != null ? Long.parseLong(session.getId()) : null;
        return SessionJpaEntity.builder()
                .id(id)
                .name(session.getName())
                .playthroughId(session.getPlaythroughId() != null ? Long.parseLong(session.getPlaythroughId()) : null)
                .startedAt(session.getStartedAt())
                .endedAt(session.getEndedAt())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }
}
