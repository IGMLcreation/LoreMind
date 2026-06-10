package com.loremind.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.campaigncontext.CampaignImportProgress;
import com.loremind.domain.campaigncontext.CampaignImportProposal;
import com.loremind.domain.campaigncontext.CampaignImportProposal.ArcProposal;
import com.loremind.domain.campaigncontext.CampaignImportProposal.ChapterProposal;
import com.loremind.domain.campaigncontext.CampaignImportProposal.NpcProposal;
import com.loremind.domain.campaigncontext.CampaignImportProposal.RoomProposal;
import com.loremind.domain.campaigncontext.CampaignImportProposal.SceneProposal;
import com.loremind.domain.campaigncontext.ports.CampaignImportException;
import com.loremind.domain.campaigncontext.ports.CampaignPdfImporter;
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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Adapter de sortie : implémente {@link CampaignPdfImporter} en appelant le
 * Brain Python via WebClient + SSE (POST /import/campaign/stream).
 * <p>
 * Le secret inter-service est ajouté par le WebClientCustomizer. Le timeout est
 * long (import d'un livre entier = nombreux appels LLM en série).
 */
@Component
public class BrainCampaignImportClient implements CampaignPdfImporter {

    private static final String IMPORT_CAMPAIGN_STREAM_PATH = "/import/campaign/stream";
    private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_STRING_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final long importTimeoutSeconds;

    public BrainCampaignImportClient(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${brain.base-url}") String baseUrl,
            @Value("${brain.import-timeout-seconds:600}") long importTimeoutSeconds) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.importTimeoutSeconds = importTimeoutSeconds;
    }

    @Override
    public void importCampaignStreaming(
            byte[] pdfBytes,
            String filename,
            Consumer<CampaignImportProgress> onProgress,
            Consumer<CampaignImportProposal> onDone,
            Consumer<Throwable> onError) {

        MultipartBodyBuilder parts = new MultipartBodyBuilder();
        parts.part("file", filePart(pdfBytes, filename))
                .filename(filename == null || filename.isBlank() ? "campaign.pdf" : filename);

        Flux<ServerSentEvent<String>> flux = webClient.post()
                .uri(IMPORT_CAMPAIGN_STREAM_PATH)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .body(BodyInserters.fromMultipartData(parts.build()))
                .retrieve()
                .bodyToFlux(SSE_STRING_TYPE);

        int[] pageCount = {0};
        int[] ocrPageCount = {0};
        boolean[] terminated = {false};

        try {
            flux
                .timeout(Duration.ofSeconds(importTimeoutSeconds))
                .doOnNext(sse -> handleEvent(
                        sse, pageCount, ocrPageCount, terminated, onProgress, onDone, onError))
                .blockLast();
            if (!terminated[0]) {
                onError.accept(new CampaignImportException(
                        "Le flux d'import s'est interrompu avant la fin."));
            }
        } catch (Exception e) {
            if (!terminated[0]) {
                // On EXPOSE la cause réelle (type + message) : sans ça, le diagnostic
                // est impossible (timeout WebClient, connexion coupée, réponse non-2xx…).
                String cause = e.getClass().getSimpleName()
                        + (e.getMessage() != null ? " — " + e.getMessage() : "");
                onError.accept(new CampaignImportException(
                        "Erreur lors du streaming d'import depuis le Brain : " + cause, e));
            }
        }
    }

    private void handleEvent(
            ServerSentEvent<String> sse,
            int[] pageCount,
            int[] ocrPageCount,
            boolean[] terminated,
            Consumer<CampaignImportProgress> onProgress,
            Consumer<CampaignImportProposal> onDone,
            Consumer<Throwable> onError) {

        String event = sse.event();
        String data = sse.data() == null ? "" : sse.data();

        if ("error".equals(event)) {
            terminated[0] = true;
            onError.accept(new CampaignImportException(
                    "Le Brain a signalé une erreur : " + readMessage(data)));
            return;
        }
        if ("extracting".equals(event)) {
            onProgress.accept(new CampaignImportProgress(0, 0, 0, 0, 0, 0, 0, 0));
            return;
        }

        JsonNode node = readJson(data);
        if (node == null) return;

        if ("start".equals(event)) {
            pageCount[0] = node.path("page_count").asInt();
            ocrPageCount[0] = node.path("ocr_page_count").asInt();
            onProgress.accept(new CampaignImportProgress(
                    0, node.path("total").asInt(), pageCount[0], ocrPageCount[0], 0, 0, 0, 0));
        } else if ("progress".equals(event)) {
            onProgress.accept(new CampaignImportProgress(
                    node.path("current").asInt(),
                    node.path("total").asInt(),
                    pageCount[0],
                    ocrPageCount[0],
                    node.path("arc_count").asInt(),
                    node.path("chapter_count").asInt(),
                    node.path("scene_count").asInt(),
                    node.path("npc_count").asInt()));
        } else if ("done".equals(event)) {
            terminated[0] = true;
            onDone.accept(new CampaignImportProposal(
                    toArcs(node.path("arcs")), toNpcs(node.path("npcs"))));
        }
    }

    // --- Parsing de l'arbre --------------------------------------------------

    private List<ArcProposal> toArcs(JsonNode arcsNode) {
        List<ArcProposal> arcs = new ArrayList<>();
        if (arcsNode != null && arcsNode.isArray()) {
            for (JsonNode arc : arcsNode) {
                arcs.add(new ArcProposal(
                        text(arc, "name"),
                        text(arc, "description"),
                        text(arc, "type"),
                        toChapters(arc.path("chapters")),
                        null));
            }
        }
        return arcs;
    }

    private List<ChapterProposal> toChapters(JsonNode chaptersNode) {
        List<ChapterProposal> chapters = new ArrayList<>();
        if (chaptersNode != null && chaptersNode.isArray()) {
            for (JsonNode ch : chaptersNode) {
                chapters.add(new ChapterProposal(
                        text(ch, "name"),
                        text(ch, "description"),
                        toScenes(ch.path("scenes")),
                        null));
            }
        }
        return chapters;
    }

    private List<SceneProposal> toScenes(JsonNode scenesNode) {
        List<SceneProposal> scenes = new ArrayList<>();
        if (scenesNode != null && scenesNode.isArray()) {
            for (JsonNode sc : scenesNode) {
                scenes.add(new SceneProposal(
                        text(sc, "name"), text(sc, "description"),
                        text(sc, "player_narration"), text(sc, "gm_notes"),
                        toRooms(sc.path("rooms")), null));
            }
        }
        return scenes;
    }

    private List<RoomProposal> toRooms(JsonNode roomsNode) {
        List<RoomProposal> rooms = new ArrayList<>();
        if (roomsNode != null && roomsNode.isArray()) {
            for (JsonNode rm : roomsNode) {
                rooms.add(new RoomProposal(
                        text(rm, "name"), text(rm, "description"),
                        text(rm, "enemies"), text(rm, "loot")));
            }
        }
        return rooms;
    }

    private List<NpcProposal> toNpcs(JsonNode npcsNode) {
        List<NpcProposal> npcs = new ArrayList<>();
        if (npcsNode != null && npcsNode.isArray()) {
            for (JsonNode n : npcsNode) {
                npcs.add(new NpcProposal(text(n, "name"), text(n, "description")));
            }
        }
        return npcs;
    }

    // --- Helpers -------------------------------------------------------------

    private ByteArrayResource filePart(byte[] pdfBytes, String filename) {
        return new ByteArrayResource(pdfBytes) {
            @Override
            public String getFilename() {
                return (filename == null || filename.isBlank()) ? "campaign.pdf" : filename;
            }
        };
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? "" : v.asText();
    }

    private JsonNode readJson(String data) {
        try {
            return objectMapper.readTree(data);
        } catch (Exception e) {
            return null;
        }
    }

    private String readMessage(String data) {
        JsonNode node = readJson(data);
        if (node != null && node.hasNonNull("message")) {
            return node.get("message").asText();
        }
        return data;
    }
}
