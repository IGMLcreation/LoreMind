package com.loremind.domain.licensing.ports;

import com.loremind.domain.licensing.RegistryCredentials;

/**
 * Port de sortie vers le service relais OAuth Patreon.
 * Encapsule les appels HTTP : refresh JWT et fetch registry credentials.
 */
public interface LicenseRelay {

    /**
     * Demande au relais l'URL OAuth a ouvrir pour connecter le compte Patreon.
     */
    String buildConnectUrl(String instanceId);

    /**
     * Demande au relais de renouveler un JWT existant. Le relais re-verifie
     * le tier Patreon de l'utilisateur ; renvoie un nouveau JWT si toujours
     * actif, ou leve {@link RelayException} sinon.
     */
    String refreshToken(String currentJwt) throws RelayException;

    /**
     * Demande au relais les credentials de pull du registry beta.
     */
    RegistryCredentials fetchRegistryCredentials(String currentJwt) throws RelayException;

    /**
     * Erreurs distinctes emises par le relais. Permet au service application
     * de differencier "tier expire" (action utilisateur) de "relais down"
     * (action transitoire, garde la grace period).
     */
    class RelayException extends Exception {
        private final RelayErrorKind kind;

        public RelayException(RelayErrorKind kind, String message) {
            super(message);
            this.kind = kind;
        }

        public RelayException(RelayErrorKind kind, String message, Throwable cause) {
            super(message, cause);
            this.kind = kind;
        }

        public RelayErrorKind getKind() {
            return kind;
        }
    }

    enum RelayErrorKind {
        /** Le relais est joignable mais refuse : tier non actif, JWT trop ancien, etc. */
        REJECTED,
        /** Le relais a renvoye un JWT mais il est invalide / non parsable. */
        BAD_RESPONSE,
        /** Le relais est injoignable / 5xx / timeout. */
        TRANSIENT
    }
}
