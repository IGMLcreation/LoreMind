package com.loremind.domain.generationcontext.ports;

import com.loremind.domain.generationcontext.ChatRequest;

import java.util.function.Consumer;

/**
 * Port de sortie pour le chat streamé avec un LLM.
 * <p>
 * Distinct de AiProvider (one-shot) par Interface Segregation Principle :
 * le streaming est une capacité séparée avec un contrat propre. Un même
 * adapter concret peut satisfaire les deux ports s'il le souhaite.
 * <p>
 * API par callbacks (plutôt que Flux/Stream) pour garder le domaine libre
 * de toute dépendance à Reactor. Les couches supérieures (controller SSE)
 * s'adaptent naturellement à ce style.
 */
public interface AiChatProvider {

    /**
     * Streame la réponse du LLM en invoquant les callbacks au fil de l'eau.
     * <p>
     * Cette méthode est bloquante : elle ne rend la main qu'après la fin
     * du stream (appel à onComplete ou onError). L'appelant est responsable
     * de l'exécuter dans un thread adapté (ex: thread dédié à la requête
     * HTTP côté controller SSE).
     *
     * @param request    messages + contexte Lore
     * @param onToken    invoqué à chaque token reçu du LLM (peut être appelé
     *                   de nombreuses fois)
     * @param onComplete invoqué une fois le stream terminé avec succès
     * @param onError    invoqué en cas d'erreur (Brain injoignable, timeout,
     *                   réponse invalide). Exclusif avec onComplete.
     */
    void streamChat(
            ChatRequest request,
            Consumer<String> onToken,
            Runnable onComplete,
            Consumer<Throwable> onError
    );
}
