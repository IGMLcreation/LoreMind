package com.loremind.application.remoteaccess;

/** Appairage refusé : code absent, faux, expiré, ou trop d'essais. Message prêt à afficher. */
public class PairingRejectedException extends RuntimeException {

    public PairingRejectedException(String message) {
        super(message);
    }
}
