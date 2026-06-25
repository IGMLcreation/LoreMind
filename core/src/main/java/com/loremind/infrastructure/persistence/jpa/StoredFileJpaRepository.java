package com.loremind.infrastructure.persistence.jpa;

import com.loremind.infrastructure.persistence.entity.StoredFileJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository Spring Data JPA pour StoredFileJpaEntity.
 */
@Repository
public interface StoredFileJpaRepository extends JpaRepository<StoredFileJpaEntity, Long> {

    /** Recherche par cle de stockage (unique). Utilise par l'import de contenu. */
    Optional<StoredFileJpaEntity> findByStorageKey(String storageKey);
}
