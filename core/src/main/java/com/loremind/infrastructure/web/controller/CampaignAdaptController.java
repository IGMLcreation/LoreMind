package com.loremind.infrastructure.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.application.campaigncontext.CampaignAdaptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

/**
 * REST Controller : conseils d'adaptation d'un PDF à une campagne existante (SSE).
 *  - POST /api/campaigns/{id}/adapt-pdf/stream  (multipart) → flux de tokens markdown.
 * Ne persiste rien : la sortie est du conseil libre à appliquer manuellement.
 */
@RestController
@RequestMapping("/api/campaigns/{campaignId}/adapt-pdf")
public class CampaignAdaptController {

    private static final Logger log = LoggerFactory.getLogger(CampaignAdaptController.class);
    private static final long SSE_TIMEOUT_MS = 15 * 60 * 1000L;

    private final CampaignAdaptService campaignAdaptService;
    private final TaskExecutor taskExecutor;
    private final ObjectMapper objectMapper;

    public CampaignAdaptController(
            CampaignAdaptService campaignAdaptService,
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
            ObjectMapper objectMapper) {
        this.campaignAdaptService = campaignAdaptService;
        this.taskExecutor = taskExecutor;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/stream",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter adaptStream(
            @PathVariable String campaignId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "messages", required = false) String messagesJson) throws IOException {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        if (file == null || file.isEmpty()) {
            sendError(emitter, "Fichier PDF vide.");
            return emitter;
        }
        byte[] bytes = file.getBytes();
        String filename = file.getOriginalFilename();

        taskExecutor.execute(() -> {
            try {
                campaignAdaptService.adviseStreaming(
                        campaignId, bytes, filename, messagesJson,
                        token -> sendToken(emitter, token),
                        () -> {
                            sendEvent(emitter, "done", Map.of());
                            emitter.complete();
                        },
                        error -> {
                            log.warn("Adaptation PDF échouée : {}", error.getMessage());
                            sendError(emitter, error.getMessage());
                        });
            } catch (IllegalArgumentException e) {
                sendError(emitter, "Campagne introuvable.");
            } catch (Exception e) {
                log.warn("Adaptation PDF échouée : {}", e.getMessage());
                sendError(emitter, e.getMessage());
            }
        });
        return emitter;
    }

    private void sendToken(SseEmitter emitter, String token) {
        sendEvent(emitter, "token", Map.of("token", token));
    }

    private void sendError(SseEmitter emitter, String message) {
        sendEvent(emitter, "error", Map.of("message", message != null ? message : "Erreur inconnue."));
        emitter.complete();
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(
                    objectMapper.writeValueAsString(payload), MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }
}
