package com.loremind.infrastructure.persistence.jpa;

import com.loremind.infrastructure.persistence.entity.PageJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository Spring Data JPA pour PageJpaEntity.
 */
@Repository
public interface PageJpaRepository extends JpaRepository<PageJpaEntity, Long> {

    List<PageJpaEntity> findByLoreId(Long loreId);

    List<PageJpaEntity> findByNodeId(Long nodeId);

    List<PageJpaEntity> findByTemplateId(Long templateId);

    long countByLoreId(Long loreId);

    List<PageJpaEntity> findByTitleContainingIgnoreCase(String query);
}
