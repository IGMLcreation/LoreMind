package com.loremind.infrastructure.persistence.jpa;

import com.loremind.infrastructure.persistence.entity.ImageJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository Spring Data JPA pour ImageJpaEntity.
 */
@Repository
public interface ImageJpaRepository extends JpaRepository<ImageJpaEntity, Long> {

    /** Recherche par cle de stockage (unique). Utilise par l'import de contenu. */
    Optional<ImageJpaEntity> findByStorageKey(String storageKey);
}
