package com.loremind.infrastructure.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.application.campaigncontext.CampaignImportService;
import com.loremind.domain.campaigncontext.CampaignImportProposal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

/**
 * REST Controller pour l'import d'un PDF de campagne → arbre arc/chapitre/scène.
 * <p>
 *  - POST /api/campaigns/{id}/import-structure/stream  (multipart) → SSE de la
 *    proposition (progress + done). Ne persiste rien.
 *  - POST /api/campaigns/{id}/import-structure/apply   (JSON arbre révisé) →
 *    crée les entités et renvoie le récapitulatif.
 */
@RestController
@RequestMapping("/api/campaigns/{campaignId}/import-structure")
public class CampaignImportController {

    private static final Logger log = LoggerFactory.getLogger(CampaignImportController.class);

    /** Timeout SSE généreux : un import de livre entier peut durer plusieurs minutes. */
    private static final long IMPORT_SSE_TIMEOUT_MS = 15 * 60 * 1000L;

    private final CampaignImportService campaignImportService;
    private final TaskExecutor taskExecutor;
    private final ObjectMapper objectMapper;

    public CampaignImportController(
            CampaignImportService campaignImportService,
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
            ObjectMapper objectMapper) {
        this.campaignImportService = campaignImportService;
        this.taskExecutor = taskExecutor;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/stream",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter importStream(
            @PathVariable String campaignId,
            @RequestParam("file") MultipartFile file) throws IOException {
        SseEmitter emitter = new SseEmitter(IMPORT_SSE_TIMEOUT_MS);
        if (file == null || file.isEmpty()) {
            sendError(emitter, "Fichier PDF vide.");
            return emitter;
        }
        byte[] bytes = file.getBytes();
        String filename = file.getOriginalFilename();

        taskExecutor.execute(() -> {
            try {
                campaignImportService.importStructureStreaming(
                        bytes, filename,
                        progress -> sendEvent(emitter, "progress", progress),
                        proposal -> {
                            sendEvent(emitter, "done", proposal);
                            emitter.complete();
                        },
                        error -> {
                            log.warn("Import campagne (stream) échoué : {}", error.getMessage());
                            sendError(emitter, error.getMessage());
                        });
            } catch (Exception e) {
                log.warn("Import campagne (stream) échoué : {}", e.getMessage());
                sendError(emitter, e.getMessage());
            }
        });
        return emitter;
    }

    @PostMapping(value = "/apply", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CampaignImportService.ApplyResult> apply(
            @PathVariable String campaignId,
            @RequestBody CampaignImportProposal proposal) {
        try {
            CampaignImportService.ApplyResult result =
                    campaignImportService.applyStructure(campaignId, proposal);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // --- Helpers SSE ---------------------------------------------------------

    private void sendEvent(SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(
                    objectMapper.writeValueAsString(payload), MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private void sendError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(
                    objectMapper.writeValueAsString(Map.of(
                            "message", message != null ? message : "Erreur inconnue.")),
                    MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }
}
