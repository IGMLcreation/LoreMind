package com.loremind.infrastructure.persistence.jpa;

import com.loremind.infrastructure.persistence.entity.NotebookMessageJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotebookMessageJpaRepository extends JpaRepository<NotebookMessageJpaEntity, Long> {
    /** Messages de la conversation ACTIVE (les archives sont exclues). */
    List<NotebookMessageJpaEntity> findByNotebookIdAndArchivedAtIsNullOrderByCreatedAtAsc(Long notebookId);

    /** Messages archivés (tous lots confondus, l'appelant regroupe par archivedAt). */
    List<NotebookMessageJpaEntity> findByNotebookIdAndArchivedAtIsNotNullOrderByCreatedAtAsc(Long notebookId);

    void deleteByNotebookId(Long notebookId);

    /** « Vider la conversation » : archive le fil actif en un lot horodaté. */
    @Modifying
    @Query("update NotebookMessageJpaEntity m set m.archivedAt = :now "
            + "where m.notebookId = :notebookId and m.archivedAt is null")
    int archiveActiveMessages(@Param("notebookId") Long notebookId, @Param("now") LocalDateTime now);
}
