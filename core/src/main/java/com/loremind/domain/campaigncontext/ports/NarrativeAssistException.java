package com.loremind.domain.campaigncontext.ports;

/**
 * Erreur de génération lors de l'étoffage IA d'une entité narrative (Brain injoignable,
 * réponse inexploitable…). Traduite en HTTP 502 par le controller.
 */
public class NarrativeAssistException extends RuntimeException {

    public NarrativeAssistException(String message) {
        super(message);
    }

    public NarrativeAssistException(String message, Throwable cause) {
        super(message, cause);
    }
}
