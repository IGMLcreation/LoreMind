package com.loremind.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.campaigncontext.ports.CampaignPdfAdvisor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * Adapter de sortie : implémente {@link CampaignPdfAdvisor} via WebClient + SSE
 * (POST /adapt/campaign/stream). Envoie le PDF (multipart) + le brief de campagne,
 * relaie les tokens de conseil au fil de l'eau.
 */
@Component
public class BrainCampaignAdaptClient implements CampaignPdfAdvisor {

    private static final String ADAPT_PATH = "/adapt/campaign/stream";
    private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_STRING_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final long timeoutSeconds;

    public BrainCampaignAdaptClient(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${brain.base-url}") String baseUrl,
            @Value("${brain.import-timeout-seconds:600}") long timeoutSeconds) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public void adviseStreaming(
            byte[] pdfBytes,
            String filename,
            String brief,
            String messagesJson,
            Consumer<String> onToken,
            Runnable onComplete,
            Consumer<Throwable> onError) {

        MultipartBodyBuilder parts = new MultipartBodyBuilder();
        parts.part("file", new ByteArrayResource(pdfBytes) {
            @Override
            public String getFilename() {
                return (filename == null || filename.isBlank()) ? "campaign.pdf" : filename;
            }
        });
        parts.part("brief", brief == null ? "" : brief);
        parts.part("messages", (messagesJson == null || messagesJson.isBlank()) ? "[]" : messagesJson);

        Flux<ServerSentEvent<String>> flux = webClient.post()
                .uri(ADAPT_PATH)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .body(BodyInserters.fromMultipartData(parts.build()))
                .retrieve()
                .bodyToFlux(SSE_STRING_TYPE);

        boolean[] terminated = {false};
        try {
            flux
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .doOnNext(sse -> handleEvent(sse, terminated, onToken, onComplete, onError))
                .blockLast();
            // Flux clos sans 'done' explicite (ex: coupure) → on complète quand même.
            if (!terminated[0]) onComplete.run();
        } catch (Exception e) {
            if (!terminated[0]) {
                onError.accept(new RuntimeException(
                        "Erreur lors du streaming d'adaptation depuis le Brain.", e));
            }
        }
    }

    private void handleEvent(
            ServerSentEvent<String> sse,
            boolean[] terminated,
            Consumer<String> onToken,
            Runnable onComplete,
            Consumer<Throwable> onError) {

        String event = sse.event();
        String data = sse.data() == null ? "" : sse.data();

        if ("error".equals(event)) {
            terminated[0] = true;
            onError.accept(new RuntimeException("Le Brain a signalé une erreur : " + readField(data, "message")));
        } else if ("done".equals(event)) {
            terminated[0] = true;
            onComplete.run();
        } else if ("token".equals(event)) {
            String token = readField(data, "token");
            if (token != null && !token.isEmpty()) onToken.accept(token);
        }
    }

    private String readField(String data, String field) {
        try {
            JsonNode node = objectMapper.readTree(data);
            return node.hasNonNull(field) ? node.get(field).asText() : data;
        } catch (Exception e) {
            return data;
        }
    }
}
