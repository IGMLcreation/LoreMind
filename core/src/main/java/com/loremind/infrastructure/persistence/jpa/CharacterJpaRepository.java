package com.loremind.infrastructure.persistence.jpa;

import com.loremind.infrastructure.persistence.entity.CharacterJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CharacterJpaRepository extends JpaRepository<CharacterJpaEntity, Long> {

    List<CharacterJpaEntity> findByPlaythroughIdOrderByOrderAsc(Long playthroughId);

    /** Recherche globale : bornée pour ne jamais inonder la palette de résultats. */
    List<CharacterJpaEntity> findTop20ByNameContainingIgnoreCaseOrderByNameAsc(String name);
}
