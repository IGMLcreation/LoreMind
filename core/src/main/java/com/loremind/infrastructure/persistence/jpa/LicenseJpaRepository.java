package com.loremind.infrastructure.persistence.jpa;

import com.loremind.infrastructure.persistence.entity.LicenseJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LicenseJpaRepository extends JpaRepository<LicenseJpaEntity, String> {
}
