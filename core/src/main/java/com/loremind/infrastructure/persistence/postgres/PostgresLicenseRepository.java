package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.licensing.License;
import com.loremind.domain.licensing.ports.LicenseRepository;
import com.loremind.infrastructure.persistence.entity.LicenseJpaEntity;
import com.loremind.infrastructure.persistence.jpa.LicenseJpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public class PostgresLicenseRepository implements LicenseRepository {

    static final String CURRENT_ID = "current";

    private final LicenseJpaRepository jpa;

    public PostgresLicenseRepository(LicenseJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<License> findCurrent() {
        return jpa.findById(CURRENT_ID).map(this::toDomain);
    }

    @Override
    public License save(License license) {
        LicenseJpaEntity entity = toEntity(license);
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(Instant.now());
        }
        LicenseJpaEntity saved = jpa.save(entity);
        return toDomain(saved);
    }

    @Override
    public void deleteCurrent() {
        jpa.deleteById(CURRENT_ID);
    }

    private License toDomain(LicenseJpaEntity e) {
        return License.builder()
                .id(e.getId())
                .rawJwt(e.getRawJwt())
                .patreonUserId(e.getPatreonUserId())
                .tierId(e.getTierId())
                .instanceId(e.getInstanceId())
                .issuedAt(e.getIssuedAt())
                .expiresAt(e.getExpiresAt())
                .lastRefreshAttemptAt(e.getLastRefreshAttemptAt())
                .lastRefreshSucceeded(e.isLastRefreshSucceeded())
                .betaChannelEnabled(e.isBetaChannelEnabled())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private LicenseJpaEntity toEntity(License l) {
        return LicenseJpaEntity.builder()
                .id(CURRENT_ID)
                .rawJwt(l.getRawJwt())
                .patreonUserId(l.getPatreonUserId())
                .tierId(l.getTierId())
                .instanceId(l.getInstanceId())
                .issuedAt(l.getIssuedAt())
                .expiresAt(l.getExpiresAt())
                .lastRefreshAttemptAt(l.getLastRefreshAttemptAt())
                .lastRefreshSucceeded(l.isLastRefreshSucceeded())
                .betaChannelEnabled(l.isBetaChannelEnabled())
                .createdAt(l.getCreatedAt())
                .updatedAt(l.getUpdatedAt())
                .build();
    }
}
