package com.loremind.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.campaigncontext.generation.CampaignImportProgress;
import com.loremind.domain.campaigncontext.generation.CampaignImportProposal;
import com.loremind.domain.campaigncontext.generation.CampaignImportProposal.ArcProposal;
import com.loremind.domain.campaigncontext.generation.CampaignImportProposal.ChapterProposal;
import com.loremind.domain.campaigncontext.generation.CampaignImportProposal.NpcProposal;
import com.loremind.domain.campaigncontext.generation.CampaignImportProposal.RoomProposal;
import com.loremind.domain.campaigncontext.generation.CampaignImportProposal.SceneProposal;
import com.loremind.domain.campaigncontext.ports.exceptions.CampaignImportException;
import com.loremind.domain.campaigncontext.ports.CampaignPdfImporter;
import com.loremind.infrastructure.web.config.UserLanguageHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

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

    private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_STRING_TYPE =
            new ParameterizedTypeReference<>() {};
    /** Champ JSON répété du proposal d'arbre (arc/chapitre/scène/salle/PNJ). */
    private static final String FIELD_DESCRIPTION = "description";

    private final WebClient webClient;
    private final BrainSseImportSupport sse;
    private final long importTimeoutSeconds;
    // Route du Brain surchargeable par config (défaut = contrat d'API actuel).
    private final String importCampaignStreamPath;

    public BrainCampaignImportClient(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${brain.base-url}") String baseUrl,
            @Value("${brain.import-timeout-seconds:600}") long importTimeoutSeconds,
            @Value("${brain.paths.import-campaign:/import/campaign/stream}") String importCampaignStreamPath) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.sse = new BrainSseImportSupport(objectMapper);
        this.importTimeoutSeconds = importTimeoutSeconds;
        this.importCampaignStreamPath = importCampaignStreamPath;
    }

    /** Callbacks de streaming groupés (réduit le nombre de paramètres de handleEvent). */
    private record ImportCallbacks(
            Consumer<CampaignImportProgress> onProgress,
            Runnable onHeartbeat,
            Consumer<String> onStatus,
            Consumer<CampaignImportProposal> onDone,
            Consumer<Throwable> onError) {}

    @Override
    public void importCampaignStreaming(
            byte[] pdfBytes,
            String filename,
            Consumer<CampaignImportProgress> onProgress,
            Runnable onHeartbeat,
            Consumer<String> onStatus,
            Consumer<CampaignImportProposal> onDone,
            Consumer<Throwable> onError) {

        MultipartBodyBuilder parts = new MultipartBodyBuilder();
        parts.part("file", sse.filePart(pdfBytes, filename, "campaign.pdf"))
                .filename(filename == null || filename.isBlank() ? "campaign.pdf" : filename);

        Flux<ServerSentEvent<String>> flux = webClient.post()
                .uri(importCampaignStreamPath)
                .header(UserLanguageHolder.HEADER, UserLanguageHolder.get())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .body(BodyInserters.fromMultipartData(parts.build()))
                .retrieve()
                .bodyToFlux(SSE_STRING_TYPE);

        int[] pageCount = {0};
        int[] ocrPageCount = {0};
        boolean[] terminated = {false};
        ImportCallbacks callbacks = new ImportCallbacks(onProgress, onHeartbeat, onStatus, onDone, onError);

        sse.runStream(
                flux, importTimeoutSeconds, terminated,
                event -> handleEvent(event, pageCount, ocrPageCount, terminated, callbacks),
                onError, CampaignImportException::new);
    }

    private void handleEvent(
            ServerSentEvent<String> ssEvent,
            int[] pageCount,
            int[] ocrPageCount,
            boolean[] terminated,
            ImportCallbacks callbacks) {

        String event = ssEvent.event();
        String data = ssEvent.data() == null ? "" : ssEvent.data();

        if ("heartbeat".equals(event)) {
            // Keep-alive du Brain pendant un appel LLM long : à PROPAGER jusqu'au
            // navigateur, sinon nginx (proxy_read_timeout) coupe le SSE Core→front.
            callbacks.onHeartbeat().run();
            return;
        }
        if ("status".equals(event)) {
            // Message d'attente lisible (retry sur fournisseur saturé, morceau
            // re-découpé…) : affiché par l'UI au lieu de n'exister qu'en logs.
            callbacks.onStatus().accept(sse.readMessage(data));
            return;
        }
        if ("chunk_failed".equals(event)) {
            callbacks.onStatus().accept(sse.chunkFailedStatus(data));
            return;
        }
        if ("error".equals(event)) {
            terminated[0] = true;
            callbacks.onError().accept(new CampaignImportException(
                    "Le Brain a signalé une erreur : " + sse.readMessage(data)));
            return;
        }
        if ("extracting".equals(event)) {
            callbacks.onProgress().accept(new CampaignImportProgress(0, 0, 0, 0, 0, 0, 0, 0));
            return;
        }

        JsonNode node = sse.readJson(data);
        if (node == null) return;

        if ("start".equals(event)) {
            pageCount[0] = node.path("page_count").asInt();
            ocrPageCount[0] = node.path("ocr_page_count").asInt();
            callbacks.onProgress().accept(new CampaignImportProgress(
                    0, node.path("total").asInt(), pageCount[0], ocrPageCount[0], 0, 0, 0, 0));
        } else if ("progress".equals(event)) {
            callbacks.onProgress().accept(new CampaignImportProgress(
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
            callbacks.onDone().accept(new CampaignImportProposal(
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
                        text(arc, FIELD_DESCRIPTION),
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
                        text(ch, FIELD_DESCRIPTION),
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
                        text(sc, "name"), text(sc, FIELD_DESCRIPTION),
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
                        text(rm, "name"), text(rm, FIELD_DESCRIPTION),
                        text(rm, "enemies"), text(rm, "loot")));
            }
        }
        return rooms;
    }

    private List<NpcProposal> toNpcs(JsonNode npcsNode) {
        List<NpcProposal> npcs = new ArrayList<>();
        if (npcsNode != null && npcsNode.isArray()) {
            for (JsonNode n : npcsNode) {
                npcs.add(new NpcProposal(text(n, "name"), text(n, FIELD_DESCRIPTION)));
            }
        }
        return npcs;
    }

    // --- Helpers -------------------------------------------------------------

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? "" : v.asText();
    }
}
