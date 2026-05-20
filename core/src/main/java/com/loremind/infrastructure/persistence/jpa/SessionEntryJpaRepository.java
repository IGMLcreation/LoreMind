package com.loremind.infrastructure.persistence.jpa;

import com.loremind.infrastructure.persistence.entity.SessionEntryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SessionEntryJpaRepository extends JpaRepository<SessionEntryJpaEntity, Long> {

    List<SessionEntryJpaEntity> findBySessionIdOrderByOccurredAtAsc(String sessionId);

    void deleteBySessionId(String sessionId);
}
