package com.loremind.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Helper d'infrastructure mutualisé entre les clients d'import SSE du Brain
 * ({@link BrainRulesImportClient} et {@link BrainCampaignImportClient}), qui
 * partagent la même mécanique de transport (multipart → flux SSE WebClient) et
 * les mêmes événements transverses (heartbeat / status / chunk_failed).
 * <p>
 * Volontairement instancié en interne par chaque client (et non injecté) pour
 * préserver leurs signatures de constructeur. Le parsing métier des événements
 * {@code start} / {@code progress} / {@code done} reste propre à chaque client.
 */
final class BrainSseImportSupport {

    private final ObjectMapper objectMapper;

    BrainSseImportSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** ByteArrayResource avec nom de fichier : sans nom, l'upload n'est pas vu comme un fichier. */
    ByteArrayResource filePart(byte[] bytes, String filename, String defaultName) {
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return (filename == null || filename.isBlank()) ? defaultName : filename;
            }
        };
    }

    /** Parse le JSON, ou {@code null} si illisible (morceau de flux non-JSON inattendu : ignoré). */
    JsonNode readJson(String data) {
        try {
            return objectMapper.readTree(data);
        } catch (Exception e) {
            return null;
        }
    }

    /** Champ {@code message} du JSON, ou la {@code data} brute si non-JSON / champ absent. */
    String readMessage(String data) {
        JsonNode node = readJson(data);
        if (node != null && node.hasNonNull("message")) {
            return node.get("message").asText();
        }
        return data;
    }

    /** Statut lisible « Morceau x/y ignoré[ : message] » depuis un payload {@code chunk_failed}. */
    String chunkFailedStatus(String data) {
        JsonNode node = readJson(data);
        String msg = node != null && node.hasNonNull("message")
                ? node.get("message").asText() : "";
        int current = node != null ? node.path("current").asInt() : 0;
        int total = node != null ? node.path("total").asInt() : 0;
        return "Morceau " + current + "/" + total + " ignoré"
                + (msg.isEmpty() ? "." : " : " + msg);
    }

    /**
     * Consomme le flux SSE jusqu'au bout ({@code blockLast}) en dispatchant chaque
     * événement vers {@code handler}, et traduit les fins anormales en {@code onError} :
     * <ul>
     *   <li>flux clos sans event {@code done}/{@code error} ({@code terminated[0]==false}) ;</li>
     *   <li>exception de transport (timeout WebClient, connexion coupée, réponse non-2xx)
     *       — la cause réelle (type + message) est exposée dans le message.</li>
     * </ul>
     * {@code errorFactory} fabrique l'exception de domaine à partir d'un message et
     * d'une cause (nullable pour l'interruption silencieuse).
     */
    void runStream(
            Flux<ServerSentEvent<String>> flux,
            long timeoutSeconds,
            boolean[] terminated,
            Consumer<ServerSentEvent<String>> handler,
            Consumer<Throwable> onError,
            BiFunction<String, Throwable, ? extends RuntimeException> errorFactory) {
        try {
            flux
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .doOnNext(handler)
                .blockLast();
            // Flux terminé sans event done/error (ex: connexion coupée) → on signale.
            if (!terminated[0]) {
                onError.accept(errorFactory.apply(
                        "Le flux d'import s'est interrompu avant la fin.", null));
            }
        } catch (Exception e) {
            if (!terminated[0]) {
                String cause = e.getClass().getSimpleName()
                        + (e.getMessage() != null ? " — " + e.getMessage() : "");
                onError.accept(errorFactory.apply(
                        "Erreur lors du streaming d'import depuis le Brain : " + cause, e));
            }
        }
    }
}
