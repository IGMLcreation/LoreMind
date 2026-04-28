package com.loremind.domain.licensing;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Licence Patreon installee dans cette instance LoreMind.
 * <p>
 * Singleton (une seule licence par instance, identifiee logiquement par
 * {@code id = "current"}). Contient le JWT brut emis par le relais OAuth
 * + les claims extraits a la verification, plus l'etat operationnel
 * (derniere tentative de refresh, succes/echec).
 * <p>
 * <b>Note securite :</b> {@link #rawJwt} est stocke tel quel ; sa signature
 * Ed25519 est verifiee a chaque lecture. Pas besoin de chiffrement au repos
 * supplementaire — un attaquant qui a acces a la base a deja l'instance,
 * et le JWT ne donne aucun pouvoir au-dela du canal beta de cette instance.
 */
@Data
@Builder
public class License {

    private String id;

    private String rawJwt;

    private String patreonUserId;

    private String tierId;

    private String instanceId;

    private Instant issuedAt;

    private Instant expiresAt;

    private Instant lastRefreshAttemptAt;

    private boolean lastRefreshSucceeded;

    private boolean betaChannelEnabled;

    private Instant createdAt;

    private Instant updatedAt;
}
