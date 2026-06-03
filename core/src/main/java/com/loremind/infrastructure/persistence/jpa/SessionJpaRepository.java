package com.loremind.infrastructure.persistence.jpa;

import com.loremind.infrastructure.persistence.entity.SessionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository Spring Data JPA pour SessionJpaEntity.
 */
@Repository
public interface SessionJpaRepository extends JpaRepository<SessionJpaEntity, Long> {

    List<SessionJpaEntity> findByPlaythroughIdOrderByStartedAtDesc(Long playthroughId);

    Optional<SessionJpaEntity> findFirstByEndedAtIsNull();

    Optional<SessionJpaEntity> findFirstByPlaythroughIdAndEndedAtIsNull(Long playthroughId);
}
