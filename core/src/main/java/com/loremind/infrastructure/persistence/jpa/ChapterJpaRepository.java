package com.loremind.infrastructure.persistence.jpa;

import com.loremind.infrastructure.persistence.entity.ChapterJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository Spring Data JPA pour ChapterJpaEntity.
 */
@Repository
public interface ChapterJpaRepository extends JpaRepository<ChapterJpaEntity, Long> {

    List<ChapterJpaEntity> findByArcId(Long arcId);
}
