package com.loremind.infrastructure.persistence.jpa;

import com.loremind.infrastructure.persistence.entity.FrontJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository Spring Data JPA pour FrontJpaEntity.
 */
@Repository
public interface FrontJpaRepository extends JpaRepository<FrontJpaEntity, Long> {

    List<FrontJpaEntity> findByPlaythroughIdOrderByOrderAsc(Long playthroughId);
}
