package com.loremind.infrastructure.persistence.jpa;

import com.loremind.infrastructure.persistence.entity.EnemyJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EnemyJpaRepository extends JpaRepository<EnemyJpaEntity, Long> {

    List<EnemyJpaEntity> findByCampaignIdOrderByOrderAsc(Long campaignId);

    /** Recherche globale : bornée pour ne jamais inonder la palette de résultats. */
    List<EnemyJpaEntity> findTop20ByNameContainingIgnoreCaseOrderByNameAsc(String name);
}
