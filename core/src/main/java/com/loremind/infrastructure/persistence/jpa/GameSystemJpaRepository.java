package com.loremind.infrastructure.persistence.jpa;

import com.loremind.infrastructure.persistence.entity.GameSystemJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GameSystemJpaRepository extends JpaRepository<GameSystemJpaEntity, Long> {

    @Query("SELECT g FROM GameSystemJpaEntity g WHERE LOWER(g.name) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<GameSystemJpaEntity> findByNameContainingIgnoreCase(@Param("query") String query);
}
