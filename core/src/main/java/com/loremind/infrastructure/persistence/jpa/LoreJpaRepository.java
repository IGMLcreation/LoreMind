package com.loremind.infrastructure.persistence.jpa;

import com.loremind.infrastructure.persistence.entity.LoreJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository Spring Data JPA pour LoreJpaEntity.
 * Utilisé par l'adaptateur PostgresLoreRepository.
 */
@Repository
public interface LoreJpaRepository extends JpaRepository<LoreJpaEntity, Long> {

    List<LoreJpaEntity> findByNameContainingIgnoreCase(String query);
}
