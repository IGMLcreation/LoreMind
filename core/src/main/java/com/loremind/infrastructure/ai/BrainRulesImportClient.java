package com.loremind.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.gamesystemcontext.RulesImportProgress;
import com.loremind.domain.gamesystemcontext.RulesImportResult;
import com.loremind.domain.gamesystemcontext.ports.RulesImportException;
import com.loremind.domain.gamesystemcontext.ports.RulesPdfImporter;
import com.loremind.infrastructure.web.config.UserLanguageHolder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Adapter de sortie : implémente {@link RulesPdfImporter} en appelant le Brain
 * Python via HTTP multipart.
 * <p>
 * Deux variantes :
 *  - {@link #importRules} : one-shot bloquant (RestTemplate, POST /import/rules).
 *  - {@link #importRulesStreaming} : streamé (WebClient + SSE, POST /import/rules/stream)
 *    pour remonter l'avancement d'un import long.
 * <p>
 * Le RestTemplate ({@code brainImportRestTemplate}) a un timeout long ; le secret
 * inter-service est ajouté par l'intercepteur du bean (RestTemplate) et par le
 * WebClientCustomizer (WebClient).
 */
@Component
public class BrainRulesImportClient implements RulesPdfImporter {

    private static final String IMPORT_RULES_PATH = "/import/rules";
    private static final String IMPORT_RULES_STREAM_PATH = "/import/rules/stream";
    private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_STRING_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestTemplate restTemplate;
    private final WebClient webClient;
    private final BrainSseImportSupport sse;
    private final String baseUrl;
    private final long importTimeoutSeconds;

    public BrainRulesImportClient(
            @Qualifier("brainImportRestTemplate") RestTemplate restTemplate,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${brain.base-url}") String baseUrl,
            @Value("${brain.import-timeout-seconds:600}") long importTimeoutSeconds) {
        this.restTemplate = restTemplate;
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.sse = new BrainSseImportSupport(objectMapper);
        this.baseUrl = baseUrl;
        this.importTimeoutSeconds = importTimeoutSeconds;
    }

    // --- One-shot (bloquant) -------------------------------------------------

    @Override
    public RulesImportResult importRules(byte[] pdfBytes, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", sse.filePart(pdfBytes, filename, "rules.pdf"));
        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            BrainRulesImportResponse response = restTemplate.postForObject(
                    baseUrl + IMPORT_RULES_PATH, entity, BrainRulesImportResponse.class);
            if (response == null || response.getSections() == null) {
                throw new RulesImportException("Le Brain a renvoyé une réponse vide.");
            }
            return new RulesImportResult(
                    Map.copyOf(response.getSections()),
                    response.getPageCount(),
                    response.getOcrPageCount());
        } catch (ResourceAccessException e) {
            throw new RulesImportException(
                    "Le Brain est injoignable (timeout ou service arrêté).", e);
        } catch (RestClientResponseException e) {
            throw new RulesImportException(
                    "Le Brain a répondu HTTP " + e.getStatusCode().value()
                            + " : " + e.getResponseBodyAsString(), e);
        } catch (RulesImportException e) {
            throw e;
        } catch (Exception e) {
            throw new RulesImportException("Erreur inattendue lors de l'import via le Brain.", e);
        }
    }

    // --- Streamé (SSE) -------------------------------------------------------

    @Override
    public void importRulesStreaming(
            byte[] pdfBytes,
            String filename,
            Consumer<RulesImportProgress> onProgress,
            Runnable onHeartbeat,
            Consumer<String> onStatus,
            Consumer<RulesImportResult> onDone,
            Consumer<Throwable> onError) {

        MultipartBodyBuilder parts = new MultipartBodyBuilder();
        parts.part("file", sse.filePart(pdfBytes, filename, "rules.pdf"))
                .filename(filename == null || filename.isBlank() ? "rules.pdf" : filename);

        Flux<ServerSentEvent<String>> flux = webClient.post()
                .uri(IMPORT_RULES_STREAM_PATH)
                .header(UserLanguageHolder.HEADER, UserLanguageHolder.get())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .body(BodyInserters.fromMultipartData(parts.build()))
                .retrieve()
                .bodyToFlux(SSE_STRING_TYPE);

        // Holders mutables : le flux est consommé séquentiellement (blockLast),
        // donc pas de souci de concurrence sur ces compteurs.
        int[] pageCount = {0};
        int[] ocrPageCount = {0};
        boolean[] terminated = {false};

        sse.runStream(
                flux, importTimeoutSeconds, terminated,
                event -> handleEvent(
                        event, pageCount, ocrPageCount, terminated,
                        onProgress, onHeartbeat, onStatus, onDone, onError),
                onError, RulesImportException::new);
    }

    private void handleEvent(
            ServerSentEvent<String> ssEvent,
            int[] pageCount,
            int[] ocrPageCount,
            boolean[] terminated,
            Consumer<RulesImportProgress> onProgress,
            Runnable onHeartbeat,
            Consumer<String> onStatus,
            Consumer<RulesImportResult> onDone,
            Consumer<Throwable> onError) {

        String event = ssEvent.event();
        String data = ssEvent.data() == null ? "" : ssEvent.data();

        if ("heartbeat".equals(event)) {
            // Keep-alive du Brain pendant un appel LLM long : à PROPAGER jusqu'au
            // navigateur, sinon nginx (proxy_read_timeout) coupe le SSE Core→front
            // resté silencieux pendant tout le traitement du morceau.
            onHeartbeat.run();
            return;
        }
        if ("status".equals(event)) {
            // Message d'attente lisible (retry sur fournisseur saturé, morceau
            // re-découpé…) : affiché par l'UI au lieu de n'exister qu'en logs.
            onStatus.accept(sse.readMessage(data));
            return;
        }
        if ("chunk_failed".equals(event)) {
            onStatus.accept(sse.chunkFailedStatus(data));
            return;
        }
        if ("error".equals(event)) {
            terminated[0] = true;
            onError.accept(new RulesImportException(
                    "Le Brain a signalé une erreur : " + sse.readMessage(data)));
            return;
        }
        if ("extracting".equals(event)) {
            // Phase d'extraction : total inconnu (0) → l'UI affiche "Extraction…".
            onProgress.accept(new RulesImportProgress(0, 0, 0, 0, List.of()));
            return;
        }

        JsonNode node = sse.readJson(data);
        if (node == null) return;

        if ("start".equals(event)) {
            pageCount[0] = node.path("page_count").asInt();
            ocrPageCount[0] = node.path("ocr_page_count").asInt();
            onProgress.accept(new RulesImportProgress(
                    0, node.path("total").asInt(), pageCount[0], ocrPageCount[0], List.of()));
        } else if ("progress".equals(event)) {
            onProgress.accept(new RulesImportProgress(
                    node.path("current").asInt(),
                    node.path("total").asInt(),
                    pageCount[0],
                    ocrPageCount[0],
                    toStringList(node.path("new_sections"))));
        } else if ("done".equals(event)) {
            terminated[0] = true;
            onDone.accept(new RulesImportResult(
                    toStringMap(node.path("sections")),
                    node.path("page_count").asInt(),
                    node.path("ocr_page_count").asInt()));
        }
    }

    // --- Helpers -------------------------------------------------------------

    private List<String> toStringList(JsonNode array) {
        List<String> out = new ArrayList<>();
        if (array != null && array.isArray()) {
            array.forEach(n -> out.add(n.asText()));
        }
        return out;
    }

    private Map<String, String> toStringMap(JsonNode object) {
        Map<String, String> out = new LinkedHashMap<>();
        if (object != null && object.isObject()) {
            object.properties().forEach(e -> out.put(e.getKey(), e.getValue().asText()));
        }
        return out;
    }
}
