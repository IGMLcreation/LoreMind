package com.loremind.infrastructure.persistence.jpa;

import com.loremind.infrastructure.persistence.entity.NotebookMessageJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotebookMessageJpaRepository extends JpaRepository<NotebookMessageJpaEntity, Long> {
    List<NotebookMessageJpaEntity> findByNotebookIdOrderByCreatedAtAsc(Long notebookId);
    void deleteByNotebookId(Long notebookId);
}
