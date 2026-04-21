package com.loremind.infrastructure.persistence.jpa;

import com.loremind.infrastructure.persistence.entity.CampaignJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository Spring Data JPA pour CampaignJpaEntity.
 */
@Repository
public interface CampaignJpaRepository extends JpaRepository<CampaignJpaEntity, Long> {

    @Query("SELECT c FROM CampaignJpaEntity c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<CampaignJpaEntity> findByNameContainingIgnoreCase(@Param("query") String query);
}
