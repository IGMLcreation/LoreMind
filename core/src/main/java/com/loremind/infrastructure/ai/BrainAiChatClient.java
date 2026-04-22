package com.loremind.infrastructure.ai;

import com.loremind.domain.generationcontext.ChatRequest;
import com.loremind.domain.generationcontext.ChatUsage;
import com.loremind.domain.generationcontext.ports.AiChatProvider;
import com.loremind.domain.generationcontext.ports.AiProviderException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Adapter de sortie (Architecture Hexagonale) : implémente AiChatProvider
 * en appelant le Brain Python via WebClient + SSE (Server-Sent Events).
 * <p>
 * Responsabilités (après extraction) :
 *  1. Transport HTTP + consommation du flux SSE.
 *  2. Dispatch des évènements SSE (data / done / error / usage).
 *  3. Traduction des erreurs techniques en AiProviderException.
 * <p>
 * Les responsabilités auxiliaires sont déléguées :
 *  - Construction du payload JSON : {@link BrainChatPayloadBuilder}.
 *  - Parsing des payloads SSE        : {@link BrainSseParser}.
 * <p>
 * Le domaine ne voit JAMAIS WebClient, Flux, ni la moindre URL.
 */
@Component
public class BrainAiChatClient implements AiChatProvider {

    private static final String CHAT_STREAM_PATH = "/chat/stream";
    private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_STRING_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient webClient;
    private final BrainChatPayloadBuilder payloadBuilder;
    private final BrainSseParser sseParser;

    public BrainAiChatClient(
            WebClient.Builder builder,
            @Value("${brain.base-url}") String baseUrl,
            BrainChatPayloadBuilder payloadBuilder,
            BrainSseParser sseParser) {
        this.webClient = builder.baseUrl(baseUrl).build();
        this.payloadBuilder = payloadBuilder;
        this.sseParser = sseParser;
    }

    @Override
    public void streamChat(
            ChatRequest request,
            Consumer<ChatUsage> onUsage,
            Consumer<String> onToken,
            Runnable onComplete,
            Consumer<Throwable> onError) {

        Map<String, Object> payload = payloadBuilder.build(request);

        Flux<ServerSentEvent<String>> flux = webClient.post()
                .uri(CHAT_STREAM_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(payload)
                .retrieve()
                .bodyToFlux(SSE_STRING_TYPE);

        try {
            // blockLast() : transforme le flux réactif en appel bloquant conforme
            // au contrat synchrone du port. L'appelant choisit le thread.
            flux
                .timeout(Duration.ofSeconds(120))
                .doOnNext(sse -> handleEvent(sse, onUsage, onToken, onError))
                .blockLast();
            onComplete.run();
        } catch (Exception e) {
            onError.accept(new AiProviderException(
                    "Erreur lors du streaming chat depuis le Brain.", e));
        }
    }

    /** Dispatch selon le type d'évènement SSE (data par défaut, done, error, usage). */
    private void handleEvent(
            ServerSentEvent<String> sse,
            Consumer<ChatUsage> onUsage,
            Consumer<String> onToken,
            Consumer<Throwable> onError) {
        String event = sse.event();  // null si pas d'event: xxx -> data par défaut
        String data = sse.data();

        if ("error".equals(event)) {
            onError.accept(new AiProviderException(
                    "Le Brain a signalé une erreur : " + data));
            return;
        }
        if ("done".equals(event)) {
            return; // fin gérée par blockLast + onComplete
        }
        if ("usage".equals(event)) {
            ChatUsage usage = sseParser.parseUsage(data);
            if (usage != null) onUsage.accept(usage);
            return;
        }
        // Défaut : évènement data avec JSON {"token":"..."}.
        String token = sseParser.parseToken(data);
        if (token != null && !token.isEmpty()) {
            onToken.accept(token);
        }
    }
}
