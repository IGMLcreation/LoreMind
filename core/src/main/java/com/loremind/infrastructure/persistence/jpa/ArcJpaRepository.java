package com.loremind.infrastructure.persistence.jpa;

import com.loremind.infrastructure.persistence.entity.ArcJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository Spring Data JPA pour ArcJpaEntity.
 */
@Repository
public interface ArcJpaRepository extends JpaRepository<ArcJpaEntity, Long> {

    List<ArcJpaEntity> findByCampaignId(Long campaignId);
}
