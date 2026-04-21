package com.loremind.infrastructure.persistence.jpa;

import com.loremind.infrastructure.persistence.entity.TemplateJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository Spring Data JPA pour TemplateJpaEntity.
 */
@Repository
public interface TemplateJpaRepository extends JpaRepository<TemplateJpaEntity, Long> {

    List<TemplateJpaEntity> findByLoreId(Long loreId);

    List<TemplateJpaEntity> findByNameContainingIgnoreCase(String query);
}
