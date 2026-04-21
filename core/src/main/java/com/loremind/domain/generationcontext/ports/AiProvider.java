package com.loremind.domain.generationcontext.ports;

import com.loremind.domain.generationcontext.GenerationContext;
import com.loremind.domain.generationcontext.GenerationResult;

/**
 * Port de sortie pour la génération IA.
 * <p>
 * Le domaine ne connaît pas l'implémentation (HTTP vers Brain Python,
 * appel direct à OpenAI, mock en test, etc.). Il manipule uniquement
 * cette interface.
 * <p>
 * C'est l'équivalent Java du Protocol LLMProvider côté Python —
 * même pattern hexagonal des deux côtés de la frontière réseau.
 */
public interface AiProvider {

    /**
     * Génère les valeurs des champs d'une Page à partir du contexte fourni.
     *
     * @throws AiProviderException si le fournisseur IA est indisponible,
     *                             renvoie une réponse invalide ou dépasse le timeout.
     */
    GenerationResult generatePage(GenerationContext context) throws AiProviderException;
}
