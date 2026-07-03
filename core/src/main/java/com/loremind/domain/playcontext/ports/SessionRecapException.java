package com.loremind.domain.playcontext.ports;

/**
 * Erreur de génération du récap de séance (Brain injoignable, réponse vide…).
 * Traduite en HTTP 502 par le controller.
 */
public class SessionRecapException extends RuntimeException {

    public SessionRecapException(String message) {
        super(message);
    }

    public SessionRecapException(String message, Throwable cause) {
        super(message, cause);
    }
}
