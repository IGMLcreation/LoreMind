package com.loremind.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entite JPA pour la licence Patreon installee.
 * <p>
 * Singleton : une seule ligne par instance (id = "current"). Ce design permet
 * de ne jamais avoir de licence "fantome" en base et de simplifier les queries.
 */
@Entity
@Table(name = "licenses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LicenseJpaEntity {

    @Id
    private String id;

    @Column(name = "raw_jwt", columnDefinition = "TEXT", nullable = false)
    private String rawJwt;

    @Column(name = "patreon_user_id", nullable = false)
    private String patreonUserId;

    @Column(name = "tier_id", nullable = false)
    private String tierId;

    @Column(name = "instance_id", nullable = false)
    private String instanceId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_refresh_attempt_at")
    private Instant lastRefreshAttemptAt;

    @Column(name = "last_refresh_succeeded", nullable = false)
    private boolean lastRefreshSucceeded;

    @Column(name = "beta_channel_enabled", nullable = false)
    private boolean betaChannelEnabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
