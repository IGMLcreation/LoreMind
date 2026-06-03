package com.loremind.infrastructure.persistence.jpa;

import com.loremind.infrastructure.persistence.entity.PlaythroughJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlaythroughJpaRepository extends JpaRepository<PlaythroughJpaEntity, Long> {

    List<PlaythroughJpaEntity> findByCampaignId(Long campaignId);
}
