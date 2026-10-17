package com.loremind.infrastructure.persistence.jpa;

import com.loremind.infrastructure.persistence.entity.RemoteAccessConfigJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RemoteAccessConfigJpaRepository extends JpaRepository<RemoteAccessConfigJpaEntity, String> {
}
