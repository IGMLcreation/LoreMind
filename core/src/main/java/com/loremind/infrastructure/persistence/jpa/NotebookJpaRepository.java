package com.loremind.infrastructure.persistence.jpa;

import com.loremind.infrastructure.persistence.entity.NotebookJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotebookJpaRepository extends JpaRepository<NotebookJpaEntity, Long> {
    List<NotebookJpaEntity> findByCampaignIdOrderByUpdatedAtDesc(Long campaignId);
}
