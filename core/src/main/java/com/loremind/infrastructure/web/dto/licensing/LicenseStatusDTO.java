package com.loremind.infrastructure.web.dto.licensing;

import com.loremind.domain.licensing.LicenseSnapshot;

import java.time.Instant;

/**
 * Vue serialisee de l'etat de la licence pour le frontend.
 * Le {@code rawJwt} n'est volontairement JAMAIS expose.
 */
public record LicenseStatusDTO(
        boolean enabled,
        String status,
        String patreonUserId,
        String tierId,
        String instanceId,
        Instant expiresAt,
        Instant lastRefreshAttemptAt,
        Boolean lastRefreshSucceeded,
        boolean betaChannelEnabled
) {
    public static LicenseStatusDTO from(boolean enabled, LicenseSnapshot snap) {
        return new LicenseStatusDTO(
                enabled,
                snap.status().name(),
                snap.patreonUserId(),
                snap.tierId(),
                snap.instanceId(),
                snap.expiresAt(),
                snap.lastRefreshAttemptAt(),
                snap.lastRefreshAttemptAt() != null ? snap.lastRefreshSucceeded() : null,
                snap.betaChannelEnabled()
        );
    }
}
