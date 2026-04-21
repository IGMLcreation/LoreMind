package com.loremind.infrastructure.persistence.jpa;

import com.loremind.infrastructure.persistence.entity.ConversationJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository Spring Data JPA pour ConversationJpaEntity.
 *
 * Les requetes de listing par contexte gerent explicitement les NULL parce
 * que JPQL `=` ne matche pas NULL. On combine `IS NULL` / `=` selon si le
 * filtre est fourni — plus simple qu'une Specification Criteria API.
 */
@Repository
public interface ConversationJpaRepository extends JpaRepository<ConversationJpaEntity, Long> {

    /** Listing Lore racine (entity_type IS NULL). */
    @Query("""
            SELECT c FROM ConversationJpaEntity c
            WHERE c.loreId = :loreId
              AND c.entityType IS NULL
            ORDER BY c.updatedAt DESC
            """)
    List<ConversationJpaEntity> findByLoreRoot(@Param("loreId") String loreId);

    /** Listing Lore + entite precise. */
    @Query("""
            SELECT c FROM ConversationJpaEntity c
            WHERE c.loreId = :loreId
              AND c.entityType = :entityType
              AND c.entityId = :entityId
            ORDER BY c.updatedAt DESC
            """)
    List<ConversationJpaEntity> findByLoreAndEntity(
            @Param("loreId") String loreId,
            @Param("entityType") String entityType,
            @Param("entityId") String entityId);

    /** Listing Campagne racine. */
    @Query("""
            SELECT c FROM ConversationJpaEntity c
            WHERE c.campaignId = :campaignId
              AND c.entityType IS NULL
            ORDER BY c.updatedAt DESC
            """)
    List<ConversationJpaEntity> findByCampaignRoot(@Param("campaignId") String campaignId);

    /** Listing Campagne + entite precise. */
    @Query("""
            SELECT c FROM ConversationJpaEntity c
            WHERE c.campaignId = :campaignId
              AND c.entityType = :entityType
              AND c.entityId = :entityId
            ORDER BY c.updatedAt DESC
            """)
    List<ConversationJpaEntity> findByCampaignAndEntity(
            @Param("campaignId") String campaignId,
            @Param("entityType") String entityType,
            @Param("entityId") String entityId);
}
