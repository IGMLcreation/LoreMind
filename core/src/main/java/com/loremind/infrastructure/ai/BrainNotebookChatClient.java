package com.loremind.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.campaigncontext.ports.NotebookChatStreamer;
import com.loremind.domain.campaigncontext.ports.NotebookException;
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
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Adapter de sortie : relaie le chat ANCRÉ (RAG) du Brain via SSE (WebClient).
 * Pattern identique aux imports streamés (cf. BrainRulesImportClient).
 */
@Component
public class BrainNotebookChatClient implements NotebookChatStreamer {

    private static final String PATH = "/chat/notebook/stream";
    private static final String DEEP_PATH = "/chat/notebook/deep/stream";
    private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_STRING_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final long timeoutSeconds;

    public BrainNotebookChatClient(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${brain.base-url}") String baseUrl,
            @Value("${brain.import-timeout-seconds:600}") long timeoutSeconds) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public void stream(
            List<String> sourceIds,
            List<Msg> messages,
            String context,
            boolean deep,
            Consumer<String> onToken,
            Consumer<Progress> onProgress,
            Runnable onDone,
            Consumer<Throwable> onError) {

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source_ids", sourceIds);
        payload.put("messages", messages.stream()
                .map(m -> Map.<String, Object>of("role", m.role(), "content", m.content()))
                .collect(Collectors.toList()));
        payload.put("context", context == null ? "" : context);

        Flux<ServerSentEvent<String>> flux = webClient.post()
                .uri(deep ? DEEP_PATH : PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(payload)
                .retrieve()
                .bodyToFlux(SSE_STRING_TYPE);

        boolean[] terminated = {false};
        try {
            flux
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .doOnNext(sse -> handleEvent(sse, terminated, onToken, onProgress, onDone, onError))
                .blockLast();
            if (!terminated[0]) {
                onDone.run();  // flux terminé sans event done explicite
            }
        } catch (Exception e) {
            if (!terminated[0]) {
                String cause = e.getClass().getSimpleName()
                        + (e.getMessage() != null ? " — " + e.getMessage() : "");
                onError.accept(new NotebookException(
                        "Erreur lors du streaming du chat depuis le Brain : " + cause, e));
            }
        }
    }

    private void handleEvent(
            ServerSentEvent<String> sse,
            boolean[] terminated,
            Consumer<String> onToken,
            Consumer<Progress> onProgress,
            Runnable onDone,
            Consumer<Throwable> onError) {

        String event = sse.event();
        String data = sse.data() == null ? "" : sse.data();
        if ("token".equals(event)) {
            String token = readField(data, "token");
            if (token != null && !token.isEmpty()) onToken.accept(token);
        } else if ("progress".equals(event)) {
            onProgress.accept(new Progress(readInt(data, "current"), readInt(data, "total")));
        } else if ("done".equals(event)) {
            terminated[0] = true;
            onDone.run();
        } else if ("error".equals(event)) {
            terminated[0] = true;
            onError.accept(new NotebookException("Le Brain a signalé une erreur : " + readMessage(data)));
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
