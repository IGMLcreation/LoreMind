package com.loremind.domain.campaigncontext.ports.exceptions;

/**
 * Erreur de domaine : l'import d'un PDF de campagne a échoué (PDF illisible,
 * Brain injoignable, LLM en erreur...).
 */
public class CampaignImportException extends RuntimeException {

    public CampaignImportException(String message) {
        super(message);
    }

    public CampaignImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
