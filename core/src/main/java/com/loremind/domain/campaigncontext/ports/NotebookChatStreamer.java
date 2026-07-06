package com.loremind.domain.campaigncontext.ports;

import java.util.List;
import java.util.function.Consumer;

/**
 * Port de sortie : chat ANCRÉ (RAG) sur les sources d'un notebook, streamé.
 * Le Brain récupère les passages pertinents puis streame la réponse token par token.
 */
public interface NotebookChatStreamer {

    /** Un message de la conversation transmis au Brain. */
    record Msg(String role, String content) {}

    /** Avancement de l'analyse approfondie (lecture du document par lots). */
    record Progress(int current, int total) {}

    /**
     * Callbacks du stream, invoqués au fil de l'eau : {@code onSourcesJson} UNE fois
     * avant le premier token (JSON brut des passages utilisés — transparence UI),
     * {@code onToken} par fragment, {@code onProgress} (mode approfondi uniquement)
     * pendant la lecture du document, {@code onDone} à la fin, {@code onError} en
     * cas d'échec. Regroupés en un seul objet (Argument Object) : ils voyagent
     * toujours ensemble entre ce port, son adaptateur et le controller SSE.
     */
    record Callbacks(
            Consumer<String> onSourcesJson,
            Consumer<String> onToken,
            Consumer<Progress> onProgress,
            Runnable onDone,
            Consumer<Throwable> onError) {}

    /**
     * Streame la réponse ancrée sur les sources.
     *
     * @param deep true = analyse approfondie (map-reduce sur tout le document) ;
     *             false = chat RAG (top-k).
     */
    void stream(
            List<String> sourceIds,
            List<Msg> messages,
            String context,
            boolean deep,
            Callbacks callbacks);
}
