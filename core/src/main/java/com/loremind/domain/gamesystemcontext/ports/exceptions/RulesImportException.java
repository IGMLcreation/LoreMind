package com.loremind.domain.gamesystemcontext.ports.exceptions;

/**
 * Erreur de domaine : l'import d'un PDF de règles a échoué (PDF illisible,
 * Brain injoignable, LLM en erreur...). Les couches supérieures la traduisent
 * en réponse HTTP sans connaître l'adapter concret.
 */
public class RulesImportException extends RuntimeException {

    public RulesImportException(String message) {
        super(message);
    }

    public RulesImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
