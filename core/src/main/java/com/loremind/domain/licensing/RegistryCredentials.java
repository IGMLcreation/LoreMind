package com.loremind.domain.licensing;

import java.time.Instant;

/**
 * Credentials de pull pour un registry Docker, distribues par le relais
 * apres verification d'un JWT licence valide.
 * <p>
 * {@code expiresAt} peut etre {@code null} si le credential est statique
 * (cas du PAT GHCR partage en MVP) ; sinon, l'instance doit re-demander
 * de nouveaux credentials avant cette date.
 */
public record RegistryCredentials(
        String registry,
        String username,
        String password,
        Instant expiresAt
) {}
