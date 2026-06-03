package com.loremind.infrastructure.persistence.jpa;

import com.loremind.infrastructure.persistence.entity.PlaythroughFlagJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlaythroughFlagJpaRepository extends JpaRepository<PlaythroughFlagJpaEntity, Long> {

    List<PlaythroughFlagJpaEntity> findByPlaythroughId(Long playthroughId);

    Optional<PlaythroughFlagJpaEntity> findByPlaythroughIdAndName(Long playthroughId, String name);

    @Modifying
    @Transactional
    @Query("DELETE FROM PlaythroughFlagJpaEntity f WHERE f.playthroughId = :playthroughId")
    void deleteByPlaythroughId(@Param("playthroughId") Long playthroughId);
}
