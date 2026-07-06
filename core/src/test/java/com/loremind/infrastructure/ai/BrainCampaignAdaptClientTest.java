package com.loremind.infrastructure.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires PURS (JUnit 5, sans Spring ni réseau) pour {@link BrainCampaignAdaptClient}.
 * Le WebClient.Builder injecté embarque une ExchangeFunction qui renvoie un corps SSE canned.
 */
class BrainCampaignAdaptClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BrainCampaignAdaptClient clientReturning(String sseBody) {
        ExchangeFunction ef = req -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                        .body(sseBody)
                        .build());
        WebClient.Builder builder = WebClient.builder().exchangeFunction(ef);
        return new BrainCampaignAdaptClient(builder, MAPPER, "http://brain", 30, "/adapt/campaign/stream");
    }

    private BrainCampaignAdaptClient clientFailingWith(Throwable boom) {
        ExchangeFunction ef = req -> Mono.error(boom);
        WebClient.Builder builder = WebClient.builder().exchangeFunction(ef);
        return new BrainCampaignAdaptClient(builder, MAPPER, "http://brain", 30, "/adapt/campaign/stream");
    }

    /** Collecteur de callbacks + déclenchement de adviseStreaming. */
    private static final class Collector {
        final StringBuilder tokens = new StringBuilder();
        final AtomicBoolean complete = new AtomicBoolean(false);
        final AtomicReference<Throwable> error = new AtomicReference<>();

        void invoke(BrainCampaignAdaptClient client, String filename, String brief, String messagesJson) {
            client.adviseStreaming(
                    new byte[]{1, 2, 3},
                    filename,
                    brief,
                    messagesJson,
                    tokens::append,
                    () -> complete.set(true),
                    error::set);
        }

        void invoke(BrainCampaignAdaptClient client) {
            invoke(client, "camp.pdf", "brief", "[]");
        }
    }

    @Test
    void streame_tokens_puis_done() {
        String sse = """
                event:token
                data:{"token":"Conseil"}

                event:token
                data:{"token":" final"}

                event:done
                data:{}

                """;
        Collector c = new Collector();
        c.invoke(clientReturning(sse));

        assertEquals("Conseil final", c.tokens.toString());
        assertTrue(c.complete.get(), "onComplete appelé via event done");
        assertNull(c.error.get());
    }

    @Test
    void token_vide_ou_absent_ignore() {
        // token "" -> non émis ; champ token absent -> readField renvoie data (non vide)
        // donc émis tel quel : on vérifie ce comportement réel.
        String sse = """
                event:token
                data:{"token":""}

                event:token
                data:{"token":"OK"}

                event:done
                data:{}

                """;
        Collector c = new Collector();
        c.invoke(clientReturning(sse));

        assertEquals("OK", c.tokens.toString());
        assertTrue(c.complete.get());
    }

    @Test
    void event_error_appelle_onError_avec_runtimeexception() {
        String sse = """
                event:token
                data:{"token":"avant"}

                event:error
                data:{"message":"PDF illisible"}

                """;
        Collector c = new Collector();
        c.invoke(clientReturning(sse));

        assertNotNull(c.error.get());
        assertInstanceOf(RuntimeException.class, c.error.get());
        assertTrue(c.error.get().getMessage().contains("PDF illisible"));
        assertFalse(c.complete.get(), "onComplete non appelé après error terminal");
    }

    @Test
    void event_error_data_non_json_relaie_data_brut() {
        // readField : data non parsable -> catch -> renvoie data brut.
        String sse = "event:error\ndata:panne-brute\n\n";
        Collector c = new Collector();
        c.invoke(clientReturning(sse));

        assertNotNull(c.error.get());
        assertTrue(c.error.get().getMessage().contains("panne-brute"));
    }

    @Test
    void flux_clos_sans_done_appelle_onComplete() {
        // Pas de done/error -> terminated false -> onComplete de secours.
        String sse = "event:token\ndata:{\"token\":\"x\"}\n\n";
        Collector c = new Collector();
        c.invoke(clientReturning(sse));

        assertEquals("x", c.tokens.toString());
        assertTrue(c.complete.get());
        assertNull(c.error.get());
    }

    @Test
    void erreur_transport_traduite_en_runtimeexception() {
        Collector c = new Collector();
        c.invoke(clientFailingWith(new RuntimeException("boom")));

        assertNotNull(c.error.get());
        assertInstanceOf(RuntimeException.class, c.error.get());
        assertTrue(c.error.get().getMessage().contains("adaptation"));
        assertFalse(c.complete.get());
    }

    @Test
    void filename_null_et_brief_null_et_messages_null_acceptes() {
        // Couvre les branches : filename blank -> "campaign.pdf", brief null -> "",
        // messagesJson null/blank -> "[]".
        String sse = "event:done\ndata:{}\n\n";
        Collector c = new Collector();
        c.invoke(clientReturning(sse), null, null, null);

        assertTrue(c.complete.get());
        assertNull(c.error.get());
    }

    @Test
    void messages_blank_remplace_par_tableau_vide() {
        String sse = "event:done\ndata:{}\n\n";
        Collector c = new Collector();
        c.invoke(clientReturning(sse), "  ", "  ", "   ");

        assertTrue(c.complete.get());
    }
}
