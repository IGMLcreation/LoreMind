package com.loremind.domain.campaigncontext.ports;

/**
 * Échec de génération IA d'un catalogue d'objets (Brain injoignable, erreur du
 * modèle, réponse inexploitable…). Mappée en HTTP 502 par le contrôleur.
 */
public class ItemCatalogGenerationException extends RuntimeException {
    public ItemCatalogGenerationException(String message) {
        super(message);
    }

    public ItemCatalogGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
