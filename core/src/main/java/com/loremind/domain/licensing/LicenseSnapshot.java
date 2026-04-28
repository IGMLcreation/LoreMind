package com.loremind.domain.licensing;

import java.time.Instant;

/**
 * Vue immuable de la licence pour exposition vers les couches superieures.
 * Decouple le domaine du DTO web et permet de calculer le {@link LicenseStatus}
 * a un instant donne sans muter l'entite.
 */
public record LicenseSnapshot(
        LicenseStatus status,
        String patreonUserId,
        String tierId,
        String instanceId,
        Instant expiresAt,
        Instant lastRefreshAttemptAt,
        boolean lastRefreshSucceeded,
        boolean betaChannelEnabled
) {
    public static LicenseSnapshot none() {
        return new LicenseSnapshot(LicenseStatus.NONE, null, null, null, null, null, false, false);
    }
}
