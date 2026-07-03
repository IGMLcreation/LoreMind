package com.loremind.infrastructure.persistence.jpa;

import com.loremind.infrastructure.persistence.entity.ClockJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository Spring Data JPA pour ClockJpaEntity.
 */
@Repository
public interface ClockJpaRepository extends JpaRepository<ClockJpaEntity, Long> {

    List<ClockJpaEntity> findByPlaythroughIdOrderByOrderAsc(Long playthroughId);
}
