package com.loremind.domain.licensing.ports;

import com.loremind.domain.licensing.LicenseClaims;

/**
 * Port de sortie : verification de signature et extraction des claims
 * d'un JWT emis par le relais.
 * <p>
 * Implemente cote infrastructure avec la cle publique Ed25519 embarquee
 * (SPKI PEM via configuration {@code licensing.jwt.public-key}).
 */
public interface JwtVerifier {

    /**
     * Verifie la signature, l'issuer, l'audience et l'expiration du JWT.
     * @throws JwtVerificationException si la signature est invalide ou les claims malformes
     */
    LicenseClaims verify(String rawJwt) throws JwtVerificationException;

    /**
     * @return true si la cle publique est configuree et utilisable.
     *         Permet a l'application de masquer la feature licensing si pas configuree.
     */
    boolean isConfigured();

    class JwtVerificationException extends Exception {
        public JwtVerificationException(String message) {
            super(message);
        }
        public JwtVerificationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
