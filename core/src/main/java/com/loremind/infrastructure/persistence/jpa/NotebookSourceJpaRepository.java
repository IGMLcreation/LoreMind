package com.loremind.infrastructure.persistence.jpa;

import com.loremind.infrastructure.persistence.entity.NotebookSourceJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotebookSourceJpaRepository extends JpaRepository<NotebookSourceJpaEntity, Long> {
    List<NotebookSourceJpaEntity> findByNotebookIdOrderByCreatedAtAsc(Long notebookId);
    void deleteByNotebookId(Long notebookId);
}
