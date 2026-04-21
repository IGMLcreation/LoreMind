package com.loremind.infrastructure.persistence.jpa;

import com.loremind.infrastructure.persistence.entity.SceneJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository Spring Data JPA pour SceneJpaEntity.
 */
@Repository
public interface SceneJpaRepository extends JpaRepository<SceneJpaEntity, Long> {

    List<SceneJpaEntity> findByChapterId(Long chapterId);
}
