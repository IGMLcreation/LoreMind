package com.loremind.infrastructure.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpEntity;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Couvre les sous-classes anonymes {@code new ByteArrayResource(){ getFilename() }}
 * des clients d'upload multipart : leur {@code getFilename()} (nom par défaut si
 * absent/vide) n'est appelé que lors de la SÉRIALISATION du corps, qui n'a pas lieu
 * quand le transport est mocké. On la déclenche donc explicitement :
 * <ul>
 *   <li>clients RestTemplate : on capture le {@link HttpEntity} envoyé et on lit la
 *       ressource « file » pour appeler {@code getFilename()} ;</li>
 *   <li>clients WebClient : l'{@link ExchangeFunction} sérialise réellement le corps
 *       multipart dans un {@link MockClientHttpRequest} (ce qui invoque getFilename).</li>
 * </ul>
 */
class BrainMultipartFilenameTest {

    // --- Helpers ------------------------------------------------------------

    /** ExchangeFunction qui sérialise le corps de la requête (déclenche getFilename) puis renvoie un SSE 'done'. */
    private static ExchangeFunction serializingExchange(String sse) {
        return request -> {
            MockClientHttpRequest mock = new MockClientHttpRequest(request.method(), request.url());
            // Le write-handler par défaut ne souscrit pas au corps : on draine donc le
            // flux nous-mêmes pour forcer l'écriture des parts (et l'appel à getFilename).
            mock.setWriteHandler(body -> DataBufferUtils.join(body)
                    .doOnNext(DataBufferUtils::release).then());
            request.writeTo(mock, ExchangeStrategies.withDefaults()).block();
            return Mono.just(ClientResponse.create(org.springframework.http.HttpStatus.OK)
                    .header(org.springframework.http.HttpHeaders.CONTENT_TYPE,
                            org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
                    .body(sse)
                    .build());
        };
    }

    @SuppressWarnings("unchecked")
    private static String capturedFilename(RestTemplate rt) {
        ArgumentCaptor<HttpEntity> cap = ArgumentCaptor.forClass(HttpEntity.class);
        verify(rt).postForObject(anyString(), cap.capture(), any());
        MultiValueMap<String, Object> body = (MultiValueMap<String, Object>) cap.getValue().getBody();
        return ((Resource) body.getFirst("file")).getFilename();
    }

    // --- BrainNotebookIndexClient (RestTemplate) ----------------------------

    @Test
    void notebookIndex_filePart_usesGivenFilename_elseDefault() {
        RestTemplate rt = mock(RestTemplate.class);
        BrainNotebookIndexClient client = new BrainNotebookIndexClient(rt, "http://brain");

        try { client.index("src-1", new byte[]{1, 2}, "doc.pdf"); } catch (RuntimeException ignored) { }
        assertEquals("doc.pdf", capturedFilename(rt));

        RestTemplate rt2 = mock(RestTemplate.class);
        BrainNotebookIndexClient client2 = new BrainNotebookIndexClient(rt2, "http://brain");
        try { client2.index("src-1", new byte[]{1, 2}, null); } catch (RuntimeException ignored) { }
        assertEquals("source.pdf", capturedFilename(rt2));
    }

    // --- BrainRulesImportClient (RestTemplate one-shot) ---------------------

    @Test
    void rulesImport_filePart_usesGivenFilename_elseDefault() {
        RestTemplate rt = mock(RestTemplate.class);
        BrainRulesImportClient client = new BrainRulesImportClient(
                rt, WebClient.builder(), new ObjectMapper(), "http://brain", 600);

        try { client.importRules(new byte[]{1, 2}, "regles.pdf"); } catch (RuntimeException ignored) { }
        assertEquals("regles.pdf", capturedFilename(rt));

        RestTemplate rt2 = mock(RestTemplate.class);
        BrainRulesImportClient client2 = new BrainRulesImportClient(
                rt2, WebClient.builder(), new ObjectMapper(), "http://brain", 600);
        try { client2.importRules(new byte[]{1, 2}, "  "); } catch (RuntimeException ignored) { }
        assertEquals("rules.pdf", capturedFilename(rt2));
    }

    // --- BrainCampaignAdaptClient (WebClient multipart) ---------------------

    @Test
    void campaignAdapt_filePart_serializedFilename() {
        ExchangeFunction ef = serializingExchange("event:done\ndata:{}\n\n");
        BrainCampaignAdaptClient client = new BrainCampaignAdaptClient(
                WebClient.builder().exchangeFunction(ef), new ObjectMapper(), "http://brain", 30);

        // filename présent puis null : les deux branches de getFilename sont sérialisées.
        client.adviseStreaming(new byte[]{1, 2}, "doc.pdf", "brief", "[]",
                t -> { }, () -> { }, e -> { });
        client.adviseStreaming(new byte[]{1, 2}, null, null, null,
                t -> { }, () -> { }, e -> { });
    }

    // --- BrainCampaignImportClient (WebClient multipart) --------------------

    @Test
    void campaignImport_filePart_serializedFilename() {
        ExchangeFunction ef = serializingExchange("event:done\ndata:{\"sections\":{}}\n\n");
        BrainCampaignImportClient client = new BrainCampaignImportClient(
                WebClient.builder().exchangeFunction(ef), new ObjectMapper(), "http://brain", 30);

        client.importCampaignStreaming(new byte[]{1, 2}, "doc.pdf",
                p -> { }, () -> { }, s -> { }, r -> { }, e -> { });
        client.importCampaignStreaming(new byte[]{1, 2}, null,
                p -> { }, () -> { }, s -> { }, r -> { }, e -> { });
    }
}
