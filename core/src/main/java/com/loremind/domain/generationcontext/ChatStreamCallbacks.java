package com.loremind.domain.generationcontext;

import java.util.function.Consumer;

/**
 * Callbacks du streaming chat IA, regroupés en un seul objet (Argument Object) :
 * ils voyagent toujours ensemble, dans le même ordre, entre le port
 * {@link com.loremind.domain.generationcontext.ports.AiChatProvider}, ses use
 * cases appelants et le controller SSE.
 *
 * @param onUsage    invoqué une fois au début du stream avec le bilan d'occupation
 *                   de la fenêtre de contexte (tokens system / history / current /
 *                   max). Peut ne jamais être invoqué si le provider ne supporte
 *                   pas le comptage.
 * @param onToken    invoqué à chaque token reçu du LLM (peut être appelé de
 *                   nombreuses fois)
 * @param onComplete invoqué une fois le stream terminé avec succès
 * @param onError    invoqué en cas d'erreur (Brain injoignable, timeout, réponse
 *                   invalide). Exclusif avec onComplete.
 */
public record ChatStreamCallbacks(
        Consumer<ChatUsage> onUsage,
        Consumer<String> onToken,
        Runnable onComplete,
        Consumer<Throwable> onError) {}
