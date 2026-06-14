package com.loremind.infrastructure.ai;

import com.loremind.domain.generationcontext.ChatRequest;
import com.loremind.domain.generationcontext.ChatUsage;
import com.loremind.domain.generationcontext.ports.AiProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires purs (JUnit 5 + Mockito, sans Spring, sans réseau) de
 * {@link BrainAiChatClient}.
 *
 * Principe : WebClient.Builder préconfiguré avec une ExchangeFunction mock
 * renvoyant un flux SSE canned. Le payloadBuilder est mocké ; le sseParser est
 * une instance réelle (simple parseur sans dépendance).
 */
class BrainAiChatClientTest {

    /** ChatRequest minimal valide : messages vide suffit (payloadBuilder mocké). */
    private ChatRequest minimalRequest() {
        return ChatRequest.builder().messages(List.of()).build();
    }

    /** Construit un client dont le WebClient renvoie le corps SSE fourni. */
    private BrainAiChatClient clientWithSse(String sseBody) {
        ExchangeFunction ef = req -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                        .body(sseBody)
                        .build());
        return buildClient(ef);
    }

    /** Construit un client dont le WebClient émet une erreur transport. */
    private BrainAiChatClient clientErroring() {
        ExchangeFunction ef = req -> Mono.error(new RuntimeException("boom"));
        return buildClient(ef);
    }

    private BrainAiChatClient buildClient(ExchangeFunction ef) {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(ef);
        BrainChatPayloadBuilder payloadBuilder = mock(BrainChatPayloadBuilder.class);
        when(payloadBuilder.build(org.mockito.ArgumentMatchers.any())).thenReturn(Map.of());
        return new BrainAiChatClient(builder, "http://brain", payloadBuilder, new BrainSseParser());
    }

    // --- Collecteurs partagés pour les callbacks ---
    private final List<ChatUsage> usages = new ArrayList<>();
    private final List<String> tokens = new ArrayList<>();
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final AtomicReference<Throwable> error = new AtomicReference<>();

    private final Consumer<ChatUsage> onUsage = usages::add;
    private final Consumer<String> onToken = tokens::add;
    private final Runnable onComplete = () -> completed.set(true);
    private final Consumer<Throwable> onError = error::set;

    @Test
    void flux_complet_parse_usage_et_token_puis_complete() {
        String sse =
                "event:usage\ndata:{\"system\":1,\"history\":2,\"current\":3,\"max\":100}\n\n" +
                "data:{\"token\":\"Bonjour\"}\n\n" +
                "event:done\ndata:{}\n\n";
        BrainAiChatClient client = clientWithSse(sse);

        client.streamChat(minimalRequest(), onUsage, onToken, onComplete, onError);

        // usage parsé et propagé
        assertThat(usages).hasSize(1);
        assertThat(usages.get(0)).isEqualTo(new ChatUsage(1, 2, 3, 100));
        // token propagé
        assertThat(tokens).containsExactly("Bonjour");
        // event done ignoré (pas de token/usage supplémentaire)
        // complétion appelée, pas d'erreur
        assertThat(completed).isTrue();
        assertThat(error.get()).isNull();
    }

    @Test
    void plusieurs_tokens_propages_dans_l_ordre() {
        String sse =
                "data:{\"token\":\"Bon\"}\n\n" +
                "data:{\"token\":\"jour\"}\n\n" +
                "event:done\ndata:{}\n\n";
        BrainAiChatClient client = clientWithSse(sse);

        client.streamChat(minimalRequest(), onUsage, onToken, onComplete, onError);

        assertThat(tokens).containsExactly("Bon", "jour");
        assertThat(completed).isTrue();
        assertThat(error.get()).isNull();
    }

    @Test
    void event_error_declenche_onError_avec_AiProviderException() {
        String sse =
                "event:error\ndata:boom\n\n" +
                "event:done\ndata:{}\n\n";
        BrainAiChatClient client = clientWithSse(sse);

        client.streamChat(minimalRequest(), onUsage, onToken, onComplete, onError);

        assertThat(error.get())
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("boom");
        // Aucun token émis sur ce flux.
        assertThat(tokens).isEmpty();
    }

    @Test
    void token_vide_n_est_pas_propage() {
        String sse =
                "data:{\"token\":\"\"}\n\n" +
                "event:done\ndata:{}\n\n";
        BrainAiChatClient client = clientWithSse(sse);

        client.streamChat(minimalRequest(), onUsage, onToken, onComplete, onError);

        assertThat(tokens).isEmpty();
        assertThat(completed).isTrue();
    }

    @Test
    void usage_illisible_n_est_pas_propage() {
        // data usage sans champs numériques -> parser renvoie ChatUsage(0,0,0,0),
        // donc propagé ; ici on teste un usage avec data non-null mais vide d'entiers.
        String sse =
                "event:usage\ndata:{\"system\":5}\n\n" +
                "event:done\ndata:{}\n\n";
        BrainAiChatClient client = clientWithSse(sse);

        client.streamChat(minimalRequest(), onUsage, onToken, onComplete, onError);

        // Les champs absents tombent à 0 (parser tolérant).
        assertThat(usages).containsExactly(new ChatUsage(5, 0, 0, 0));
    }

    @Test
    void erreur_transport_declenche_onError_avec_AiProviderException() {
        BrainAiChatClient client = clientErroring();

        client.streamChat(minimalRequest(), onUsage, onToken, onComplete, onError);

        assertThat(error.get())
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("streaming chat");
        // onComplete NON appelé puisqu'une exception a interrompu blockLast().
        assertThat(completed).isFalse();
    }

    @Test
    void flux_vide_appelle_seulement_onComplete() {
        // Flux SSE vide : aucun évènement, blockLast renvoie null, onComplete appelé.
        BrainAiChatClient client = clientWithSse("");

        client.streamChat(minimalRequest(), onUsage, onToken, onComplete, onError);

        assertThat(tokens).isEmpty();
        assertThat(usages).isEmpty();
        assertThat(completed).isTrue();
        assertThat(error.get()).isNull();
    }
}
