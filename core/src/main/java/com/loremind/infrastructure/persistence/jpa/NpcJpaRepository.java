package com.loremind.infrastructure.persistence.jpa;

import com.loremind.infrastructure.persistence.entity.NpcJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NpcJpaRepository extends JpaRepository<NpcJpaEntity, Long> {

    List<NpcJpaEntity> findByCampaignIdOrderByOrderAsc(Long campaignId);
}
