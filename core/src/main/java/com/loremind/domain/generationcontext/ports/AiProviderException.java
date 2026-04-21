package com.loremind.domain.generationcontext.ports;

/**
 * Exception de domaine signalant un échec du fournisseur IA.
 * <p>
 * Équivalent Java de LLMProviderError (Python). Hérite de RuntimeException
 * pour rester cohérent avec le reste du code (pas d'exceptions checked
 * qui polluent les signatures de méthodes).
 * <p>
 * L'Adapter (BrainAiClient) traduira toute erreur technique (timeout,
 * 5xx, JSON invalide) en AiProviderException avant de la propager.
 */
public class AiProviderException extends RuntimeException {

    public AiProviderException(String message) {
        super(message);
    }

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
