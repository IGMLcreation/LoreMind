package com.loremind.domain.campaigncontext.ports;

/**
 * Échec de génération/improvisation IA d'une table (Brain injoignable, erreur du
 * modèle, réponse inexploitable…). Mappée en HTTP 502 par le contrôleur.
 */
public class RandomTableGenerationException extends RuntimeException {
    public RandomTableGenerationException(String message) {
        super(message);
    }

    public RandomTableGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
