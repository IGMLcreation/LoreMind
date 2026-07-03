package com.loremind.infrastructure.persistence.jpa;

import com.loremind.infrastructure.persistence.entity.QuestJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository Spring Data JPA pour QuestJpaEntity.
 */
@Repository
public interface QuestJpaRepository extends JpaRepository<QuestJpaEntity, Long> {

    List<QuestJpaEntity> findByCampaignId(Long campaignId);

    List<QuestJpaEntity> findByArcId(Long arcId);
}
