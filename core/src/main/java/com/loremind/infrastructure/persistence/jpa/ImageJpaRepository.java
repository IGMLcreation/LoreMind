package com.loremind.infrastructure.persistence.jpa;

import com.loremind.infrastructure.persistence.entity.ImageJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository Spring Data JPA pour ImageJpaEntity.
 * Ne contient aucune requete custom pour l'instant : CRUD standard suffit.
 */
@Repository
public interface ImageJpaRepository extends JpaRepository<ImageJpaEntity, Long> {
}
