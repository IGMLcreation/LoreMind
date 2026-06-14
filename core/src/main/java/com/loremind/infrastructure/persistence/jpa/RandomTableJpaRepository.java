package com.loremind.infrastructure.persistence.jpa;

import com.loremind.infrastructure.persistence.entity.RandomTableJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RandomTableJpaRepository extends JpaRepository<RandomTableJpaEntity, Long> {

    List<RandomTableJpaEntity> findByCampaignIdOrderByOrderAsc(Long campaignId);

    /** Recherche globale : bornée pour ne jamais inonder la palette de résultats. */
    List<RandomTableJpaEntity> findTop20ByNameContainingIgnoreCaseOrderByNameAsc(String name);
}
