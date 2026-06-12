package com.loremind.infrastructure.persistence.jpa;

import com.loremind.infrastructure.persistence.entity.ItemCatalogJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemCatalogJpaRepository extends JpaRepository<ItemCatalogJpaEntity, Long> {

    List<ItemCatalogJpaEntity> findByCampaignIdOrderByOrderAsc(Long campaignId);

    /** Recherche globale : bornée pour ne jamais inonder la palette de résultats. */
    List<ItemCatalogJpaEntity> findTop20ByNameContainingIgnoreCaseOrderByNameAsc(String name);
}
