package com.loremind.infrastructure.persistence.jpa;

import com.loremind.infrastructure.persistence.entity.QuestProgressionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestProgressionJpaRepository extends JpaRepository<QuestProgressionJpaEntity, Long> {

    List<QuestProgressionJpaEntity> findByPlaythroughId(Long playthroughId);

    Optional<QuestProgressionJpaEntity> findByPlaythroughIdAndQuestId(Long playthroughId, Long questId);

    @Modifying
    @Transactional
    @Query("DELETE FROM QuestProgressionJpaEntity q WHERE q.playthroughId = :playthroughId")
    void deleteByPlaythroughId(@Param("playthroughId") Long playthroughId);

    @Modifying
    @Transactional
    @Query("DELETE FROM QuestProgressionJpaEntity q WHERE q.questId = :questId")
    void deleteByQuestId(@Param("questId") Long questId);
}
