package com.loremind.domain.campaigncontext.ports.exceptions;

/**
 * Échec d'indexation/chat d'un notebook (Brain injoignable, erreur du modèle…).
 * Mappée en HTTP 502 par le contrôleur.
 */
public class NotebookException extends RuntimeException {
    public NotebookException(String message) {
        super(message);
    }

    public NotebookException(String message, Throwable cause) {
        super(message, cause);
    }
}
