package com.loremind.domain.licensing;

import java.time.Instant;

/**
 * Claims extraits d'un JWT licence apres verification de signature.
 * Immuable.
 */
public record LicenseClaims(
        String subject,
        String tierId,
        String instanceId,
        Instant issuedAt,
        Instant expiresAt
) {}
