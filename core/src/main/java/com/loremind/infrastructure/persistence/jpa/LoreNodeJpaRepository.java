package com.loremind.infrastructure.persistence.jpa;

import com.loremind.infrastructure.persistence.entity.LoreNodeJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository Spring Data JPA pour LoreNodeJpaEntity.
 */
@Repository
public interface LoreNodeJpaRepository extends JpaRepository<LoreNodeJpaEntity, Long> {

    List<LoreNodeJpaEntity> findByLoreId(Long loreId);

    List<LoreNodeJpaEntity> findByParentId(Long parentId);

    long countByLoreId(Long loreId);

    List<LoreNodeJpaEntity> findByNameContainingIgnoreCase(String query);
}
