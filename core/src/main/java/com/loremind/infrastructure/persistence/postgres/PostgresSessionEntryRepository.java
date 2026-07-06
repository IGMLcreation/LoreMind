package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.playcontext.SessionEntry;
import com.loremind.domain.playcontext.ports.SessionEntryRepository;
import com.loremind.infrastructure.persistence.entity.SessionEntryJpaEntity;
import com.loremind.infrastructure.persistence.jpa.SessionEntryJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Adaptateur d'infrastructure : implémente le Port SessionEntryRepository.
 */
@Repository
public class PostgresSessionEntryRepository implements SessionEntryRepository {

    private final SessionEntryJpaRepository jpaRepository;

    public PostgresSessionEntryRepository(SessionEntryJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public SessionEntry save(SessionEntry entry) {
        SessionEntryJpaEntity saved = jpaRepository.save(toJpaEntity(entry));
        return toDomain(saved);
    }

    @Override
    public Optional<SessionEntry> findById(String id) {
        return jpaRepository.findById(Long.parseLong(id)).map(this::toDomain);
    }

    @Override
    public List<SessionEntry> findBySessionId(String sessionId) {
        return jpaRepository.findBySessionIdOrderByOccurredAtAsc(sessionId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void deleteById(String id) {
        jpaRepository.deleteById(Long.parseLong(id));
    }

    /** {@code @Transactional} requis : Spring Data exige une transaction pour les deleteByXxx dérivés. */
    @Override
    @Transactional
    public void deleteBySessionId(String sessionId) {
        jpaRepository.deleteBySessionId(sessionId);
    }

    @Override
    public boolean existsById(String id) {
        return jpaRepository.existsById(Long.parseLong(id));
    }

    private SessionEntry toDomain(SessionEntryJpaEntity jpa) {
        return SessionEntry.builder()
                .id(jpa.getId().toString())
                .sessionId(jpa.getSessionId())
                .type(jpa.getType())
                .content(jpa.getContent())
                .occurredAt(jpa.getOccurredAt())
                .createdAt(jpa.getCreatedAt())
                .updatedAt(jpa.getUpdatedAt())
                .build();
    }

    private SessionEntryJpaEntity toJpaEntity(SessionEntry entry) {
        Long id = entry.getId() != null ? Long.parseLong(entry.getId()) : null;
        return SessionEntryJpaEntity.builder()
                .id(id)
                .sessionId(entry.getSessionId())
                .type(entry.getType())
                .content(entry.getContent())
                .occurredAt(entry.getOccurredAt())
                .createdAt(entry.getCreatedAt())
                .updatedAt(entry.getUpdatedAt())
                .build();
    }
}
