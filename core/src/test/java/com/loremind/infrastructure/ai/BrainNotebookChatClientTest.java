package com.loremind.infrastructure.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.campaigncontext.ports.NotebookChatStreamer.Msg;
import com.loremind.domain.campaigncontext.ports.NotebookChatStreamer.Progress;
import com.loremind.domain.campaigncontext.ports.exceptions.NotebookException;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires PURS (JUnit 5 + Mockito-less) pour {@link BrainNotebookChatClient}.
 * On injecte un WebClient.Builder dont l'ExchangeFunction renvoie un corps SSE canned :
 * aucun réseau n'est sollicité.
 */
class BrainNotebookChatClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Construit un client dont le WebClient renvoie le corps SSE fourni. */
    private BrainNotebookChatClient clientReturning(String sseBody) {
        ExchangeFunction ef = req -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                        .body(sseBody)
                        .build());
        WebClient.Builder builder = WebClient.builder().exchangeFunction(ef);
        return new BrainNotebookChatClient(builder, MAPPER, "http://brain", 30, "/chat/notebook/stream", "/chat/notebook/deep/stream");
    }

    /** Construit un client dont le transport échoue immédiatement. */
    private BrainNotebookChatClient clientFailingWith(Throwable boom) {
        ExchangeFunction ef = req -> Mono.error(boom);
        WebClient.Builder builder = WebClient.builder().exchangeFunction(ef);
        return new BrainNotebookChatClient(builder, MAPPER, "http://brain", 30, "/chat/notebook/stream", "/chat/notebook/deep/stream");
    }

    /** Collecteur réutilisable pour les callbacks. */
    private static final class Collector {
        final AtomicReference<String> sources = new AtomicReference<>();
        final StringBuilder tokens = new StringBuilder();
        final List<Progress> progresses = new ArrayList<>();
        final AtomicBoolean done = new AtomicBoolean(false);
        final AtomicReference<Throwable> error = new AtomicReference<>();

        void invoke(BrainNotebookChatClient client, boolean deep) {
            client.stream(
                    List.of("s1", "s2"),
                    List.of(new Msg("user", "Bonjour")),
                    "ctx",
                    deep,
                    new BrainNotebookChatClient.Callbacks(
                            sources::set,
                            tokens::append,
                            progresses::add,
                            () -> done.set(true),
                            error::set));
        }
    }

    @Test
    void streame_tous_les_events_token_sources_progress_done() {
        // SSE déclenchant chaque branche de handleEvent : sources, progress, token, done.
        String sse = """
                event:sources
                data:{"passages":[1,2]}

                event:progress
                data:{"current":2,"total":5}

                event:token
                data:{"token":"Salut"}

                event:token
                data:{"token":" toi"}

                event:done
                data:{}

                """;
        Collector c = new Collector();
        c.invoke(clientReturning(sse), true);

        assertEquals("{\"passages\":[1,2]}", c.sources.get(), "JSON sources relayé brut");
        assertEquals("Salut toi", c.tokens.toString(), "tokens concaténés dans l'ordre");
        assertEquals(1, c.progresses.size());
        assertEquals(2, c.progresses.get(0).current());
        assertEquals(5, c.progresses.get(0).total());
        assertTrue(c.done.get(), "onDone appelé via event done");
        assertNull(c.error.get());
    }

    @Test
    void token_vide_ou_absent_ignore() {
        // token vide -> non émis ; token absent -> readField null -> non émis.
        String sse = """
                event:token
                data:{"token":""}

                event:token
                data:{"foo":"bar"}

                event:token
                data:{"token":"X"}

                event:done
                data:{}

                """;
        Collector c = new Collector();
        c.invoke(clientReturning(sse), false);

        assertEquals("X", c.tokens.toString(), "seul le token non vide est émis");
        assertTrue(c.done.get());
    }

    @Test
    void progress_avec_json_invalide_donne_zero() {
        // data non-JSON -> readInt catch -> 0/0 (couvre la branche d'exception).
        String sse = """
                event:progress
                data:pas-du-json

                event:done
                data:{}

                """;
        Collector c = new Collector();
        c.invoke(clientReturning(sse), true);

        assertEquals(1, c.progresses.size());
        assertEquals(0, c.progresses.get(0).current());
        assertEquals(0, c.progresses.get(0).total());
    }

    @Test
    void event_error_appelle_onError_avec_NotebookException() {
        String sse = """
                event:token
                data:{"token":"avant"}

                event:error
                data:{"message":"oups modèle"}

                """;
        Collector c = new Collector();
        c.invoke(clientReturning(sse), false);

        assertNotNull(c.error.get());
        assertInstanceOf(NotebookException.class, c.error.get());
        assertTrue(c.error.get().getMessage().contains("oups modèle"));
        assertFalse(c.done.get(), "onDone non appelé après un error terminal");
    }

    @Test
    void event_error_sans_message_relaie_data_brut() {
        // readMessage : pas de champ message -> renvoie data brut.
        String sse = "event:error\ndata:erreur-brute\n\n";
        Collector c = new Collector();
        c.invoke(clientReturning(sse), false);

        assertNotNull(c.error.get());
        assertTrue(c.error.get().getMessage().contains("erreur-brute"));
    }

    @Test
    void flux_termine_sans_done_appelle_onDone() {
        // Aucun event done/error -> terminated reste false -> onDone() de secours.
        String sse = "event:token\ndata:{\"token\":\"fin\"}\n\n";
        Collector c = new Collector();
        c.invoke(clientReturning(sse), false);

        assertEquals("fin", c.tokens.toString());
        assertTrue(c.done.get(), "onDone de secours appelé pour flux clos sans done");
        assertNull(c.error.get());
    }

    @Test
    void erreur_transport_traduite_en_NotebookException() {
        Collector c = new Collector();
        c.invoke(clientFailingWith(new RuntimeException("boom")), false);

        assertNotNull(c.error.get());
        assertInstanceOf(NotebookException.class, c.error.get());
        assertTrue(c.error.get().getMessage().contains("boom"));
        assertFalse(c.done.get());
    }

    @Test
    void context_null_est_accepte() {
        // Couvre la branche context == null -> "" lors de la construction du payload.
        String sse = "event:done\ndata:{}\n\n";
        ExchangeFunction ef = req -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                        .body(sse)
                        .build());
        BrainNotebookChatClient client =
                new BrainNotebookChatClient(WebClient.builder().exchangeFunction(ef), MAPPER, "http://brain", 30, "/chat/notebook/stream", "/chat/notebook/deep/stream");

        AtomicBoolean done = new AtomicBoolean(false);
        client.stream(
                List.of("s1"),
                List.of(new Msg("user", "hi")),
                null,
                false,
                new BrainNotebookChatClient.Callbacks(
                        json -> {}, tok -> {}, p -> {}, () -> done.set(true), err -> {}));

        assertTrue(done.get());
    }
}
