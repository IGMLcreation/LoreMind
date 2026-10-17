package com.loremind.infrastructure.persistence.jpa;

import com.loremind.infrastructure.persistence.entity.RemoteDeviceJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface RemoteDeviceJpaRepository extends JpaRepository<RemoteDeviceJpaEntity, Long> {

    Optional<RemoteDeviceJpaEntity> findByTokenHash(String tokenHash);

    /** Mise à jour ciblée (pas de relecture de l'entité à chaque requête d'une tablette). */
    @Modifying
    @Transactional
    @Query("update RemoteDeviceJpaEntity d set d.lastSeenAt = :seenAt where d.id = :id")
    void touch(@Param("id") Long id, @Param("seenAt") Instant seenAt);
}
