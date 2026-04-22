package com.loremind.infrastructure.persistence.jpa;

import com.loremind.infrastructure.persistence.entity.CharacterJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CharacterJpaRepository extends JpaRepository<CharacterJpaEntity, Long> {

    List<CharacterJpaEntity> findByCampaignIdOrderByOrderAsc(Long campaignId);
}
