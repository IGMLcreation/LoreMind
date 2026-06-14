package com.loremind.infrastructure.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.gamesystemcontext.RulesImportProgress;
import com.loremind.domain.gamesystemcontext.RulesImportResult;
import com.loremind.domain.gamesystemcontext.ports.RulesImportException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires purs (JUnit 5 + Mockito, sans Spring/réseau) de
 * {@link BrainRulesImportClient} :
 *  - one-shot via RestTemplate (importRules) ;
 *  - streaming SSE via WebClient + ExchangeFunction (importRulesStreaming).
 * Couvre aussi indirectement les getters du DTO {@link BrainRulesImportResponse}.
 */
class BrainRulesImportClientTest {

    private static final String BASE_URL = "http://brain";

    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        objectMapper = new ObjectMapper();
    }

    /** Construit le client avec un WebClient câblé sur l'ExchangeFunction fournie. */
    private BrainRulesImportClient client(ExchangeFunction ef) {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(ef);
        return new BrainRulesImportClient(restTemplate, builder, objectMapper, BASE_URL, 30);
    }

    /** Client one-shot : l'ExchangeFunction n'est jamais utilisée. */
    private BrainRulesImportClient oneShotClient() {
        return client(req -> Mono.empty());
    }

    // --- One-shot (RestTemplate) --------------------------------------------

    @Test
    void importRules_succes_retourneResultatEtCouvreGettersDuDto() {
        BrainRulesImportResponse wire = new BrainRulesImportResponse();
        wire.setSections(Map.of("Combat", "## Combat"));
        wire.setPageCount(12);
        wire.setOcrPageCount(3);
        // Couvre aussi les getters Lombok du DTO.
        assertEquals(Map.of("Combat", "## Combat"), wire.getSections());
        assertEquals(12, wire.getPageCount());
        assertEquals(3, wire.getOcrPageCount());

        when(restTemplate.postForObject(anyString(), any(), eq(BrainRulesImportResponse.class)))
                .thenReturn(wire);

        RulesImportResult result = oneShotClient().importRules(new byte[]{1, 2, 3}, "regles.pdf");

        assertEquals(Map.of("Combat", "## Combat"), result.sections());
        assertEquals(12, result.pageCount());
        assertEquals(3, result.ocrPageCount());
    }

    @Test
    void importRules_filenameNull_utiliseNomParDefaut() {
        BrainRulesImportResponse wire = new BrainRulesImportResponse();
        wire.setSections(Map.of("A", "x"));
        when(restTemplate.postForObject(anyString(), any(), eq(BrainRulesImportResponse.class)))
                .thenReturn(wire);

        // filename null -> branche du filePart anonyme (getFilename par défaut).
        RulesImportResult result = oneShotClient().importRules(new byte[]{9}, null);
        assertEquals(1, result.sections().size());
    }

    @Test
    void importRules_reponseNull_leveRulesImportException() {
        when(restTemplate.postForObject(anyString(), any(), eq(BrainRulesImportResponse.class)))
                .thenReturn(null);

        RulesImportException ex = assertThrows(RulesImportException.class,
                () -> oneShotClient().importRules(new byte[]{1}, "r.pdf"));
        assertTrue(ex.getMessage().contains("vide"));
    }

    @Test
    void importRules_sectionsNull_leveRulesImportException() {
        BrainRulesImportResponse wire = new BrainRulesImportResponse();
        wire.setSections(null);
        when(restTemplate.postForObject(anyString(), any(), eq(BrainRulesImportResponse.class)))
                .thenReturn(wire);

        assertThrows(RulesImportException.class,
                () -> oneShotClient().importRules(new byte[]{1}, "r.pdf"));
    }

    @Test
    void importRules_resourceAccess_leveRulesImportException() {
        when(restTemplate.postForObject(anyString(), any(), eq(BrainRulesImportResponse.class)))
                .thenThrow(new ResourceAccessException("timeout"));

        RulesImportException ex = assertThrows(RulesImportException.class,
                () -> oneShotClient().importRules(new byte[]{1}, "r.pdf"));
        assertTrue(ex.getMessage().contains("injoignable"));
    }

    @Test
    void importRules_httpServerError_leveRulesImportExceptionAvecStatut() {
        HttpServerErrorException http = HttpServerErrorException.create(
                HttpStatus.BAD_GATEWAY, "Bad Gateway", new HttpHeaders(), new byte[0], null);
        when(restTemplate.postForObject(anyString(), any(), eq(BrainRulesImportResponse.class)))
                .thenThrow(http);

        RulesImportException ex = assertThrows(RulesImportException.class,
                () -> oneShotClient().importRules(new byte[]{1}, "r.pdf"));
        assertTrue(ex.getMessage().contains("502"));
    }

    @Test
    void importRules_erreurInattendue_leveRulesImportException() {
        when(restTemplate.postForObject(anyString(), any(), eq(BrainRulesImportResponse.class)))
                .thenThrow(new IllegalStateException("boom"));

        RulesImportException ex = assertThrows(RulesImportException.class,
                () -> oneShotClient().importRules(new byte[]{1}, "r.pdf"));
        assertTrue(ex.getMessage().contains("inattendue"));
    }

    // --- Collecteur de callbacks pour le streaming --------------------------

    private static final class Collector {
        final List<RulesImportProgress> progress = new ArrayList<>();
        final AtomicInteger heartbeats = new AtomicInteger();
        final List<String> statuses = new ArrayList<>();
        final AtomicReference<RulesImportResult> done = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
    }

    private void runStreaming(String sse, Collector c) {
        ExchangeFunction ef = req -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                        .body(sse)
                        .build());
        client(ef).importRulesStreaming(
                new byte[]{1, 2}, "r.pdf",
                c.progress::add,
                c.heartbeats::incrementAndGet,
                c.statuses::add,
                c.done::set,
                c.error::set);
    }

    // --- Streaming (WebClient + SSE) ----------------------------------------

    @Test
    void streaming_tousLesEvenements_declenchentLesBonsCallbacks() {
        String sse =
                "event:extracting\ndata:{}\n\n" +
                "event:start\ndata:{\"total\":2,\"page_count\":10,\"ocr_page_count\":1}\n\n" +
                "event:progress\ndata:{\"current\":1,\"total\":2,\"new_sections\":[\"Combat\"]}\n\n" +
                "event:heartbeat\ndata:{}\n\n" +
                "event:status\ndata:{\"message\":\"retry\"}\n\n" +
                "event:chunk_failed\ndata:{\"current\":2,\"total\":2,\"message\":\"timeout\"}\n\n" +
                "event:done\ndata:{\"sections\":{\"Combat\":\"## Combat\"},\"page_count\":10,\"ocr_page_count\":1}\n\n";

        Collector c = new Collector();
        runStreaming(sse, c);

        // extracting -> progress(0,0,0,0,[]) ; start -> progress(0,2,10,1,[]) ; progress -> progress(1,2,10,1,[Combat])
        assertEquals(3, c.progress.size());

        RulesImportProgress extracting = c.progress.get(0);
        assertEquals(0, extracting.total());
        assertTrue(extracting.newSectionTitles().isEmpty());

        RulesImportProgress start = c.progress.get(1);
        assertEquals(0, start.current());
        assertEquals(2, start.total());
        assertEquals(10, start.pageCount());
        assertEquals(1, start.ocrPageCount());

        RulesImportProgress prog = c.progress.get(2);
        assertEquals(1, prog.current());
        assertEquals(2, prog.total());
        assertEquals(10, prog.pageCount());
        assertEquals(1, prog.ocrPageCount());
        assertEquals(List.of("Combat"), prog.newSectionTitles());

        assertEquals(1, c.heartbeats.get());

        // status (readMessage -> "retry") + chunk_failed (statut formaté).
        assertEquals(2, c.statuses.size());
        assertEquals("retry", c.statuses.get(0));
        assertTrue(c.statuses.get(1).contains("Morceau 2/2"));
        assertTrue(c.statuses.get(1).contains("timeout"));

        assertNotNull(c.done.get());
        assertEquals(Map.of("Combat", "## Combat"), c.done.get().sections());
        assertEquals(10, c.done.get().pageCount());
        assertEquals(1, c.done.get().ocrPageCount());

        // done a positionné terminated -> pas d'onError.
        assertEquals(null, c.error.get());
    }

    @Test
    void streaming_evenementError_appelleOnError() {
        String sse = "event:error\ndata:{\"message\":\"LLM saturé\"}\n\n";
        Collector c = new Collector();
        runStreaming(sse, c);

        assertNotNull(c.error.get());
        assertTrue(c.error.get() instanceof RulesImportException);
        assertTrue(c.error.get().getMessage().contains("LLM saturé"));
        assertNull(c.done.get());
    }

    @Test
    void streaming_chunkFailedSansMessage_statutAvecPoint() {
        // node sans champ "message" -> branche "." finale.
        String sse = "event:chunk_failed\ndata:{\"current\":1,\"total\":3}\n\n";
        Collector c = new Collector();
        runStreaming(sse, c);

        assertEquals(1, c.statuses.size());
        assertTrue(c.statuses.get(0).contains("Morceau 1/3 ignoré."));
    }

    @Test
    void streaming_statusDataNonJson_readMessageRenvoieDataBrut() {
        // data non-JSON -> readJson renvoie null -> readMessage retourne le data brut.
        String sse = "event:status\ndata:texte brut\n\n";
        Collector c = new Collector();
        runStreaming(sse, c);

        assertEquals(1, c.statuses.size());
        assertEquals("texte brut", c.statuses.get(0));
    }

    @Test
    void streaming_evenementInconnuAvecDataNonJson_estIgnore() {
        // Pas heartbeat/status/chunk_failed/error/extracting, et readJson==null -> return.
        String sse = "event:mystere\ndata:pas du json\n\n";
        Collector c = new Collector();
        runStreaming(sse, c);

        assertTrue(c.progress.isEmpty());
        assertTrue(c.statuses.isEmpty());
        // Aucun done/error -> flux interrompu -> onError.
        assertNotNull(c.error.get());
        assertTrue(c.error.get().getMessage().contains("interrompu"));
    }

    @Test
    void streaming_finSansDoneNiError_signaleFluxInterrompu() {
        // Que des heartbeats : pas de done/error -> branche "flux interrompu".
        String sse = "event:heartbeat\ndata:{}\n\n";
        Collector c = new Collector();
        runStreaming(sse, c);

        assertEquals(1, c.heartbeats.get());
        assertNotNull(c.error.get());
        assertTrue(c.error.get() instanceof RulesImportException);
        assertTrue(c.error.get().getMessage().contains("interrompu"));
    }

    @Test
    void streaming_erreurTransport_appelleOnErrorAvecCauseExposee() {
        ExchangeFunction ef = req -> Mono.error(new RuntimeException("boom"));
        Collector c = new Collector();
        client(ef).importRulesStreaming(
                new byte[]{1}, "r.pdf",
                c.progress::add, c.heartbeats::incrementAndGet, c.statuses::add,
                c.done::set, c.error::set);

        assertNotNull(c.error.get());
        assertTrue(c.error.get() instanceof RulesImportException);
        // La cause réelle (type + message) est exposée dans le message.
        assertTrue(c.error.get().getMessage().contains("boom"));
        assertNotNull(c.error.get().getCause());
    }

    @Test
    void streaming_filenameVide_utiliseNomParDefautSansErreur() {
        // filename vide -> branches "rules.pdf" du filePart et du part().filename().
        String sse = "event:done\ndata:{\"sections\":{},\"page_count\":0,\"ocr_page_count\":0}\n\n";
        Collector c = new Collector();
        ExchangeFunction ef = req -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                        .body(sse)
                        .build());
        client(ef).importRulesStreaming(
                new byte[]{1}, "  ",
                c.progress::add, c.heartbeats::incrementAndGet, c.statuses::add,
                c.done::set, c.error::set);

        assertNotNull(c.done.get());
        assertTrue(c.done.get().sections().isEmpty());
        assertNull(c.error.get());
    }

    // petits helpers d'assertion null/non-null (évite import statique supplémentaire)
    private static void assertNull(Object o) {
        assertTrue(o == null, "attendu null");
    }
}
