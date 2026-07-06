package com.loremind.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.campaigncontext.ports.NotebookChatStreamer;
import com.loremind.domain.campaigncontext.ports.exceptions.NotebookException;
import com.loremind.infrastructure.web.config.UserLanguageHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter de sortie : relaie le chat ANCRÉ (RAG) du Brain via SSE (WebClient).
 * Pattern identique aux imports streamés (cf. BrainRulesImportClient).
 */
@Component
public class BrainNotebookChatClient implements NotebookChatStreamer {

    private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_STRING_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final long timeoutSeconds;
    // Routes du Brain surchargeables par config (défauts = contrat d'API actuel).
    private final String chatPath;
    private final String deepChatPath;

    public BrainNotebookChatClient(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${brain.base-url}") String baseUrl,
            @Value("${brain.import-timeout-seconds:600}") long timeoutSeconds,
            @Value("${brain.paths.notebook-chat:/chat/notebook/stream}") String chatPath,
            @Value("${brain.paths.notebook-chat-deep:/chat/notebook/deep/stream}") String deepChatPath) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.timeoutSeconds = timeoutSeconds;
        this.chatPath = chatPath;
        this.deepChatPath = deepChatPath;
    }

    @Override
    public void stream(
            List<String> sourceIds,
            List<Msg> messages,
            String context,
            boolean deep,
            Callbacks callbacks) {

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source_ids", sourceIds);
        payload.put("messages", messages.stream()
                .map(m -> Map.<String, Object>of("role", m.role(), "content", m.content()))
                .toList());
        payload.put("context", context == null ? "" : context);

        Flux<ServerSentEvent<String>> flux = webClient.post()
                .uri(deep ? deepChatPath : chatPath)
                .header(UserLanguageHolder.HEADER, UserLanguageHolder.get())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(payload)
                .retrieve()
                .bodyToFlux(SSE_STRING_TYPE);

        boolean[] terminated = {false};
        try {
            flux
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .doOnNext(sse -> handleEvent(sse, terminated, callbacks))
                .blockLast();
            if (!terminated[0]) {
                callbacks.onDone().run();  // flux terminé sans event done explicite
            }
        } catch (Exception e) {
            if (!terminated[0]) {
                String cause = e.getClass().getSimpleName()
                        + (e.getMessage() != null ? " — " + e.getMessage() : "");
                callbacks.onError().accept(new NotebookException(
                        "Erreur lors du streaming du chat depuis le Brain : " + cause, e));
            }
        }
    }

    private void handleEvent(ServerSentEvent<String> sse, boolean[] terminated, Callbacks callbacks) {
        String event = sse.event();
        String data = sse.data() == null ? "" : sse.data();
        if ("token".equals(event)) {
            String token = readField(data, "token");
            if (token != null && !token.isEmpty()) callbacks.onToken().accept(token);
        } else if ("sources".equals(event)) {
            // Passages utilisés par le RAG : relayés tels quels (JSON brut) — le
            // Core n'a pas besoin de les comprendre, seulement de les transmettre.
            callbacks.onSourcesJson().accept(data);
        } else if ("progress".equals(event)) {
            callbacks.onProgress().accept(new Progress(readInt(data, "current"), readInt(data, "total")));
        } else if ("done".equals(event)) {
            terminated[0] = true;
            callbacks.onDone().run();
        } else if ("error".equals(event)) {
            terminated[0] = true;
            callbacks.onError().accept(new NotebookException("Le Brain a signalé une erreur : " + readMessage(data)));
        }
    }

    private int readInt(String data, String field) {
        try {
            JsonNode node = objectMapper.readTree(data);
            return node.hasNonNull(field) ? node.get(field).asInt() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private String readField(String data, String field) {
        try {
            JsonNode node = objectMapper.readTree(data);
            return node.hasNonNull(field) ? node.get(field).asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String readMessage(String data) {
        String msg = readField(data, "message");
        return msg != null ? msg : data;
    }
}
